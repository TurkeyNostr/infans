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

import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest

/**
 * Nostr event creation and signing.
 * Events are signed with Schnorr (BIP-340) via secp256k1-kmp.
 */
object NostrEventSigner {

    private val secp = Secp256k1.get()

    /**
     * Compute the Nostr event id (SHA-256 of the canonical serialization).
     *
     * id = SHA256( [0, pubkey_hex, created_at, kind, tags, content] )
     * serialized as a JSON array (without id and sig fields).
     */
    fun computeId(pubkeyHex: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): ByteArray {
        val serialized = buildJsonArray(listOf(
            "0", pubkeyHex, createdAt.toString(), kind.toString(),
            jsonArrayOf(tags),
            jsonString(content)
        ))
        return MessageDigest.getInstance("SHA-256").digest(serialized.toByteArray(Charsets.UTF_8))
    }

    /**
     * Sign the event id with Schnorr (BIP-340).
     */
    fun signSchnorr(id: ByteArray, privKey: ByteArray): ByteArray {
        return secp.signSchnorr(id, privKey, null)
    }

    /**
     * Verify a Schnorr (BIP-340) signature.
     * @param id        the 32-byte message hash (event id)
     * @param signature the 64-byte Schnorr signature
     * @param pubKey    the 32-byte x-only public key
     * @return true if the signature is valid
     */
    fun verifySchnorr(id: ByteArray, signature: ByteArray, pubKey: ByteArray): Boolean {
        return try {
            secp.verifySchnorr(signature, id, pubKey)
        } catch (e: Exception) {
            false
        }
    }

    // ─── JSON helpers (manual, no external dep) ───────────

    private fun buildJsonArray(elements: List<String>): String {
        return "[" + elements.joinToString(",") + "]"
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u%04x".format(c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    private fun jsonArrayOf(tags: List<List<String>>): String {
        return "[" + tags.joinToString(",") { tag ->
            "[" + tag.joinToString(",") { jsonString(it) } + "]"
        } + "]"
    }
}

/**
 * Helper to get Secp256k1 instance.
 */
object Secp256k1Helper {
    fun get(): Secp256k1 = Secp256k1.get()
}
