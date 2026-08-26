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

package com.turkbot.babytracker.nostr.crypto

import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

/**
 * NIP-44 v2 conformance tests.
 *
 * Uses the official test vector from the NIP-44 spec plus round-trip and
 * edge-case tests to verify interop with other NIP-44 v2 implementations.
 *
 * Test vectors: https://github.com/paulmillr/nip44/blob/main/nip44.vectors.json
 *
 * NOTE: secp256k1-kmp's native JNI library does not load on the build server,
 * so we use BouncyCastle directly for pubkey derivation in tests. This is the
 * same ECDH code path used in Nip44.kt itself.
 */
class Nip44Test {

    // secp256k1 curve (BouncyCastle) — for deriving pubkeys from privkeys in tests
    private val curveParams: X9ECParameters = CustomNamedCurves.getByName("secp256k1")
    private val domain = ECDomainParameters(curveParams.curve, curveParams.g, curveParams.n, curveParams.h)

    /**
     * Derive x-only pubkey (32 bytes) from a private key using BouncyCastle.
     * Same as NostrKeys.fromPrivateKey() but without the secp256k1-kmp JNI dependency.
     */
    private fun derivePubKey(privKey: ByteArray): ByteArray {
        val privBigInt = BigInteger(1, privKey)
        val point = curveParams.g.multiply(privBigInt).normalize()
        return point.affineXCoord.encoded
    }

    // ─── Official test vector from the NIP-44 spec ────────────

    /**
     * Test vector from the spec (encrypt_decrypt category).
     * sec1 = 0x01, sec2 = 0x02, nonce = 0x01, plaintext = "a"
     */
    @Test
    fun `official test vector - conversation key matches`() {
        val sec1 = ByteArray(32).also { it[31] = 1 }
        val pub2 = derivePubKey(ByteArray(32).also { it[31] = 2 })

        val conversationKey = Nip44.getConversationKey(sec1, pub2)
        val expected = hexToBytes("c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d")
        assertArrayEquals(expected, conversationKey)
    }

    @Test
    fun `official test vector - encrypt produces correct payload`() {
        val sec1 = ByteArray(32).also { it[31] = 1 }
        val pub2 = derivePubKey(ByteArray(32).also { it[31] = 2 })

        val conversationKey = Nip44.getConversationKey(sec1, pub2)
        val nonce = ByteArray(32).also { it[31] = 1 }

        val payload = Nip44.encryptWithNonce("a", conversationKey, nonce)

        // Expected payload from the spec:
        // AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb
        assertEquals(
            "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb",
            payload
        )
    }

    @Test
    fun `official test vector - decrypt recovers plaintext`() {
        val sec2 = ByteArray(32).also { it[31] = 2 }
        val pub1 = derivePubKey(ByteArray(32).also { it[31] = 1 })

        val conversationKey = Nip44.getConversationKey(sec2, pub1)
        val payload = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb"

        val plaintext = Nip44.decryptWithKey(payload, conversationKey)
        assertEquals("a", plaintext)
    }

    // ─── Conversation key symmetry ────────────────────────────

    @Test
    fun `conversation key is symmetric`() {
        val secA = ByteArray(32).also { it[31] = 0x42.toByte() }
        val secB = ByteArray(32).also { it[31] = 0x99.toByte() }
        val pubA = derivePubKey(secA)
        val pubB = derivePubKey(secB)

        val keyAB = Nip44.getConversationKey(secA, pubB)
        val keyBA = Nip44.getConversationKey(secB, pubA)
        assertArrayEquals(keyAB, keyBA)
    }

    // ─── Round-trip encrypt/decrypt ───────────────────────────

    @Test
    fun `round-trip encrypt then decrypt`() {
        val secA = ByteArray(32).also { it[31] = 0x42.toByte() }
        val secB = ByteArray(32).also { it[31] = 0x99.toByte() }
        val pubB = derivePubKey(secB)
        val pubA = derivePubKey(secA)

        val message = "{\"feedings\":[{\"amount\":120,\"type\":\"bottle\"}],\"sleeps\":[]}"
        val encrypted = Nip44.encrypt(message, secA, pubB)
        val decrypted = Nip44.decrypt(encrypted, secB, pubA)
        assertEquals(message, decrypted)
    }

    @Test
    fun `self-encrypt then self-decrypt`() {
        val sec = ByteArray(32).also { it[31] = 0x07.toByte() }
        val pub = derivePubKey(sec)

        val message = "backup payload test"
        val encrypted = Nip44.selfEncrypt(message, sec, pub)
        val decrypted = Nip44.selfDecrypt(encrypted, sec, pub)
        assertEquals(message, decrypted)
    }

    @Test
    fun `round-trip with unicode`() {
        val secA = ByteArray(32).also { it[31] = 0x11.toByte() }
        val secB = ByteArray(32).also { it[31] = 0x22.toByte() }
        val pubB = derivePubKey(secB)
        val pubA = derivePubKey(secA)

        val message = "Feeding note: 瓶喂 120ml — café ☕"
        val encrypted = Nip44.encrypt(message, secA, pubB)
        val decrypted = Nip44.decrypt(encrypted, secB, pubA)
        assertEquals(message, decrypted)
    }

    @Test
    fun `round-trip with large message`() {
        val secA = ByteArray(32).also { it[31] = 0x33.toByte() }
        val secB = ByteArray(32).also { it[31] = 0x44.toByte() }
        val pubB = derivePubKey(secB)
        val pubA = derivePubKey(secA)

        // 100KB message — exercises multiple padding chunks
        val message = "x".repeat(100_000)
        val encrypted = Nip44.encrypt(message, secA, pubB)
        val decrypted = Nip44.decrypt(encrypted, secB, pubA)
        assertEquals(message, decrypted)
    }

    // ─── Padding tests (pure crypto, no secp needed) ──────────

    @Test
    fun `calcPaddedLen - minimum is 32`() {
        assertEquals(32, Nip44.calcPaddedLen(1))
        assertEquals(32, Nip44.calcPaddedLen(32))
    }

    @Test
    fun `calcPaddedLen - small messages use 32-byte chunks`() {
        // For sizes 33-256, next_power <= 256 so chunk=32
        assertEquals(64, Nip44.calcPaddedLen(33))
        assertEquals(64, Nip44.calcPaddedLen(64))
        assertEquals(96, Nip44.calcPaddedLen(65))
        assertEquals(256, Nip44.calcPaddedLen(225))
        assertEquals(256, Nip44.calcPaddedLen(256))
    }

    @Test
    fun `calcPaddedLen - medium messages switch to next_power divided by 8`() {
        // 257: next_power=512, chunk=64 → 64*ceil(257/64) = 64*5 = 320
        assertEquals(320, Nip44.calcPaddedLen(257))
        // 512: next_power=512, chunk=64 → 64*8 = 512
        assertEquals(512, Nip44.calcPaddedLen(512))
    }

    @Test
    fun `pad and unpad round-trip for small message`() {
        val plaintext = "hello".toByteArray()
        val padded = Nip44.pad(plaintext)
        // 2-byte prefix + 32-byte padded content = 34 bytes
        assertEquals(34, padded.size)
        val unpadded = Nip44.unpad(padded)
        assertArrayEquals(plaintext, unpadded)
    }

    @Test
    fun `pad and unpad round-trip for 32-byte message`() {
        val plaintext = ByteArray(32) { it.toByte() }
        val padded = Nip44.pad(plaintext)
        assertEquals(34, padded.size)  // 2 + 32
        val unpadded = Nip44.unpad(padded)
        assertArrayEquals(plaintext, unpadded)
    }

    @Test
    fun `pad and unpad round-trip for 33-byte message`() {
        val plaintext = ByteArray(33) { it.toByte() }
        val padded = Nip44.pad(plaintext)
        // 2-byte prefix + calcPaddedLen(33) = 2 + 64 = 66
        assertEquals(66, padded.size)
        val unpadded = Nip44.unpad(padded)
        assertArrayEquals(plaintext, unpadded)
    }

    // ─── Negative tests (security) ────────────────────────────

    @Test
    fun `decrypt rejects tampered ciphertext`() {
        val secA = ByteArray(32).also { it[31] = 0x42.toByte() }
        val secB = ByteArray(32).also { it[31] = 0x99.toByte() }
        val pubB = derivePubKey(secB)
        val pubA = derivePubKey(secA)

        val encrypted = Nip44.encrypt("secret data", secA, pubB)

        // Tamper: flip a byte in the ciphertext region
        val tampered = tamperBase64(encrypted, 50)
        assertThrows(IllegalArgumentException::class.java) {
            Nip44.decrypt(tampered, secB, pubA)
        }
    }

    @Test
    fun `decrypt rejects wrong key`() {
        val secA = ByteArray(32).also { it[31] = 0x42.toByte() }
        val secB = ByteArray(32).also { it[31] = 0x99.toByte() }
        val secC = ByteArray(32).also { it[31] = 0x77.toByte() }
        val pubB = derivePubKey(secB)
        val pubC = derivePubKey(secC)

        val encrypted = Nip44.encrypt("secret", secA, pubB)

        // Try to decrypt with wrong key (C instead of B)
        assertThrows(IllegalArgumentException::class.java) {
            Nip44.decrypt(encrypted, secC, pubC)
        }
    }

    @Test
    fun `decrypt rejects too-short payload`() {
        val sec = ByteArray(32).also { it[31] = 1 }
        val pub = derivePubKey(sec)

        // Less than 132 base64 chars
        assertThrows(IllegalArgumentException::class.java) {
            Nip44.decrypt("AAAAAAAA", sec, pub)
        }
    }

    @Test
    fun `decrypt rejects unknown version`() {
        val sec = ByteArray(32).also { it[31] = 1 }
        val pub = derivePubKey(sec)

        // Build a valid-length base64 with version=1 instead of 2
        val fakePayload = ByteArray(99)
        fakePayload[0] = 1  // version 1 (deprecated)
        val b64 = java.util.Base64.getEncoder().encodeToString(fakePayload)
        assertThrows(IllegalArgumentException::class.java) {
            Nip44.decrypt(b64, sec, pub)
        }
    }

    @Test
    fun `decrypt rejects hash-prefixed payload`() {
        val sec = ByteArray(32).also { it[31] = 1 }
        val pub = derivePubKey(sec)

        // '#' prefix means non-base64 future encoding
        assertThrows(IllegalArgumentException::class.java) {
            Nip44.decrypt("#somefutureformat", sec, pub)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────

    private fun hexToBytes(hex: String): ByteArray {
        return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun tamperBase64(b64: String, position: Int): String {
        val bytes = java.util.Base64.getDecoder().decode(b64)
        // Flip a byte at the given position (within ciphertext region)
        val tamperPos = position.coerceIn(33, bytes.size - 33)
        bytes[tamperPos] = (bytes[tamperPos].toInt() xor 0x01).toByte()
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }
}
