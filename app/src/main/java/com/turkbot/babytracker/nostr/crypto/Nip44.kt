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
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * NIP-44 v2 encryption / decryption — spec-compliant implementation.
 *
 * Spec: https://github.com/nostr-protocol/nips/blob/master/44.md
 *
 * Cryptographic scheme (version 0x02):
 *   1. ECDH: shared_x = x-coordinate of (privkey_a * pubkey_b), unhashed, 32 bytes
 *   2. HKDF-extract: conversation_key = HMAC-SHA256(salt="nip44-v2", IKM=shared_x)
 *   3. Per-message HKDF-expand: 76 bytes from (PRK=conversation_key, info=nonce)
 *      → chacha_key[0:32], chacha_nonce[32:44], hmac_key[44:76]
 *   4. Pad: chunk-based power-of-2 padding with 2-byte (or 6-byte for >=64KB) length prefix
 *   5. Encrypt: ChaCha20 (RFC 8439, counter=0) with chacha_key + chacha_nonce
 *   6. MAC: HMAC-SHA256(hmac_key, concat(nonce, ciphertext))
 *   7. Encode: base64(version(2) + nonce(32) + ciphertext + mac(32))
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
    private const val MAC_LEN = 32
    private const val MIN_PAYLOAD_LEN = 99  // version(1) + nonce(32) + min_ciphertext(34) + mac(32)
    private const val MIN_PLAINTEXT_SIZE = 1
    private const val MAX_PLAINTEXT_SIZE = 4294967295
    private const val EXTENDED_PREFIX_THRESHOLD = 65536

    /**
     * Per-message derived keys from HKDF-expand.
     * - chachaKey:    32 bytes — ChaCha20 encryption key
     * - chachaNonce:  12 bytes — ChaCha20 nonce (RFC 8439, 96-bit)
     * - hmacKey:      32 bytes — HMAC-SHA256 key for MAC
     */
    data class MessageKeys(
        val chachaKey: ByteArray,
        val chachaNonce: ByteArray,
        val hmacKey: ByteArray
    )

    // ─── Public API ───────────────────────────────────────────

    /**
     * Encrypt plaintext for a recipient's public key.
     * Returns base64-encoded NIP-44 v2 payload.
     */
    fun encrypt(plaintext: String, senderPrivKey: ByteArray, recipientPubKey: ByteArray): String {
        val conversationKey = getConversationKey(senderPrivKey, recipientPubKey)
        val nonce = ByteArray(NONCE_LEN)
        random.nextBytes(nonce)
        return encryptWithNonce(plaintext, conversationKey, nonce)
    }

    /**
     * Decrypt a base64-encoded NIP-44 v2 payload.
     * Verifies MAC (constant-time) before decrypting.
     */
    fun decrypt(payload: String, recipientPrivKey: ByteArray, senderPubKey: ByteArray): String {
        val conversationKey = getConversationKey(recipientPrivKey, senderPubKey)
        return decryptWithKey(payload, conversationKey)
    }

    fun selfEncrypt(plaintext: String, privKey: ByteArray, pubKey: ByteArray): String =
        encrypt(plaintext, privKey, pubKey)

    fun selfDecrypt(payload: String, privKey: ByteArray, pubKey: ByteArray): String =
        decrypt(payload, privKey, pubKey)

    // ─── Internal (visible to unit tests) ─────────────────────

    /**
     * Compute the long-term conversation key between two users.
     * conversation_key = HKDF-extract(IKM=shared_x, salt=utf8("nip44-v2"))
     * Symmetric: getConversationKey(a, B) == getConversationKey(b, A)
     */
    internal fun getConversationKey(privKey: ByteArray, pubKey: ByteArray): ByteArray {
        val sharedX = computeEcdh(privKey, pubKey)
        return hkdfExtract(sharedX, "nip44-v2".toByteArray(Charsets.UTF_8))
    }

    /**
     * Encrypt with a pre-computed conversation key and explicit nonce.
     * Used by [encrypt] with a random nonce; exposed for test-vector verification.
     */
    internal fun encryptWithNonce(plaintext: String, conversationKey: ByteArray, nonce: ByteArray): String {
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        require(plaintextBytes.size in MIN_PLAINTEXT_SIZE..MAX_PLAINTEXT_SIZE) {
            "invalid plaintext length"
        }
        require(conversationKey.size == 32) { "invalid conversation_key length" }
        require(nonce.size == NONCE_LEN) { "invalid nonce length" }

        val keys = getMessageKeys(conversationKey, nonce)
        val padded = pad(plaintextBytes)
        val ciphertext = chacha20(keys.chachaKey, keys.chachaNonce, padded)
        val mac = hmacSha256(keys.hmacKey, nonce + ciphertext)

        // payload = version(1) + nonce(32) + ciphertext + mac(32)
        val payload = ByteArray(1 + NONCE_LEN + ciphertext.size + MAC_LEN)
        payload[0] = VERSION
        System.arraycopy(nonce, 0, payload, 1, NONCE_LEN)
        System.arraycopy(ciphertext, 0, payload, 1 + NONCE_LEN, ciphertext.size)
        System.arraycopy(mac, 0, payload, 1 + NONCE_LEN + ciphertext.size, MAC_LEN)

        return Base64.getEncoder().encodeToString(payload)
    }

    /**
     * Decrypt with a pre-computed conversation key.
     * Verifies MAC (constant-time) before decrypting.
     */
    internal fun decryptWithKey(payload: String, conversationKey: ByteArray): String {
        require(conversationKey.size == 32) { "invalid conversation_key length" }

        // 1. Check for non-base64 future-proof flag
        if (payload.isEmpty() || payload[0] == '#') {
            throw IllegalArgumentException("unknown version")
        }

        // 2. Validate minimum base64 length (prevents DoS on decoder)
        require(payload.length >= 132) { "invalid payload size" }

        // 3. Decode base64
        val data = Base64.getDecoder().decode(payload)
        require(data.size >= MIN_PAYLOAD_LEN) { "invalid data size" }

        // 4. Check version
        val version = data[0]
        require(version == VERSION) { "unknown version $version" }

        // 5. Extract fields: nonce(32) + ciphertext + mac(32)
        val nonce = data.copyOfRange(1, 1 + NONCE_LEN)
        val ciphertext = data.copyOfRange(1 + NONCE_LEN, data.size - MAC_LEN)
        val mac = data.copyOfRange(data.size - MAC_LEN, data.size)

        // 6. Derive keys and verify MAC (constant-time comparison)
        val keys = getMessageKeys(conversationKey, nonce)
        val expectedMac = hmacSha256(keys.hmacKey, nonce + ciphertext)
        if (!MessageDigest.isEqual(expectedMac, mac)) {
            throw IllegalArgumentException("invalid MAC")
        }

        // 7. Decrypt and unpad
        val padded = chacha20(keys.chachaKey, keys.chachaNonce, ciphertext)
        return String(unpad(padded), Charsets.UTF_8)
    }

    /**
     * Derive per-message keys from conversation key + nonce.
     * HKDF-expand(PRK=conversation_key, info=nonce, L=76) → 76 bytes
     *   chacha_key   = bytes[0:32]
     *   chacha_nonce = bytes[32:44]
     *   hmac_key     = bytes[44:76]
     */
    internal fun getMessageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        require(conversationKey.size == 32) { "invalid conversation_key length" }
        require(nonce.size == NONCE_LEN) { "invalid nonce length" }

        val okm = hkdfExpand(conversationKey, nonce, 76)
        return MessageKeys(
            chachaKey = okm.copyOfRange(0, 32),
            chachaNonce = okm.copyOfRange(32, 44),
            hmacKey = okm.copyOfRange(44, 76)
        )
    }

    /**
     * NIP-44 v2 padded length calculation.
     *
     * Chunks: 32 bytes for small messages (next_power <= 256),
     * next_power/8 for larger ones. Minimum padded size is 32.
     */
    internal fun calcPaddedLen(unpaddedLen: Int): Int {
        if (unpaddedLen <= 32) return 32
        // next power of 2 >= unpaddedLen
        var nextPower = 32
        while (nextPower < unpaddedLen) nextPower *= 2
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((unpaddedLen - 1) / chunk + 1)
    }

    // ─── Private: ECDH ────────────────────────────────────────

    /**
     * Compute the ECDH shared secret (x-coordinate of the shared point).
     * shared_point = privkey * pubkey → take x-coordinate (32 bytes, unhashed)
     *
     * We reconstruct the EC point from the x-only public key by prepending
     * 0x02 (even y). The x-coordinate of the shared point is the same
     * regardless of y parity: (k * (x,y)) and (k * (x,-y)) share the same x.
     */
    private fun computeEcdh(privKey: ByteArray, pubKey: ByteArray): ByteArray {
        val compressed = ByteArray(33)
        compressed[0] = 0x02
        System.arraycopy(pubKey, 0, compressed, 1, 32)
        val point = curveParams.curve.decodePoint(compressed)

        val privBigInt = BigInteger(1, privKey)
        val sharedPoint = point.multiply(privBigInt).normalize()

        return sharedPoint.affineXCoord.encoded
    }

    // ─── Private: ChaCha20 (RFC 8439) ─────────────────────────

    /**
     * ChaCha20 stream cipher (RFC 8439, counter starts at 0).
     * Symmetric: same function for encrypt and decrypt (XOR with keystream).
     */
    private fun chacha20(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray {
        val engine = ChaCha7539Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        val output = ByteArray(data.size)
        engine.processBytes(data, 0, data.size, output, 0)
        return output
    }

    // ─── Private: HMAC-SHA256 ─────────────────────────────────

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    // ─── Private: HKDF (RFC 5869) ─────────────────────────────

    /**
     * HKDF-extract: PRK = HMAC-SHA256(salt, IKM)
     */
    private fun hkdfExtract(ikm: ByteArray, salt: ByteArray): ByteArray {
        return hmacSha256(salt, ikm)
    }

    /**
     * HKDF-expand: OKM = T(1) | T(2) | ... | T(N), truncated to L bytes
     * where T(i) = HMAC-SHA256(PRK, T(i-1) | info | i)
     */
    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hashLen = 32
        val n = (length + hashLen - 1) / hashLen
        var t = ByteArray(0)
        val okm = ByteArray(n * hashLen)
        for (i in 1..n) {
            t = hmacSha256(prk, t + info + byteArrayOf(i.toByte()))
            System.arraycopy(t, 0, okm, (i - 1) * hashLen, hashLen)
        }
        return okm.copyOfRange(0, length)
    }

    // ─── Private: Padding (NIP-44 v2) ─────────────────────────

    /**
     * NIP-44 v2 padding:
     * - 2-byte u16 big-endian length prefix (for plaintext < 65536)
     * - 6-byte [0x00, 0x00, u32 big-endian] prefix (for plaintext >= 65536)
     * - Padded to calcPaddedLen() with trailing zeros
     */
    internal fun pad(plaintext: ByteArray): ByteArray {
        val unpaddedLen = plaintext.size
        require(unpaddedLen in MIN_PLAINTEXT_SIZE..MAX_PLAINTEXT_SIZE) {
            "invalid plaintext length"
        }

        val prefix: ByteArray = if (unpaddedLen >= EXTENDED_PREFIX_THRESHOLD) {
            // 6-byte extended: [0x00, 0x00] + u32 big-endian
            byteArrayOf(0, 0,
                ((unpaddedLen shr 24) and 0xff).toByte(),
                ((unpaddedLen shr 16) and 0xff).toByte(),
                ((unpaddedLen shr 8) and 0xff).toByte(),
                (unpaddedLen and 0xff).toByte()
            )
        } else {
            // 2-byte u16 big-endian
            byteArrayOf(
                ((unpaddedLen shr 8) and 0xff).toByte(),
                (unpaddedLen and 0xff).toByte()
            )
        }

        val paddedLen = calcPaddedLen(unpaddedLen)
        val suffix = ByteArray(paddedLen - unpaddedLen)
        return prefix + plaintext + suffix
    }

    /**
     * Remove NIP-44 v2 padding and validate structure.
     * Throws on invalid padding (wrong length, zero plaintext, size mismatch).
     */
    internal fun unpad(padded: ByteArray): ByteArray {
        require(padded.size >= 2) { "invalid padded data" }

        val firstTwo = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)

        val (prefixLen, unpaddedLen) = if (firstTwo == 0) {
            // Extended 6-byte prefix
            require(padded.size >= 6) { "invalid padded data" }
            val len = ((padded[2].toInt() and 0xff) shl 24) or
                      ((padded[3].toInt() and 0xff) shl 16) or
                      ((padded[4].toInt() and 0xff) shl 8) or
                      (padded[5].toInt() and 0xff)
            require(len >= EXTENDED_PREFIX_THRESHOLD) { "invalid padding" }
            6 to len
        } else {
            2 to firstTwo
        }

        require(unpaddedLen > 0) { "invalid padding" }
        require(padded.size == prefixLen + calcPaddedLen(unpaddedLen)) { "invalid padding" }

        return padded.copyOfRange(prefixLen, prefixLen + unpaddedLen)
    }
}
