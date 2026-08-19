/**
 * Baby Tracker — Native Android (Kotlin)
 *
 * A privacy-first baby tracking app with Nostr-based encrypted storage
 * and parent-to-parent messaging.
 *
 * Copyright (c) 2026 Turkey
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for full license details.
 */

package com.turkbot.babytracker.nostr.amber

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import com.turkbot.babytracker.nostr.crypto.NostrSigner
import com.turkbot.babytracker.nostr.crypto.SignerType
import com.turkbot.babytracker.nostr.events.NostrEvent

/**
 * NIP-55 signer that delegates to an external Android signer app (e.g. Amber)
 * via the `nostrsigner:` URI scheme.
 *
 * Implements the NIP-55 intent transport:
 *   - Detection: query for activities that handle `nostrsigner:` URIs
 *   - get_public_key: `Intent(ACTION_VIEW, Uri.parse("nostrsigner:"))` with
 *     `type` = "get_public_key" — NO package set (lets user pick signer)
 *   - sign_event / nip44_encrypt / nip44_decrypt: same URI scheme but with
 *     `package` set to the signer's package name (returned from get_public_key)
 *
 * The private key never enters this app. The signer holds it and performs:
 *   - Schnorr event signing
 *   - NIP-44 encryption / decryption
 *
 * Result handling (per NIP-55):
 *   - resultCode != RESULT_OK → signer error (crash)
 *   - `rejected` extra == true → user denied the request
 *   - `result` extra → the method result (pubkey, signature, ciphertext, etc.)
 *   - `event` extra → signed event JSON (sign_event only)
 *   - `package` extra → signer package name (get_public_key only)
 */
class AmberSigner(
    override val npub: String,
    private val pubkeyHexStr: String,
    private val signerPackage: String
) : NostrSigner {

    override val pubkeyHex: String get() = pubkeyHexStr
    override val hasLocalPrivateKey: Boolean = false
    override val type: SignerType = SignerType.AMBER

    companion object {
        private const val TAG = "AmberSigner"

        // NIP-55 intent extras
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_RESULT = "result"
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_REJECTED = "rejected"
        private const val EXTRA_PUBKEY = "pubkey"
        private const val EXTRA_CURRENT_USER = "current_user"
        private const val EXTRA_ID = "id"

        // NIP-55 method names
        private const val METHOD_GET_PUBKEY = "get_public_key"
        private const val METHOD_SIGN_EVENT = "sign_event"
        private const val METHOD_NIP44_ENCRYPT = "nip44_encrypt"
        private const val METHOD_NIP44_DECRYPT = "nip44_decrypt"

        /**
         * Result of requesting pubkey from a NIP-55 signer.
         */
        data class SignerLoginResult(
            val npub: String,
            val pubkeyHex: String,
            val signerPackage: String
        )

        /**
         * Request the user's pubkey from any installed NIP-55 signer.
         * This triggers the signer's permission dialog for the first time.
         *
         * Per NIP-55: the get_public_key intent does NOT set the package —
         * the system shows a chooser if multiple signers are installed.
         *
         * Returns the npub + hex pubkey + signer package name, or null if
         * the user denied, no signer is installed, or the response was invalid.
         */
        suspend fun requestPubkey(): SignerLoginResult? {
            if (!AmberBridge.isBound()) {
                Log.e(TAG, "AmberBridge not bound")
                return null
            }
            return try {
                // Per NIP-55: omit package for get_public_key so the user can
                // pick which signer to use (if multiple are installed)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
                    putExtra(EXTRA_TYPE, METHOD_GET_PUBKEY)
                }
                val result = AmberBridge.launch(intent)
                if (result.resultCode != -1) {  // Activity.RESULT_OK = -1
                    Log.e(TAG, "Signer returned code ${result.resultCode}")
                    return null
                }
                if (result.data?.getBooleanExtra(EXTRA_REJECTED, false) == true) {
                    Log.d(TAG, "User rejected get_public_key")
                    return null
                }
                val pubkeyHex = result.data?.getStringExtra(EXTRA_RESULT)
                if (pubkeyHex.isNullOrBlank()) {
                    Log.e(TAG, "Signer returned empty pubkey")
                    return null
                }
                // The signer returns its package name so we can address
                // all subsequent requests to it
                val pkg = result.data?.getStringExtra(EXTRA_PACKAGE)
                if (pkg.isNullOrBlank()) {
                    Log.e(TAG, "Signer did not return package name")
                    return null
                }
                // Convert hex pubkey to npub for storage/display
                val npub = try {
                    val pubBytes = com.turkbot.babytracker.nostr.crypto.NostrKeys.fromHex(pubkeyHex)
                    com.turkbot.babytracker.nostr.crypto.NostrKeys.encodeNpub(pubBytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid pubkey hex from signer: $pubkeyHex", e)
                    return null
                }
                SignerLoginResult(npub = npub, pubkeyHex = pubkeyHex, signerPackage = pkg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get pubkey from signer", e)
                null
            }
        }
    }

    override suspend fun signEvent(unsigned: NostrEvent): NostrEvent {
        if (!AmberBridge.isBound()) {
            throw IllegalStateException("AmberBridge not bound")
        }
        val unsignedJson = unsigned.copy(sig = "").toJsonObject()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$unsignedJson")).apply {
            setPackage(signerPackage)
            putExtra(EXTRA_TYPE, METHOD_SIGN_EVENT)
            putExtra(EXTRA_CURRENT_USER, pubkeyHexStr)
            putExtra(EXTRA_ID, unsigned.id)
        }
        val result = AmberBridge.launch(intent)
        if (result.resultCode != -1) {
            throw AmberException("Signer returned code ${result.resultCode}")
        }
        if (result.data?.getBooleanExtra(EXTRA_REJECTED, false) == true) {
            throw AmberException("User rejected sign request")
        }
        // Prefer the full signed event JSON; fall back to just the signature
        val signedEventJson = result.data?.getStringExtra(EXTRA_EVENT)
        if (!signedEventJson.isNullOrBlank()) {
            return NostrEvent.fromJson(signedEventJson)
        }
        val signature = result.data?.getStringExtra(EXTRA_RESULT)
        if (!signature.isNullOrBlank()) {
            return unsigned.copy(sig = signature)
        }
        throw AmberException("Signer returned no signature")
    }

    override suspend fun nip44Encrypt(plaintext: String, recipientPubkeyHex: String): String {
        if (!AmberBridge.isBound()) {
            throw IllegalStateException("AmberBridge not bound")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$plaintext")).apply {
            setPackage(signerPackage)
            putExtra(EXTRA_TYPE, METHOD_NIP44_ENCRYPT)
            putExtra(EXTRA_PUBKEY, recipientPubkeyHex)
            putExtra(EXTRA_CURRENT_USER, pubkeyHexStr)
        }
        val result = AmberBridge.launch(intent)
        if (result.resultCode != -1) {
            throw AmberException("Signer encrypt returned code ${result.resultCode}")
        }
        if (result.data?.getBooleanExtra(EXTRA_REJECTED, false) == true) {
            throw AmberException("User rejected encrypt request")
        }
        val ciphertext = result.data?.getStringExtra(EXTRA_RESULT)
        if (ciphertext.isNullOrBlank()) {
            throw AmberException("Signer returned no ciphertext")
        }
        return ciphertext
    }

    override suspend fun nip44Decrypt(payload: String, senderPubkeyHex: String): String {
        if (!AmberBridge.isBound()) {
            throw IllegalStateException("AmberBridge not bound")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$payload")).apply {
            setPackage(signerPackage)
            putExtra(EXTRA_TYPE, METHOD_NIP44_DECRYPT)
            putExtra(EXTRA_PUBKEY, senderPubkeyHex)
            putExtra(EXTRA_CURRENT_USER, pubkeyHexStr)
        }
        val result = AmberBridge.launch(intent)
        if (result.resultCode != -1) {
            throw AmberException("Signer decrypt returned code ${result.resultCode}")
        }
        if (result.data?.getBooleanExtra(EXTRA_REJECTED, false) == true) {
            throw AmberException("User rejected decrypt request")
        }
        val plaintext = result.data?.getStringExtra(EXTRA_RESULT)
        if (plaintext.isNullOrBlank()) {
            throw AmberException("Signer returned no plaintext")
        }
        return plaintext
    }
}

class AmberException(message: String) : Exception(message)
