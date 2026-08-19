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

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores Nostr identity credentials in EncryptedSharedPreferences (Android Keystore-backed).
 *
 * Two modes:
 *   - LOCAL: stores the nsec (private key) — keys never leave secure storage in plaintext
 *   - AMBER: stores only the npub — the private key lives in the Amber app, never here
 *
 * The mode is persisted so NostrManager knows which signer to construct on startup.
 */
class SecureKeyStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "nostr_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── Mode ───────────────────────────────────────────

    fun getMode(): SignerMode {
        val stored = prefs.getString(KEY_MODE, null)
        return when (stored) {
            "amber" -> SignerMode.AMBER
            "local" -> SignerMode.LOCAL
            else -> SignerMode.NONE
        }
    }

    fun setMode(mode: SignerMode) {
        prefs.edit().putString(KEY_MODE, mode.name.lowercase()).apply()
    }

    // ── Local key storage ─────────────────────────────

    fun saveKeyPair(keys: NostrKeyPair) {
        prefs.edit()
            .putString(KEY_NSEC, keys.nsec)
            .putString(KEY_NPUB, keys.npub)
            .putString(KEY_MODE, "local")
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

    // ── Amber (NIP-55) ─────────────────────────────────

    fun saveAmberNpub(npub: String, signerPackage: String? = null) {
        val edit = prefs.edit()
            .putString(KEY_NPUB, npub)
            .putString(KEY_MODE, "amber")
            .remove(KEY_NSEC) // no local key in amber mode
        if (signerPackage != null) {
            edit.putString(KEY_SIGNER_PKG, signerPackage)
        }
        edit.apply()
    }

    fun getAmberNpub(): String? = prefs.getString(KEY_NPUB, null)

    fun getSignerPackage(): String? = prefs.getString(KEY_SIGNER_PKG, null)

    fun saveSignerPackage(pkg: String) {
        prefs.edit().putString(KEY_SIGNER_PKG, pkg).apply()
    }

    // ── Common ─────────────────────────────────────────

    fun getNpub(): String? = prefs.getString(KEY_NPUB, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    // ── Relay preferences ──────────────────────────────

    /**
     * Save the user's preferred relay URLs (fetched from NIP-65 or Amber).
     * Stored as a newline-delimited string.
     */
    fun saveRelays(urls: List<String>) {
        prefs.edit().putString(KEY_RELAYS, urls.joinToString("\n")).apply()
    }

    fun getRelays(): List<String>? {
        val raw = prefs.getString(KEY_RELAYS, null) ?: return null
        return raw.split("\n").filter { it.isNotBlank() }
    }

    // ── Partner sync ───────────────────────────────────

    /**
     * Save the partner's npub for shared baby data sync.
     * When set, backups are dual-encrypted: once for self, once for partner.
     */
    fun savePartnerNpub(npub: String?) {
        val edit = prefs.edit()
        if (npub.isNullOrBlank()) {
            edit.remove(KEY_PARTNER_NPUB)
        } else {
            edit.putString(KEY_PARTNER_NPUB, npub)
        }
        edit.apply()
    }

    fun getPartnerNpub(): String? = prefs.getString(KEY_PARTNER_NPUB, null)

    // ── Reminder interval ──────────────────────────────

    fun saveReminderInterval(minutes: Int) {
        prefs.edit().putInt(KEY_REMINDER_INTERVAL, minutes).apply()
    }

    fun getReminderInterval(): Int = prefs.getInt(KEY_REMINDER_INTERVAL, 0)

    companion object {
        private const val KEY_NSEC = "nsec"
        private const val KEY_NPUB = "npub"
        private const val KEY_MODE = "signer_mode"
        private const val KEY_SIGNER_PKG = "signer_package"
        private const val KEY_RELAYS = "relay_urls"
        private const val KEY_PARTNER_NPUB = "partner_npub"
        private const val KEY_REMINDER_INTERVAL = "reminder_interval_min"
    }
}

enum class SignerMode { NONE, LOCAL, AMBER }
