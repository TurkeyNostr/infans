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
import java.security.SecureRandom

/**
 * Nostr key pair. Private key is 32 bytes; public key is 32-byte X-only Schnorr.
 */
data class NostrKeyPair(
    val privateKey: ByteArray,   // 32 bytes
    val publicKey: ByteArray     // 32 bytes (x-only)
) {
    val npub: String get() = Bech32.encode("npub", publicKey)
    val nsec: String get() = Bech32.encode("nsec", privateKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NostrKeyPair) return false
        return privateKey.contentEquals(other.privateKey) && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = privateKey.contentHashCode() * 31 + publicKey.contentHashCode()
}

object NostrKeys {

    private val secp = Secp256k1.get()

    /**
     * Generate a new random key pair.
     */
    fun generate(): NostrKeyPair {
        val priv = ByteArray(32)
        SecureRandom().nextBytes(priv)
        return fromPrivateKey(priv)
    }

    /**
     * Derive a key pair from an existing private key (32 bytes).
     * pubkeyCreate returns 65-byte uncompressed; pubKeyCompress gives 33-byte;
     * strip prefix byte → 32-byte x-only.
     */
    fun fromPrivateKey(priv: ByteArray): NostrKeyPair {
        require(priv.size == 32) { "Private key must be 32 bytes" }
        val uncompressed = secp.pubkeyCreate(priv)   // 65 bytes: [0x04, x(32), y(32)]
        val compressed = secp.pubKeyCompress(uncompressed) // 33 bytes: [prefix, x(32)]
        val pub = compressed.copyOfRange(1, 33)      // x-only 32 bytes
        return NostrKeyPair(priv, pub)
    }

    /**
     * Decode an nsec string to raw 32-byte private key.
     */
    fun decodeNsec(nsec: String): ByteArray {
        val (hrp, data) = Bech32.decode(nsec)
        require(hrp == "nsec") { "Expected nsec prefix, got $hrp" }
        require(data.size == 32) { "Invalid key length" }
        return data
    }

    /**
     * Decode an npub string to raw 32-byte public key.
     */
    fun decodeNpub(npub: String): ByteArray {
        val (hrp, data) = Bech32.decode(npub)
        require(hrp == "npub") { "Expected npub prefix, got $hrp" }
        require(data.size == 32) { "Invalid key length" }
        return data
    }

    /**
     * Encode raw 32-byte public key as npub.
     */
    fun encodeNpub(pubkey: ByteArray): String = Bech32.encode("npub", pubkey)

    /**
     * Encode raw 32-byte private key as nsec.
     */
    fun encodeNsec(privkey: ByteArray): String = Bech32.encode("nsec", privkey)

    /**
     * Convert bytes to hex string.
     */
    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * Convert hex string to ByteArray.
     */
    fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex length" }
        return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /**
     * Get the Secp256k1 instance (shared).
     */
    fun secp(): Secp256k1 = secp
}
