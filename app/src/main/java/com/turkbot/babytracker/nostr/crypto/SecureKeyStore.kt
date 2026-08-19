package com.turkbot.babytracker.nostr.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the parent's Nostr nsec in EncryptedSharedPreferences (Android Keystore-backed).
 * The key never leaves secure storage in plaintext.
 */
class SecureKeyStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "nostr_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveKeyPair(keys: NostrKeyPair) {
        prefs.edit()
            .putString(KEY_NSEC, keys.nsec)
            .putString(KEY_NPUB, keys.npub)
            .apply()
    }

    fun getKeyPair(): NostrKeyPair? {
        val nsec = prefs.getString(KEY_NSEC, null) ?: return null
        return try {
            val priv = NostrKeys.decodeNsec(nsec)
            NostrKeys.fromPrivateKey(priv)
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasKeys(): Boolean = prefs.getString(KEY_NSEC, null) != null

    companion object {
        private const val KEY_NSEC = "nsec"
        private const val KEY_NPUB = "npub"
    }
}
