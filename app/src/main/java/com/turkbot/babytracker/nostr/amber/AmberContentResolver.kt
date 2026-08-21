/**
 * Baby Tracker — Native Android (Kotlin)
 *
 * A privacy-first baby tracking app with Nostr-based encrypted storage
 * and parent-to-parent sync.
 *
 * Copyright (c) 2026 Turkey
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for full license details.
 */

package com.turkbot.babytracker.nostr.amber

import android.content.Context
import android.net.Uri
import android.util.Log
import com.turkbot.babytracker.debug.DebugLogger as Dbg
import com.turkbot.babytracker.debug.DebugLogger.Category as Cat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NIP-55 Content Resolver transport — the background, no-UI path to the signer.
 *
 * Per NIP-55 there are two Android transports:
 *
 *   - Intents:          the signer opens, the user approves manually. Always works,
 *                       always shows UI.
 *   - Content Resolver: "Runs in the background without opening the signer, but only
 *                       works for permissions the user has previously chosen to
 *                       remember."
 *
 * Using intents for every operation means Amber pops up 4x per data entry
 * (encrypt-self, sign-self, encrypt-partner, sign-partner) plus once per inbound
 * partner event. This class provides the silent path so a user who tapped
 * "remember my choice" in Amber never sees a prompt again.
 *
 * Query URI is content://<signer-package>.<METHOD> and selectionArgs carry the
 * payload in the fixed order [payload, pubkey, current_user].
 *
 * A null return means "not available via this transport" — the caller MUST fall
 * back to the intent path. Reasons per the spec:
 *   - the user did not enable "remember my choice" for this request
 *   - the user-pubkey is not present in the signer
 *   - the request type is not recognised
 *
 * The one exception is a `rejected` column, which means the user chose to ALWAYS
 * reject this request. The spec is explicit that clients SHOULD NOT then fall back
 * to an intent, so that case is reported distinctly via [Outcome.Rejected].
 */
object AmberContentResolver {

    private const val TAG = "AmberCR"

    // Content provider path segments (upper-case method names per NIP-55)
    private const val PATH_SIGN_EVENT = "SIGN_EVENT"
    private const val PATH_NIP44_ENCRYPT = "NIP44_ENCRYPT"
    private const val PATH_NIP44_DECRYPT = "NIP44_DECRYPT"
    private const val PATH_GET_PUBLIC_KEY = "GET_PUBLIC_KEY"

    private const val COL_RESULT = "result"
    private const val COL_EVENT = "event"
    private const val COL_REJECTED = "rejected"

    /**
     * Signers whose content providers are declared in AndroidManifest <queries>.
     *
     * On Android 11+ a provider is only reachable if its package is visible to us.
     * Intent-scheme visibility does not cover content providers, so any signer not
     * listed here transparently falls back to the intent path rather than failing.
     */
    private val KNOWN_PROVIDER_PACKAGES = setOf(
        "com.greenart7c3.nostrsigner",
        "com.greenart7c3.nostrsigner.debug"
    )

    /**
     * Outcome of a background query.
     *
     * [Unavailable] is the "fall back to an intent" signal; [Rejected] is a hard no
     * that must NOT be retried via intent (the user chose always-reject).
     */
    sealed class Outcome {
        data class Success(val result: String, val event: String? = null) : Outcome()
        object Unavailable : Outcome()
        object Rejected : Outcome()
    }

    /**
     * nip44_encrypt without opening the signer.
     */
    suspend fun nip44Encrypt(
        context: Context,
        signerPackage: String,
        plaintext: String,
        recipientPubkeyHex: String,
        currentUserHex: String
    ): Outcome = query(
        context, signerPackage, PATH_NIP44_ENCRYPT,
        arrayOf(plaintext, recipientPubkeyHex, currentUserHex)
    )

    /**
     * nip44_decrypt without opening the signer.
     */
    suspend fun nip44Decrypt(
        context: Context,
        signerPackage: String,
        ciphertext: String,
        senderPubkeyHex: String,
        currentUserHex: String
    ): Outcome = query(
        context, signerPackage, PATH_NIP44_DECRYPT,
        arrayOf(ciphertext, senderPubkeyHex, currentUserHex)
    )

    /**
     * sign_event without opening the signer.
     *
     * The pubkey slot is empty for signing — only the payload and current_user
     * are meaningful, but the arg order is fixed by the spec.
     */
    suspend fun signEvent(
        context: Context,
        signerPackage: String,
        eventJson: String,
        currentUserHex: String
    ): Outcome = query(
        context, signerPackage, PATH_SIGN_EVENT,
        arrayOf(eventJson, "", currentUserHex)
    )

    /**
     * Probe whether the signer answers background queries at all for this user.
     *
     * Used once after login to tell the user whether they are in the silent path
     * or still on the prompt-per-operation path, without burning a real request.
     */
    suspend fun isBackgroundSigningAvailable(
        context: Context,
        signerPackage: String,
        currentUserHex: String
    ): Boolean = query(
        context, signerPackage, PATH_GET_PUBLIC_KEY,
        arrayOf("", "", currentUserHex)
    ) is Outcome.Success

    /**
     * Run the content resolver query on the IO dispatcher.
     *
     * Every failure mode is swallowed into [Outcome.Unavailable] so a signer that
     * does not implement the provider (or throws SecurityException because the
     * user revoked access) degrades to the intent path instead of crashing the
     * export.
     */
    private suspend fun query(
        context: Context,
        signerPackage: String,
        path: String,
        args: Array<String>
    ): Outcome = withContext(Dispatchers.IO) {
        // Skip the query entirely for signers we cannot see on Android 11+.
        // resolveContentProvider returning null means the provider is invisible
        // or absent; querying anyway just throws and costs a round-trip.
        if (signerPackage !in KNOWN_PROVIDER_PACKAGES) {
            return@withContext Outcome.Unavailable
        }
        val uri = Uri.parse("content://$signerPackage.$path")
        try {
            context.contentResolver.query(uri, args, null, null, null).use { cursor ->
                if (cursor == null) {
                    // Signer has no remembered permission for this request.
                    return@withContext Outcome.Unavailable
                }
                // A `rejected` column means always-reject: do not fall back to intent.
                if (cursor.getColumnIndex(COL_REJECTED) > -1) {
                    Dbg.warn(Cat.AMBER, "Background $path: signer returned always-reject")
                    return@withContext Outcome.Rejected
                }
                if (!cursor.moveToFirst()) {
                    return@withContext Outcome.Unavailable
                }
                val resultIdx = cursor.getColumnIndex(COL_RESULT)
                if (resultIdx < 0) {
                    return@withContext Outcome.Unavailable
                }
                val result = cursor.getString(resultIdx)
                if (result.isNullOrBlank()) {
                    return@withContext Outcome.Unavailable
                }
                // sign_event also returns the full signed event JSON
                val eventIdx = cursor.getColumnIndex(COL_EVENT)
                val event = if (eventIdx > -1) cursor.getString(eventIdx) else null
                Outcome.Success(result, event)
            }
        } catch (e: SecurityException) {
            // Provider exists but this app is not allowed to query it.
            Log.d(TAG, "Background $path denied: ${e.message}")
            Outcome.Unavailable
        } catch (e: Exception) {
            // Provider missing entirely, or the signer threw. Fall back to intents.
            Log.d(TAG, "Background $path unavailable: ${e.message}")
            Outcome.Unavailable
        }
    }
}
