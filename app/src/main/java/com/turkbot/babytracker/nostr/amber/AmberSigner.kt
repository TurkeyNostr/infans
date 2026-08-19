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
import android.util.Log
import androidx.activity.result.ActivityResult
import com.turkbot.babytracker.nostr.crypto.NostrSigner
import com.turkbot.babytracker.nostr.crypto.SignerType
import com.turkbot.babytracker.nostr.events.NostrEvent

/**
 * NIP-55 signer that delegates to the Amber app via Android Intents.
 *
 * The private key never enters this app — Amber holds it and performs:
 *   - Schnorr event signing
 *   - NIP-44 encryption / decryption (ECDH + AES-GCM)
 *
 * Flow for each operation:
 *   1. Build an Intent with the appropriate action + extras
 *   2. AmberBridge.launch(intent) suspends and sends to Amber
 *   3. Amber shows permission dialog (first time per app)
 *   4. Result returns via ActivityResult → parsed here
 *
 * Amber package: com.greenart7c3.amber
 */
class AmberSigner(
    override val npub: String,
    private val pubkeyHexStr: String
) : NostrSigner {

    override val pubkeyHex: String get() = pubkeyHexStr
    override val hasLocalPrivateKey: Boolean = false
    override val type: SignerType = SignerType.AMBER

    companion object {
        private const val TAG = "AmberSigner"
        private const val AMBER_PACKAGE = "com.greenart7c3.amber"

        // Intent actions
        private const val ACTION_SIGN_EVENT = "com.greenart7c3.amber.SIGN_EVENT"
        private const val ACTION_NIP44_ENCRYPT = "com.greenart7c3.amber.NIP44_ENCRYPT"
        private const val ACTION_NIP44_DECRYPT = "com.greenart7c3.amber.NIP44_DECRYPT"
        private const val ACTION_GET_PUBKEY = "com.greenart7c3.amber.GET_PUBKEY"

        // Intent extras
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_PUBKEY = "pubkey"
        private const val EXTRA_PUBKEY_TO = "pubkey_to"        // recipient (encrypt)
        private const val EXTRA_PUBKEY_FROM = "pubkey_from"    // sender (decrypt)
        private const val EXTRA_PLAINTEXT = "plaintext"
        private const val EXTRA_CIPHERTEXT = "ciphertext"
        private const val EXTRA_RESULT = "result"
        private const val EXTRA_SIGNATURE = "signature"
        private const val EXTRA_SIGNED_EVENT = "signed_event"
        private const val EXTRA_ERROR = "error"
        private const val EXTRA_RELAYS = "relays"

        /**
         * Result of requesting pubkey from Amber — may include relay preferences.
         */
        data class AmberLoginResult(
            val npub: String,
            val relays: List<String>? = null
        )

        /**
         * Request the user's pubkey from Amber.
         * This triggers Amber's permission dialog for the first time.
         * Returns the npub + optional relay list, or null if the user denied or Amber isn't installed.
         *
         * Amber may include a "relays" string-array extra in the response intent,
         * containing the user's preferred relays (from their Amber settings).
         */
        suspend fun requestPubkey(): AmberLoginResult? {
            if (!AmberBridge.isBound()) {
                Log.e(TAG, "AmberBridge not bound")
                return null
            }
            return try {
                val intent = Intent(ACTION_GET_PUBKEY).apply {
                    setPackage(AMBER_PACKAGE)
                }
                val result = AmberBridge.launch(intent)
                if (result.resultCode != -1) {  // Activity.RESULT_OK = -1
                    Log.e(TAG, "Amber returned code ${result.resultCode}")
                    return null
                }
                val npub = result.data?.getStringExtra(EXTRA_PUBKEY)
                    ?: result.data?.getStringExtra(EXTRA_RESULT)
                if (npub.isNullOrBlank()) {
                    Log.e(TAG, "Amber returned empty pubkey")
                    null
                } else {
                    // Amber may return preferred relays as a string array
                    val relayArray = result.data?.getStringArrayExtra(EXTRA_RELAYS)
                    val relays = relayArray?.toList()?.filter { it.isNotBlank() }
                    if (relays != null) {
                        Log.d(TAG, "Amber returned ${relays.size} relays")
                    }
                    AmberLoginResult(npub, relays)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get pubkey from Amber", e)
                null
            }
        }

        /**
         * Check whether Amber is installed and the bridge is ready.
         * (Actual install detection is done at the UI layer via package manager.)
         */
        fun isAvailable(): Boolean = AmberBridge.isBound()
    }

    override suspend fun signEvent(unsigned: NostrEvent): NostrEvent {
        if (!AmberBridge.isBound()) {
            throw IllegalStateException("AmberBridge not bound")
        }
        // Send the unsigned event JSON (with id, without sig) to Amber
        val unsignedJson = unsigned.copy(sig = "").toJsonObject()
        val intent = Intent(ACTION_SIGN_EVENT).apply {
            setPackage(AMBER_PACKAGE)
            putExtra(EXTRA_EVENT, unsignedJson)
            putExtra(EXTRA_PUBKEY, pubkeyHexStr)
        }
        val result = AmberBridge.launch(intent)
        if (result.resultCode != -1) {
            throw AmberException("Amber sign returned code ${result.resultCode}")
        }
        val data = result.data
        // Amber may return the full signed event JSON or just the signature
        val signedEventJson = data?.getStringExtra(EXTRA_SIGNED_EVENT)
            ?: data?.getStringExtra(EXTRA_EVENT)
        if (signedEventJson != null) {
            return NostrEvent.fromJson(signedEventJson)
        }
        val signature = data?.getStringExtra(EXTRA_SIGNATURE)
        if (signature != null) {
            return unsigned.copy(sig = signature)
        }
        val error = data?.getStringExtra(EXTRA_ERROR)
        throw AmberException("Amber sign returned no signature${error?.let { ": $it" } ?: ""}")
    }

    override suspend fun nip44Encrypt(plaintext: String, recipientPubkeyHex: String): String {
        if (!AmberBridge.isBound()) {
            throw IllegalStateException("AmberBridge not bound")
        }
        val intent = Intent(ACTION_NIP44_ENCRYPT).apply {
            setPackage(AMBER_PACKAGE)
            putExtra(EXTRA_PUBKEY, pubkeyHexStr)
            putExtra(EXTRA_PUBKEY_TO, recipientPubkeyHex)
            putExtra(EXTRA_PLAINTEXT, plaintext)
        }
        val result = AmberBridge.launch(intent)
        if (result.resultCode != -1) {
            throw AmberException("Amber encrypt returned code ${result.resultCode}")
        }
        val ciphertext = result.data?.getStringExtra(EXTRA_RESULT)
            ?: result.data?.getStringExtra(EXTRA_CIPHERTEXT)
        if (ciphertext.isNullOrBlank()) {
            val error = result.data?.getStringExtra(EXTRA_ERROR)
            throw AmberException("Amber encrypt returned no ciphertext${error?.let { ": $it" } ?: ""}")
        }
        return ciphertext
    }

    override suspend fun nip44Decrypt(payload: String, senderPubkeyHex: String): String {
        if (!AmberBridge.isBound()) {
            throw IllegalStateException("AmberBridge not bound")
        }
        val intent = Intent(ACTION_NIP44_DECRYPT).apply {
            setPackage(AMBER_PACKAGE)
            putExtra(EXTRA_PUBKEY, pubkeyHexStr)
            putExtra(EXTRA_PUBKEY_FROM, senderPubkeyHex)
            putExtra(EXTRA_CIPHERTEXT, payload)
        }
        val result = AmberBridge.launch(intent)
        if (result.resultCode != -1) {
            throw AmberException("Amber decrypt returned code ${result.resultCode}")
        }
        val plaintext = result.data?.getStringExtra(EXTRA_RESULT)
            ?: result.data?.getStringExtra(EXTRA_PLAINTEXT)
        if (plaintext == null) {
            val error = result.data?.getStringExtra(EXTRA_ERROR)
            throw AmberException("Amber decrypt returned no plaintext${error?.let { ": $it" } ?: ""}")
        }
        return plaintext
    }
}

class AmberException(message: String) : Exception(message)
