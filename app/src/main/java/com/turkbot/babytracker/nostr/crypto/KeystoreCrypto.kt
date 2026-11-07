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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Direct AndroidKeyStore-backed AES-256-GCM encryption.
 *
 * Replaces androidx.security.crypto (EncryptedSharedPreferences), which Google
 * deprecated and which is known to break after device restores. A single AES-256
 * key is generated inside the TEE-backed AndroidKeyStore and never leaves it;
 * ciphertext blobs are stored on disk as Base64(IV || ciphertext || GCM tag).
 *
 * Nothing here touches the network and no third-party code is involved.
 */
object KeystoreCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "nostr_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Encrypt to a Base64(IV || ciphertext+tag) string. Fresh IV per call. */
    fun encrypt(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected GCM IV length: ${iv.size}" }
        val ciphertext = cipher.doFinal(plaintext)
        val out = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(out)
        ciphertext.copyInto(out, iv.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun encrypt(plaintext: String): String = encrypt(plaintext.toByteArray(Charsets.UTF_8))

    /** Decrypt a Base64(IV || ciphertext+tag) string produced by [encrypt]. */
    fun decrypt(encoded: String): ByteArray {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        require(data.size > IV_BYTES) { "ciphertext too short" }
        val iv = data.copyOfRange(0, IV_BYTES)
        val ciphertext = data.copyOfRange(IV_BYTES, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun decryptToString(encoded: String): String = String(decrypt(encoded), Charsets.UTF_8)
}
