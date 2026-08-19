package com.turkbot.babytracker.nostr.events

import com.turkbot.babytracker.nostr.crypto.NostrEventSigner
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * A Nostr event. Serialized to JSON for relay communication.
 * Signed with Schnorr (BIP-340) via secp256k1-kmp.
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
         * Build and sign a Nostr event.
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
}
