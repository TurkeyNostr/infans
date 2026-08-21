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
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * NIP-44 v2 encryption / decryption.
 *
 * This is the modern Nostr encryption standard used by both Runstr (for encrypted backup)
 * and nospeak (for gift-wrapped DMs).
 *
 * Flow:
 *   1. ECDH: compute shared point = privkey_a * pubkey_b, take x-coordinate (32 bytes)
 *   2. HKDF: extract+expand to 32-byte encryption key
 *   3. AES-256-GCM: encrypt padded plaintext with random 32-byte nonce
 *   4. Encode: version(2) + nonce(32) + ciphertext+tag → base64
 *
 * For self-encryption (backup), privkey_a == privkey_b (encrypt to yourself).
 *
 * NOTE: secp256k1-kmp's ecdh() returns SHA256(compressed_point), but NIP-44 requires
 * the raw x-coordinate of the shared point. We use BouncyCastle directly for ECDH.
 */
object Nip44 {

    private val random = SecureRandom()

    // secp256k1 curve parameters (BouncyCastle)
    private val curveParams: X9ECParameters = CustomNamedCurves.getByName("secp256k1")
    private val domain = ECDomainParameters(curveParams.curve, curveParams.g, curveParams.n, curveParams.h)

    private const val VERSION: Byte = 2
    private const val NONCE_LEN = 32
    private const val TAG_BITS = 128 // GCM auth tag

    /**
     * Compute the ECDH shared secret (x-coordinate of the shared point).
     * shared_point = privkey * pubkey
     * conversation_key = shared_point.x (32 bytes)
     */
    private fun computeEcdh(privKey: ByteArray, pubKey: ByteArray): ByteArray {
        // Reconstruct the EC point from x-only public key.
        // Prepend 0x02 (even y) to create a compressed point — BouncyCastle will decode it.
        // The ECDH shared secret x-coordinate is the same regardless of y parity,
        // since (priv * (x,y)) and (priv * (x,-y)) have the same x-coordinate.
        val compressed = ByteArray(33)
        compressed[0] = 0x02
        System.arraycopy(pubKey, 0, compressed, 1, 32)
        val point = curveParams.curve.decodePoint(compressed)

        // Multiply: shared_point = privkey * pubkey_point
        val privBigInt = BigInteger(1, privKey)
        val sharedPoint = point.multiply(privBigInt).normalize()

        // Extract x-coordinate (32 bytes)
        return sharedPoint.affineXCoord.encoded
    }

    /**
     * Encrypt plaintext for a recipient's public key.
     */
    fun encrypt(plaintext: String, senderPrivKey: ByteArray, recipientPubKey: ByteArray): String {
        // 1. ECDH shared secret
        val conversationKey = computeEcdh(senderPrivKey, recipientPubKey)

        // 2. HKDF to derive encryption key
        val encKey = hkdfExtractExpand(conversationKey)

        // 3. Generate random nonce
        val nonce = ByteArray(NONCE_LEN)
        random.nextBytes(nonce)

        // 4. Pad plaintext
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val padded = pad(plaintextBytes)

        // 5. AES-256-GCM encrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(padded)

        // 6. Assemble: version + nonce + ciphertext
        val payload = ByteArray(1 + NONCE_LEN + ciphertext.size)
        payload[0] = VERSION
        System.arraycopy(nonce, 0, payload, 1, NONCE_LEN)
        System.arraycopy(ciphertext, 0, payload, 1 + NONCE_LEN, ciphertext.size)

        return android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)
    }

    /**
     * Decrypt a base64-encoded NIP-44 v2 payload.
     */
    fun decrypt(payload: String, recipientPrivKey: ByteArray, senderPubKey: ByteArray): String {
        val bytes = android.util.Base64.decode(payload, android.util.Base64.NO_WRAP)
        require(bytes.size > 1 + NONCE_LEN) { "Payload too short" }
        require(bytes[0] == VERSION) { "Unsupported NIP-44 version: ${bytes[0]}" }

        // 1. ECDH shared secret
        val conversationKey = computeEcdh(recipientPrivKey, senderPubKey)

        // 2. HKDF
        val encKey = hkdfExtractExpand(conversationKey)

        // 3. Extract nonce + ciphertext
        val nonce = bytes.copyOfRange(1, 1 + NONCE_LEN)
        val ciphertext = bytes.copyOfRange(1 + NONCE_LEN, bytes.size)

        // 4. AES-256-GCM decrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val padded = cipher.doFinal(ciphertext)

        // 5. Unpad
        val plaintextBytes = unpad(padded)

        return String(plaintextBytes, Charsets.UTF_8)
    }

    fun selfEncrypt(plaintext: String, privKey: ByteArray, pubKey: ByteArray): String =
        encrypt(plaintext, privKey, pubKey)

    fun selfDecrypt(payload: String, privKey: ByteArray, pubKey: ByteArray): String =
        decrypt(payload, privKey, pubKey)

    // ─── HKDF (RFC 5869) ──────────────────────────────────────

    /**
     * HKDF extract+expand to produce a 32-byte encryption key.
     * Per NIP-44 v2: salt = 32 zero bytes, info = "nip44-v2"
     */
    private fun hkdfExtractExpand(ikm: ByteArray): ByteArray {
        val salt = ByteArray(32) // all zeros
        val info = "nip44-v2".toByteArray(Charsets.UTF_8)

        // Extract
        val prk = hmacSha256(salt, ikm)

        // Expand to 32 bytes (we only need one key)
        val t1 = hmacSha256(prk, info + byteArrayOf(1))
        return t1.copyOfRange(0, 32)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    // ─── Padding (NIP-44 v2) ──────────────────────────────────

    /**
     * NIP-44 v2 padding: pad to next power of 2, minimum 32 bytes.
     * The padding format includes a 2-byte length prefix (big-endian) of the original size.
     */
    private fun pad(plaintext: ByteArray): ByteArray {
        val size = plaintext.size
        // Calculate padded size: next power of 2, minimum 32
        val padSize: Int = when {
            size <= 32 -> 32
            else -> {
                var next = 32
                while (next < size) next *= 2
                next
            }
        }

        // Format: [2-byte big-endian original length] + [plaintext] + [zero padding]
        val result = ByteArray(2 + padSize)
        result[0] = ((size shr 8) and 0xff).toByte()
        result[1] = (size and 0xff).toByte()
        System.arraycopy(plaintext, 0, result, 2, size)
        return result
    }

    private fun unpad(padded: ByteArray): ByteArray {
        // Read 2-byte big-endian length prefix
        val size = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
        require(size >= 0 && size <= padded.size - 2) { "Invalid padded length" }
        return padded.copyOfRange(2, 2 + size)
    }
}
