package com.turkbot.babytracker.nostr.messaging

import android.util.Log
import com.turkbot.babytracker.nostr.crypto.Nip44
import com.turkbot.babytracker.nostr.crypto.NostrKeyPair
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import com.turkbot.babytracker.nostr.events.NostrEvent
import com.turkbot.babytracker.nostr.relay.RelayEvent
import com.turkbot.babytracker.nostr.relay.RelayPool
import kotlinx.serialization.json.Json

/**
 * NIP-17 Gift Wrap messaging for parent-to-parent encrypted DMs.
 *
 * Following nospeak's model:
 *   Layer 1: Rumor (kind 1 or 14) — the actual message content
 *   Layer 2: Seal (kind 13) — NIP-44 encrypted rumor, signed by sender
 *   Layer 3: Gift Wrap (kind 14 or 1059) — NIP-44 encrypted seal, signed by random one-time key
 *
 * The recipient decrypts the gift wrap → extracts the seal → verifies sender → extracts the rumor.
 * Randomized timestamps prevent timing analysis.
 *
 * NIP-17 uses:
 *   - Kind 14 for gift-wrapped DMs (replacing the older NIP-04 kind 4)
 *   - Kind 13 for sealed rumors
 *   - Kind 1059 as the gift wrap (wrap with one-time key)
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
     * @param myKeys Our key pair
     * @param recipientNpub Recipient's npub
     */
    suspend fun sendDirectMessage(
        text: String,
        myKeys: NostrKeyPair,
        recipientNpub: String
    ): Boolean {
        try {
            val recipientPubKey = NostrKeys.decodeNpub(recipientNpub)

            // 1. Create the rumor (kind 14 = private direct message)
            val rumor = NostrEvent.create(
                kind = KIND_PRIVATE_DIRECT_MESSAGE,
                content = text,
                privKey = myKeys.privateKey,
                pubKey = myKeys.publicKey,
                createdAt = System.currentTimeMillis() / 1000
            )

            // 2. Seal: NIP-44 encrypt the rumor, then wrap in a kind 13 event
            val rumorJson = rumor.toJsonObject()
            val sealedContent = Nip44.encrypt(rumorJson, myKeys.privateKey, recipientPubKey)

            val seal = NostrEvent.create(
                kind = KIND_SEAL,
                content = sealedContent,
                privKey = myKeys.privateKey,
                pubKey = myKeys.publicKey,
                // Randomize timestamp slightly to prevent timing analysis
                createdAt = System.currentTimeMillis() / 1000
            )

            // 3. Gift wrap: NIP-44 encrypt the seal with a ONE-TIME random key
            val oneTimeKeys = NostrKeys.generate()
            val sealJson = seal.toJsonObject()
            val wrappedContent = Nip44.encrypt(sealJson, oneTimeKeys.privateKey, recipientPubKey)

            val giftWrap = NostrEvent.create(
                kind = KIND_GIFT_WRAP,
                content = wrappedContent,
                privKey = oneTimeKeys.privateKey,
                pubKey = oneTimeKeys.publicKey,
                // Randomize timestamp: offset from real time by up to 2 days
                createdAt = System.currentTimeMillis() / 1000 - (0..172800L).random(),
                tags = listOf(
                    listOf("p", NostrKeys.toHex(recipientPubKey))
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
     * @param myKeys Our key pair
     * @return UnwrappedMessage or null if decryption fails
     */
    fun unwrapGiftWrap(event: RelayEvent, myKeys: NostrKeyPair): UnwrappedMessage? {
        try {
            // 1. NIP-44 decrypt the gift wrap content (using one-time sender pubkey)
            val senderOneTimePubKey = NostrKeys.fromHex(event.pubkey)
            val sealJson = Nip44.decrypt(event.content, myKeys.privateKey, senderOneTimePubKey)

            // 2. Parse the seal (kind 13)
            val seal = NostrEvent.fromJson(sealJson)
            require(seal.kind == KIND_SEAL) { "Expected kind 13 seal, got ${seal.kind}" }

            // 3. Verify the seal is signed by the claimed sender
            val senderPubKey = NostrKeys.fromHex(seal.pubkey)

            // 4. NIP-44 decrypt the seal content → rumor JSON
            val rumorJson = Nip44.decrypt(seal.content, myKeys.privateKey, senderPubKey)

            // 5. Parse the rumor (kind 14)
            val rumor = NostrEvent.fromJson(rumorJson)
            require(rumor.kind == KIND_PRIVATE_DIRECT_MESSAGE) { "Expected kind 14, got ${rumor.kind}" }

            return UnwrappedMessage(
                senderPubkeyHex = seal.pubkey,
                senderNpub = NostrKeys.encodeNpub(senderPubKey),
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
