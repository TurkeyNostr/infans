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

package com.turkbot.babytracker.nostr.crypto

import com.turkbot.babytracker.nostr.events.NostrEvent

/**
 * Abstraction over Nostr signing + NIP-44 encryption.
 *
 * Two implementations:
 *   - LocalSigner:  private key lives in SecureKeyStore (existing behavior)
 *   - AmberSigner:  private key lives in the Amber app (NIP-55), signing/encrypt
 *                   is delegated via Android Intents
 *
 * Gift-wrap messaging always generates a one-time local key for the outer gift-wrap
 * layer (privacy requirement — the wrap key must be random and disposable).
 * That one-time key uses LocalSigner directly, never this interface.
 */
interface NostrSigner {
    val npub: String
    val pubkeyHex: String
    val hasLocalPrivateKey: Boolean
    val type: SignerType

    /** Sign an unsigned event (id already computed, sig empty). Returns the signed event. */
    suspend fun signEvent(unsigned: NostrEvent): NostrEvent

    /** NIP-44 encrypt plaintext to a recipient. Returns base64 payload. */
    suspend fun nip44Encrypt(plaintext: String, recipientPubkeyHex: String): String

    /** NIP-44 decrypt a base64 payload from a sender. Returns plaintext. */
    suspend fun nip44Decrypt(payload: String, senderPubkeyHex: String): String
}

enum class SignerType { LOCAL, AMBER }

/**
 * Signer backed by a local private key (stored in EncryptedSharedPreferences).
 * This is the original behavior — all crypto happens in-process.
 */
class LocalSigner(private val keyPair: NostrKeyPair) : NostrSigner {

    override val npub: String get() = keyPair.npub
    override val pubkeyHex: String get() = NostrKeys.toHex(keyPair.publicKey)
    override val hasLocalPrivateKey: Boolean = true
    override val type: SignerType = SignerType.LOCAL

    override suspend fun signEvent(unsigned: NostrEvent): NostrEvent {
        val idBytes = NostrKeys.fromHex(unsigned.id)
        val sigBytes = NostrEventSigner.signSchnorr(idBytes, keyPair.privateKey)
        return unsigned.copy(sig = NostrKeys.toHex(sigBytes))
    }

    override suspend fun nip44Encrypt(plaintext: String, recipientPubkeyHex: String): String {
        val recipientPub = NostrKeys.fromHex(recipientPubkeyHex)
        return Nip44.encrypt(plaintext, keyPair.privateKey, recipientPub)
    }

    override suspend fun nip44Decrypt(payload: String, senderPubkeyHex: String): String {
        val senderPub = NostrKeys.fromHex(senderPubkeyHex)
        return Nip44.decrypt(payload, keyPair.privateKey, senderPub)
    }
}
