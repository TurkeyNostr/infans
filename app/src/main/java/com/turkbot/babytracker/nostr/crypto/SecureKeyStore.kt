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

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores Nostr identity credentials.
 *
 * Storage layout (replaces EncryptedSharedPreferences, deprecated by Google):
 *   - Non-secret values (mode, npub, relays, cursors) live in a plain
 *     SharedPreferences file — they were never secrets to begin with.
 *   - The nsec (LOCAL mode private key) is the only secret: it is encrypted
 *     with [KeystoreCrypto] (hardware-backed AndroidKeyStore AES-GCM) and
 *     stored as a ciphertext blob.
 *
 * Migration: on first launch after upgrading, values are copied out of the
 * legacy EncryptedSharedPreferences ("nostr_keys") — the nsec is re-encrypted
 * into the new store, then the legacy file is deleted. The legacy read is the
 * ONLY remaining use of androidx.security.crypto in the app; once devices
 * have upgraded, the library and this migration path can be removed together.
 *
 * Two modes:
 *   - LOCAL: stores the nsec (private key) — keys never leave secure storage in plaintext
 *   - AMBER: stores only the npub — the private key lives in the Amber app, never here
 *
 * The mode is persisted so NostrManager knows which signer to construct on startup.
 */
class SecureKeyStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        migrateLegacyIfNeeded(context)
    }

    /**
     * One-time upgrade from the deprecated EncryptedSharedPreferences store.
     * Idempotent: guarded by a flag in the new store. If the legacy store is
     * unreadable (lost Keystore master key / corrupted), the flag is still set
     * and the user simply re-runs onboarding — we never crash the app over it.
     */
    private fun migrateLegacyIfNeeded(context: Context) {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        try {
            val legacy = androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                LEGACY_PREF_FILE,
                androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // Secrets get re-encrypted with the new Keystore path; everything
            // else is copied as-is (it was never secret).
            legacy.getString(KEY_NSEC, null)?.let { nsec ->
                prefs.edit().putString(KEY_NSEC, KeystoreCrypto.encrypt(nsec)).apply()
            }
            for (key in PLAINTEXT_KEYS) {
                val all = legacy.all
                when (val value = all[key]) {
                    is String -> prefs.edit().putString(key, value).apply()
                    is Long -> prefs.edit().putLong(key, value).apply()
                    is Int -> prefs.edit().putInt(key, value).apply()
                }
            }

            context.deleteSharedPreferences(LEGACY_PREF_FILE)
        } catch (_: Exception) {
            // Legacy store unreadable — fall through and mark migrated so we
            // never retry (and never crash) on every launch.
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

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
            .putString(KEY_NSEC, KeystoreCrypto.encrypt(keys.nsec))
            .putString(KEY_NPUB, keys.npub)
            .putString(KEY_MODE, "local")
            .apply()
    }

    fun getKeyPair(): NostrKeyPair? {
        val blob = prefs.getString(KEY_NSEC, null) ?: return null
        return try {
            val nsec = KeystoreCrypto.decryptToString(blob)
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

    // ── Processed sync events ──────────────────────────

    /**
     * Track which kind-30078 event IDs we've already decrypted and merged.
     * Without this, every app launch re-decrypts the same backup/partner-sync
     * events on every relay — with Amber that means a permission prompt each
     * time. Capped at the most recent 200 IDs to bound storage.
     */
    fun isEventProcessed(eventId: String): Boolean {
        val raw = prefs.getString(KEY_PROCESSED_EVENTS, "") ?: ""
        return raw.split(",").contains(eventId)
    }

    fun markEventProcessed(eventId: String) {
        val raw = prefs.getString(KEY_PROCESSED_EVENTS, "") ?: ""
        val ids = raw.split(",").filter { it.isNotBlank() }.toMutableList()
        if (ids.contains(eventId)) return
        ids.add(eventId)
        // Keep only the most recent 200
        val trimmed = if (ids.size > 200) ids.takeLast(200) else ids
        prefs.edit().putString(KEY_PROCESSED_EVENTS, trimmed.joinToString(",")).apply()
    }

    /** Clear the processed-events cache and last-seen timestamps (used after deleting relay data). */
    fun clearProcessedEvents() {
        prefs.edit()
            .remove(KEY_PROCESSED_EVENTS)
            .remove(KEY_LAST_BACKUP_TIME)
            .remove(KEY_LAST_PARTNER_SYNC_TIME)
            .apply()
    }

    // ── Last-seen timestamps (avoid re-fetching history) ───
    /** Unix seconds of the most recent backup/sync event we've processed. */
    fun getLastBackupTime(): Long = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
    fun saveLastBackupTime(seconds: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, seconds).apply()
    }

    /**
     * Separate cursor for partner-sync events — must NOT share with
     * lastBackupTime. Self-backup events advance lastBackupTime, which
     * would cause the partner-sync since-filter to skip partner events
     * published before our own self-backup.
     */
    fun getLastPartnerSyncTime(): Long = prefs.getLong(KEY_LAST_PARTNER_SYNC_TIME, 0L)
    fun saveLastPartnerSyncTime(seconds: Long) {
        prefs.edit().putLong(KEY_LAST_PARTNER_SYNC_TIME, seconds).apply()
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
            edit.remove(KEY_PARTNER_NIP05) // clear cached nip05 too
        } else {
            edit.putString(KEY_PARTNER_NPUB, npub)
        }
        edit.apply()
    }

    fun getPartnerNpub(): String? = prefs.getString(KEY_PARTNER_NPUB, null)

    /**
     * Cache the partner's NIP-05 identifier (resolved from their kind 0 metadata).
     * Null clears it. Cleared automatically when partner npub is cleared.
     */
    fun savePartnerNip05(nip05: String?) {
        prefs.edit().putString(KEY_PARTNER_NIP05, nip05).apply()
    }

    fun getPartnerNip05(): String? = prefs.getString(KEY_PARTNER_NIP05, null)

    // ── Reminder interval ──────────────────────────────

    fun saveReminderInterval(minutes: Int) {
        prefs.edit().putInt(KEY_REMINDER_INTERVAL, minutes).apply()
    }

    fun getReminderInterval(): Int = prefs.getInt(KEY_REMINDER_INTERVAL, 0)

    companion object {
        private const val PREF_FILE = "nostr_prefs"
        private const val LEGACY_PREF_FILE = "nostr_keys"
        private const val KEY_MIGRATED = "migrated_from_legacy_keystore"

        private const val KEY_NSEC = "nsec" // stored as KeystoreCrypto ciphertext blob
        private const val KEY_NPUB = "npub"
        private const val KEY_MODE = "signer_mode"
        private const val KEY_SIGNER_PKG = "signer_package"
        private const val KEY_RELAYS = "relay_urls"
        private const val KEY_PROCESSED_EVENTS = "processed_sync_events"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val KEY_LAST_PARTNER_SYNC_TIME = "last_partner_sync_time"
        private const val KEY_PARTNER_NPUB = "partner_npub"
        private const val KEY_PARTNER_NIP05 = "partner_nip05"
        private const val KEY_REMINDER_INTERVAL = "reminder_interval_min"

        /** Legacy keys migrated verbatim (non-secret). */
        private val PLAINTEXT_KEYS = listOf(
            KEY_NPUB, KEY_MODE, KEY_SIGNER_PKG, KEY_RELAYS, KEY_PROCESSED_EVENTS,
            KEY_LAST_BACKUP_TIME, KEY_LAST_PARTNER_SYNC_TIME, KEY_PARTNER_NPUB,
            KEY_PARTNER_NIP05, KEY_REMINDER_INTERVAL
        )
    }
}

enum class SignerMode { NONE, LOCAL, AMBER }
