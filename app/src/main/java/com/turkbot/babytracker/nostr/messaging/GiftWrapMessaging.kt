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

package com.turkbot.babytracker.nostr.messaging

import android.util.Log
import com.turkbot.babytracker.nostr.crypto.LocalSigner
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import com.turkbot.babytracker.nostr.crypto.NostrSigner
import com.turkbot.babytracker.nostr.events.NostrEvent
import com.turkbot.babytracker.nostr.relay.RelayEvent
import com.turkbot.babytracker.nostr.relay.RelayPool
import kotlinx.serialization.json.Json

/**
 * NIP-17 Gift Wrap messaging for parent-to-parent encrypted DMs.
 *
 * Following nospeak's model:
 *   Layer 1: Rumor (kind 14) — the actual message content
 *   Layer 2: Seal (kind 13) — NIP-44 encrypted rumor, signed by sender
 *   Layer 3: Gift Wrap (kind 1059) — NIP-44 encrypted seal, signed by random one-time key
 *
 * The recipient decrypts the gift wrap → extracts the seal → verifies sender → extracts the rumor.
 * Randomized timestamps prevent timing analysis.
 *
 * Signing: the seal is signed by [signer] (LocalSigner or AmberSigner).
 * The gift wrap is always signed by a one-time local key — this is a privacy requirement
 * of NIP-17; the wrap key must be random and disposable, so we never delegate it to Amber.
 */
class GiftWrapMessaging(
    private val relayPool: RelayPool
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "GiftWrap"
        const val KIND_SEAL = 13
        const val KIND_GIFT_WRAP = 1059
        const val KIND_PRIVATE_DIRECT_MESSAGE = 14
    }

    /**
     * Send an encrypted DM to another parent.
     *
     * @param text The message text
     * @param signer Our signer (local key or Amber)
     * @param recipientNpub Recipient's npub
     */
    suspend fun sendDirectMessage(
        text: String,
        signer: NostrSigner,
        recipientNpub: String
    ): Boolean {
        try {
            val recipientPubKey = NostrKeys.decodeNpub(recipientNpub)
            val recipientPubHex = NostrKeys.toHex(recipientPubKey)

            // 1. Create the rumor (kind 14 = private direct message) — unsigned, included in seal
            val rumor = NostrEvent.unsigned(
                kind = KIND_PRIVATE_DIRECT_MESSAGE,
                content = text,
                pubKeyHex = signer.pubkeyHex,
                createdAt = System.currentTimeMillis() / 1000
            )

            // 2. Seal: NIP-44 encrypt the rumor via signer, then wrap in a kind 13 event
            //    The seal is signed by the sender (our signer).
            val rumorJson = rumor.toJsonObject()
            val sealedContent = signer.nip44Encrypt(rumorJson, recipientPubHex)

            val seal = NostrEvent.createSigned(
                kind = KIND_SEAL,
                content = sealedContent,
                signer = signer,
                createdAt = System.currentTimeMillis() / 1000
            )

            // 3. Gift wrap: NIP-44 encrypt the seal with a ONE-TIME random local key.
            //    This key is never stored, never sent to Amber — it's disposable.
            val oneTimeKeys = NostrKeys.generate()
            val oneTimeSigner = LocalSigner(oneTimeKeys)
            val sealJson = seal.toJsonObject()
            val wrappedContent = oneTimeSigner.nip44Encrypt(sealJson, recipientPubHex)

            val giftWrap = NostrEvent.create(
                kind = KIND_GIFT_WRAP,
                content = wrappedContent,
                privKey = oneTimeKeys.privateKey,
                pubKey = oneTimeKeys.publicKey,
                // Randomize timestamp: offset from real time by up to 2 days
                createdAt = System.currentTimeMillis() / 1000 - (0..172800L).random(),
                tags = listOf(
                    listOf("p", recipientPubHex)
                )
            )

            // 4. Publish the gift wrap to relays
            relayPool.publish(giftWrap.toJsonObject())
            Log.d(TAG, "DM sent to ${recipientNpub.take(20)}...")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send DM", e)
            return false
        }
    }

    /**
     * Unwrap a gift-wrapped DM (kind 1059) and return the plaintext message.
     *
     * @param event The gift wrap event from relays
     * @param signer Our signer (local key or Amber)
     * @return UnwrappedMessage or null if decryption fails
     */
    suspend fun unwrapGiftWrap(event: RelayEvent, signer: NostrSigner): UnwrappedMessage? {
        try {
            // 1. NIP-44 decrypt the gift wrap content (using one-time sender pubkey)
            //    We can use local decrypt for the outer layer only if we have the local key.
            //    With Amber, the decryption must go through Amber (our signer).
            val sealJson = signer.nip44Decrypt(event.content, event.pubkey)

            // 2. Parse the seal (kind 13)
            val seal = NostrEvent.fromJson(sealJson)
            require(seal.kind == KIND_SEAL) { "Expected kind 13 seal, got ${seal.kind}" }

            // 3. Verify the seal's Schnorr signature — prevents forged sender identity
            require(seal.verifySignature()) { "Seal signature verification failed" }

            // 4. NIP-44 decrypt the seal content → rumor JSON (via signer)
            val rumorJson = signer.nip44Decrypt(seal.content, seal.pubkey)

            // 5. Parse the rumor (kind 14)
            val rumor = NostrEvent.fromJson(rumorJson)
            require(rumor.kind == KIND_PRIVATE_DIRECT_MESSAGE) { "Expected kind 14, got ${rumor.kind}" }

            return UnwrappedMessage(
                senderPubkeyHex = seal.pubkey,
                senderNpub = NostrKeys.encodeNpub(NostrKeys.fromHex(seal.pubkey)),
                content = rumor.content,
                createdAt = rumor.created_at * 1000 // seconds → millis
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to unwrap gift wrap", e)
            return null
        }
    }
}

/**
 * A decrypted DM.
 */
data class UnwrappedMessage(
    val senderPubkeyHex: String,
    val senderNpub: String,
    val content: String,
    val createdAt: Long
)
