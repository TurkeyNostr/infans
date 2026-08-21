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

package com.turkbot.babytracker.nostr.events

import com.turkbot.babytracker.nostr.crypto.NostrEventSigner
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import com.turkbot.babytracker.nostr.crypto.NostrSigner
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * A Nostr event. Serialized to JSON for relay communication.
 * Signed with Schnorr (BIP-340) via secp256k1-kmp (local) or Amber (NIP-55).
 */
@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val created_at: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
) {
    companion object {

        /**
         * Build and sign a Nostr event using a local private key.
         * Kept for backward compatibility (one-time gift-wrap keys use this).
         */
        fun create(
            kind: Int,
            content: String,
            privKey: ByteArray,
            pubKey: ByteArray,
            tags: List<List<String>> = emptyList(),
            createdAt: Long = System.currentTimeMillis() / 1000
        ): NostrEvent {
            val pubKeyHex = NostrKeys.toHex(pubKey)

            // Compute event id
            val idBytes = NostrEventSigner.computeId(pubKeyHex, createdAt, kind, tags, content)
            val idHex = NostrKeys.toHex(idBytes)

            // Schnorr sign (BIP-340)
            val sigBytes = NostrEventSigner.signSchnorr(idBytes, privKey)
            val sigHex = NostrKeys.toHex(sigBytes)

            return NostrEvent(
                id = idHex,
                pubkey = pubKeyHex,
                created_at = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = sigHex
            )
        }

        /**
         * Build an unsigned event (id computed, sig empty) for signing by a [NostrSigner].
         * Used by both LocalSigner and AmberSigner.
         */
        fun unsigned(
            kind: Int,
            content: String,
            pubKeyHex: String,
            tags: List<List<String>> = emptyList(),
            createdAt: Long = System.currentTimeMillis() / 1000
        ): NostrEvent {
            val idBytes = NostrEventSigner.computeId(pubKeyHex, createdAt, kind, tags, content)
            return NostrEvent(
                id = NostrKeys.toHex(idBytes),
                pubkey = pubKeyHex,
                created_at = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = ""
            )
        }

        /**
         * Build and sign an event via a [NostrSigner] (local or Amber).
         */
        suspend fun createSigned(
            kind: Int,
            content: String,
            signer: NostrSigner,
            tags: List<List<String>> = emptyList(),
            createdAt: Long = System.currentTimeMillis() / 1000
        ): NostrEvent {
            val unsigned = unsigned(kind, content, signer.pubkeyHex, tags, createdAt)
            return signer.signEvent(unsigned)
        }

        /**
         * Parse a Nostr event from JSON.
         */
        fun fromJson(jsonStr: String): NostrEvent {
            val obj = Json.parseToJsonElement(jsonStr).jsonObject
            return NostrEvent(
                id = obj["id"]!!.jsonPrimitive.content,
                pubkey = obj["pubkey"]!!.jsonPrimitive.content,
                created_at = obj["created_at"]!!.jsonPrimitive.long,
                kind = obj["kind"]!!.jsonPrimitive.int,
                tags = obj["tags"]!!.jsonArray.map { it.jsonArray.map { e -> e.jsonPrimitive.content } },
                content = obj["content"]!!.jsonPrimitive.content,
                sig = obj["sig"]!!.jsonPrimitive.content
            )
        }
    }

    /**
     * Serialize as a plain JSON object.
     */
    fun toJsonObject(): String {
        return Json.encodeToString(this)
    }

    /**
     * Verify this event's Schnorr signature against its id and pubkey.
     * @return true if the signature is cryptographically valid
     */
    fun verifySignature(): Boolean {
        return try {
            val idBytes = NostrKeys.fromHex(id)
            val sigBytes = NostrKeys.fromHex(sig)
            val pubBytes = NostrKeys.fromHex(pubkey)
            NostrEventSigner.verifySchnorr(idBytes, sigBytes, pubBytes)
        } catch (e: Exception) {
            false
        }
    }
}
