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

package com.turkbot.babytracker.nostr

import android.content.Context
import android.util.Log
import com.turkbot.babytracker.data.entities.ChatMessage
import com.turkbot.babytracker.data.repo.BabyRepository
import com.turkbot.babytracker.data.repo.BackupPayload
import com.turkbot.babytracker.nostr.amber.AmberSigner
import com.turkbot.babytracker.nostr.crypto.LocalSigner
import com.turkbot.babytracker.nostr.crypto.NostrKeyPair
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import com.turkbot.babytracker.nostr.crypto.NostrSigner
import com.turkbot.babytracker.nostr.crypto.SecureKeyStore
import com.turkbot.babytracker.nostr.crypto.SignerMode
import com.turkbot.babytracker.nostr.events.BackupService
import com.turkbot.babytracker.nostr.events.NostrEvent
import com.turkbot.babytracker.nostr.messaging.GiftWrapMessaging
import com.turkbot.babytracker.nostr.relay.RelayEvent
import com.turkbot.babytracker.nostr.relay.RelayPool
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Central Nostr manager — owns the relay pool, signer, backup service, and messaging.
 *
 * Two signer modes:
 *   - LOCAL: private key stored in EncryptedSharedPreferences (SecureKeyStore)
 *   - AMBER: private key held by the Amber app (NIP-55); signing/encrypt via Intents
 *
 * Both modes produce a [NostrSigner] that the rest of the app uses uniformly.
 * Handles incoming gift-wrapped DMs and encrypted backups.
 *
 * Relay management:
 *   - Default relays are used until the user's NIP-65 (kind 10002) relay list is fetched.
 *   - After login (Amber or local), the user's preferred relays are fetched and the pool
 *     is reconfigured. Amber may also return relays directly in its GET_PUBKEY response.
 *   - Preferred relays are persisted in SecureKeyStore for next startup.
 */
class NostrManager(context: Context) {

    private val appContext = context.applicationContext
    private val keyStore = SecureKeyStore(appContext)
    private val repo = BabyRepository(appContext)

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSocket stays open
        .build()

    // Default relays — used as fallback before NIP-65 / Amber relays are fetched
    private val defaultRelays = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.primal.net"
    )

    // Effective relays — loaded from storage or defaults
    private val effectiveRelays: List<String> = keyStore.getRelays() ?: defaultRelays

    val relayPool = RelayPool(effectiveRelays, httpClient)
    private val backupService = BackupService(appContext, relayPool)
    private val messaging = GiftWrapMessaging(relayPool)

    private val _signer = MutableStateFlow<NostrSigner?>(null)
    val signer: StateFlow<NostrSigner?> = _signer

    private val _relayConnected = MutableStateFlow(false)
    val relayConnected: StateFlow<Boolean> = _relayConnected

    /** Current relay URLs the pool is connected to (for display in Settings) */
    val currentRelays: StateFlow<List<String>> = MutableStateFlow(effectiveRelays)

    private val _partnerNpub = MutableStateFlow<String?>(keyStore.getPartnerNpub())
    val partnerNpub: StateFlow<String?> = _partnerNpub

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "NostrManager"
        const val SUB_DMS = "baby_dm_sub"
        const val SUB_BACKUP = "baby_backup_sub"
        const val SUB_NIP65 = "nip65_relay_sub"
        const val SUB_PARTNER_SYNC = "partner_sync_sub"
    }

    /**
     * Initialize: load stored signer (local or amber), connect to relays, subscribe.
     * If saved relay preferences exist, they are used; otherwise defaults.
     */
    suspend fun initialize() {
        when (keyStore.getMode()) {
            SignerMode.LOCAL -> {
                val stored = keyStore.getKeyPair()
                if (stored != null) {
                    val signer = LocalSigner(stored)
                    _signer.value = signer
                    connectAndSubscribe(signer)
                    // Refresh NIP-65 relays in background (user may have changed them)
                    scope.launch { fetchAndApplyNip65Relays(signer.pubkeyHex) }
                }
            }
            SignerMode.AMBER -> {
                val npub = keyStore.getAmberNpub()
                if (npub != null) {
                    val pubHex = npubToHex(npub)
                    if (pubHex != null) {
                        val signer = AmberSigner(npub, pubHex)
                        _signer.value = signer
                        connectAndSubscribe(signer)
                        // Refresh NIP-65 relays in background
                        scope.launch { fetchAndApplyNip65Relays(signer.pubkeyHex) }
                    }
                }
            }
            SignerMode.NONE -> { /* no identity yet */ }
        }
    }

    /**
     * Generate a new local Nostr identity for this parent.
     */
    suspend fun generateIdentity(): NostrKeyPair {
        val newKeys = NostrKeys.generate()
        keyStore.saveKeyPair(newKeys)
        val signer = LocalSigner(newKeys)
        _signer.value = signer
        connectAndSubscribe(signer)
        scope.launch { fetchAndApplyNip65Relays(signer.pubkeyHex) }
        return newKeys
    }

    /**
     * Import an existing identity via nsec (local key).
     */
    suspend fun importIdentity(nsec: String): NostrKeyPair? {
        return try {
            val priv = NostrKeys.decodeNsec(nsec)
            val keys = NostrKeys.fromPrivateKey(priv)
            keyStore.saveKeyPair(keys)
            val signer = LocalSigner(keys)
            _signer.value = signer
            connectAndSubscribe(signer)
            scope.launch { fetchAndApplyNip65Relays(signer.pubkeyHex) }
            keys
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import nsec", e)
            null
        }
    }

    /**
     * Log in with Amber (NIP-55). Requests the pubkey from Amber; the private key
     * never enters this app. Returns the npub on success, null on failure.
     *
     * If Amber returns relay preferences, they are applied immediately.
     * NIP-65 relays are also fetched as a fallback/supplement.
     */
    suspend fun loginWithAmber(): String? {
        return try {
            val loginResult = AmberSigner.requestPubkey()
            if (loginResult == null || loginResult.npub.isBlank()) {
                Log.e(TAG, "Amber returned empty npub")
                return null
            }
            val pubHex = npubToHex(loginResult.npub)
            if (pubHex == null) {
                Log.e(TAG, "Invalid npub from Amber: ${loginResult.npub}")
                return null
            }
            keyStore.saveAmberNpub(loginResult.npub)
            val signer = AmberSigner(loginResult.npub, pubHex)
            _signer.value = signer
            connectAndSubscribe(signer)

            // Apply Amber's relay preferences if provided
            if (!loginResult.relays.isNullOrEmpty()) {
                applyRelays(loginResult.relays)
            }
            // Also fetch NIP-65 in background (may override or supplement)
            scope.launch { fetchAndApplyNip65Relays(pubHex) }

            loginResult.npub
        } catch (e: Exception) {
            Log.e(TAG, "Amber login failed", e)
            null
        }
    }

    /**
     * Check if Amber is installed on the device.
     */
    fun isAmberInstalled(): Boolean {
        return try {
            appContext.packageManager.getPackageInfo("com.greenart7c3.amber", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all identity data (logout).
     */
    fun clearIdentity() {
        keyStore.clear()
        _signer.value = null
        _partnerNpub.value = null
    }

    /**
     * Set or clear the partner's npub for shared baby data sync.
     * When set, backups are dual-published: self-encrypted + partner-encrypted.
     * The partner's app will receive and merge the data automatically.
     */
    fun setPartnerNpub(npub: String?) {
        val trimmed = npub?.trim()?.takeIf { it.startsWith("npub1") }
        keyStore.savePartnerNpub(trimmed)
        _partnerNpub.value = trimmed
        if (trimmed != null) {
            Log.d(TAG, "Partner npub set: ${trimmed.take(20)}...")
        }
    }

    private fun npubToHex(npub: String): String? {
        return try {
            val pub = NostrKeys.decodeNpub(npub)
            NostrKeys.toHex(pub)
        } catch (e: Exception) {
            null
        }
    }

    private fun connectAndSubscribe(signer: NostrSigner) {
        relayPool.connect()

        // Subscribe to gift-wrapped DMs (kind 1059) addressed to us
        val myPubkeyHex = signer.pubkeyHex
        val dmFilter = """{"kinds":[1059],"#p":["$myPubkeyHex"],"limit":100}"""
        relayPool.subscribe(SUB_DMS, dmFilter)

        // Subscribe to our own encrypted backups (kind 30078)
        val backupFilter = """{"kinds":[30078],"authors":["$myPubkeyHex"],"#d":["${BackupService.BACKUP_D_TAG}"],"limit":1}"""
        relayPool.subscribe(SUB_BACKUP, backupFilter)

        // Subscribe to partner sync events (kind 30078 where #p = our pubkey)
        val partnerSyncFilter = """{"kinds":[30078],"#p":["$myPubkeyHex"],"#d":["${BackupService.PARTNER_SYNC_D_TAG}"],"limit":1}"""
        relayPool.subscribe(SUB_PARTNER_SYNC, partnerSyncFilter)

        // Listen for incoming events
        scope.launch {
            relayPool.events.collect { wrapper ->
                when (wrapper.event.kind) {
                    GiftWrapMessaging.KIND_GIFT_WRAP -> {
                        if (wrapper.subscriptionId == SUB_DMS) {
                            handleIncomingDM(wrapper.event, signer)
                        }
                    }
                    BackupService.BACKUP_KIND -> {
                        when (wrapper.subscriptionId) {
                            SUB_BACKUP -> handleBackupEvent(wrapper.event, signer)
                            SUB_PARTNER_SYNC -> handlePartnerSyncEvent(wrapper.event, signer)
                        }
                    }
                }
            }
        }
    }

    /**
     * Fetch the user's NIP-65 relay list (kind 10002) and reconfigure the pool.
     * Runs in background — does nothing if no relay list is found.
     */
    private suspend fun fetchAndApplyNip65Relays(pubkeyHex: String) {
        val relays = fetchNip65Relays(pubkeyHex)
        if (relays.isNotEmpty()) {
            Log.d(TAG, "NIP-65: found ${relays.size} relays for user")
            applyRelays(relays)
        } else {
            Log.d(TAG, "NIP-65: no relay list found, keeping current relays")
        }
    }

    /**
     * Query default relays for a kind 10002 event from the given pubkey.
     * Parses "r" tags for relay URLs. Returns empty list if not found.
     */
    private suspend fun fetchNip65Relays(pubkeyHex: String): List<String> {
        return withTimeoutOrNull(10_000) {
            val filter = """{"kinds":[10002],"authors":["$pubkeyHex"],"limit":1}"""
            val deferred = CompletableDeferred<List<String>>()

            // Use a one-shot subscription on the existing pool
            val subId = SUB_NIP65
            val job = scope.launch {
                relayPool.events.collect { wrapper ->
                    if (wrapper.subscriptionId == subId && wrapper.event.kind == 10002) {
                        val urls = wrapper.event.tags
                            .filter { it.isNotEmpty() && it[0] == "r" }
                            .mapNotNull { it.getOrNull(1)?.takeIf { url -> url.startsWith("ws") } }
                        deferred.complete(urls)
                    }
                }
            }
            relayPool.subscribe(subId, filter)

            try {
                val result = deferred.await()
                result
            } finally {
                relayPool.unsubscribe(subId)
                job.cancel()
            }
        } ?: emptyList()
    }

    /**
     * Apply a new relay set: save to storage, reconfigure the pool.
     */
    private fun applyRelays(urls: List<String>) {
        if (urls.isEmpty()) return
        val sanitized = urls
            .filter { it.startsWith("wss://") || it.startsWith("ws://") }
            .distinct()
        if (sanitized.isEmpty()) return

        keyStore.saveRelays(sanitized)
        relayPool.reconfigure(sanitized)
        (currentRelays as MutableStateFlow).value = sanitized
        Log.d(TAG, "Relays updated to: $sanitized")
    }

    /**
     * Decrypt and store an incoming gift-wrapped DM.
     */
    private suspend fun handleIncomingDM(event: RelayEvent, signer: NostrSigner) {
        val unwrapped = messaging.unwrapGiftWrap(event, signer) ?: return
        val chatMsg = ChatMessage(
            id = event.id,
            senderPubkey = unwrapped.senderPubkeyHex,
            senderNpub = unwrapped.senderNpub,
            content = unwrapped.content,
            createdAt = unwrapped.createdAt
        )
        repo.saveMessage(chatMsg)
        Log.d(TAG, "Stored DM from ${unwrapped.senderNpub.take(20)}...")
    }

    /**
     * Decrypt and restore a backup event.
     */
    private suspend fun handleBackupEvent(event: RelayEvent, signer: NostrSigner) {
        val payload = backupService.decryptBackup(event.content, signer) ?: return
        restoreFromPayload(payload)
        Log.d(TAG, "Restored backup: ${payload.feedings.size} feedings, ${payload.sleeps.size} sleeps")
    }

    /**
     * Decrypt a partner sync event and merge their data into our local database.
     * The partner encrypted this payload to our pubkey — we decrypt with our key.
     *
     * Security: verifies the event pubkey matches our configured partner npub
     * before decrypting, preventing arbitrary relay-injected data merges.
     */
    private suspend fun handlePartnerSyncEvent(event: RelayEvent, signer: NostrSigner) {
        // Verify the sender is our configured partner
        val expectedPartnerHex = _partnerNpub.value?.let { npubToHex(it) }
        if (expectedPartnerHex == null) {
            Log.w(TAG, "Partner sync event received but no partner npub configured — ignoring")
            return
        }
        if (event.pubkey != expectedPartnerHex) {
            Log.w(TAG, "Partner sync event from unexpected pubkey ${event.pubkey.take(20)}... — ignoring")
            return
        }

        // Verify the event signature to prevent pubkey spoofing
        val nostrEvent = NostrEvent(
            id = event.id, pubkey = event.pubkey, created_at = event.createdAt,
            kind = event.kind, tags = event.tags, content = event.content, sig = event.sig
        )
        if (!nostrEvent.verifySignature()) {
            Log.w(TAG, "Partner sync event signature verification failed — ignoring")
            return
        }

        val payload = backupService.decryptPartnerBackup(event.content, signer, expectedPartnerHex) ?: return
        restoreFromPayload(payload)
        Log.d(TAG, "Partner sync: merged ${payload.feedings.size} feedings, ${payload.sleeps.size} sleeps from partner")
    }

    /**
     * Restore data from a decrypted backup payload into local Room database.
     */
    private suspend fun restoreFromPayload(payload: BackupPayload) {
        payload.children.forEach { repo.saveChild(it) }
        payload.feedings.forEach { repo.saveFeeding(it) }
        payload.sleeps.forEach { repo.saveSleep(it) }
        payload.weights.forEach { repo.saveWeight(it) }
        payload.milestones.forEach { repo.saveMilestone(it) }
        payload.diapers.forEach { repo.saveDiaper(it) }
        payload.pumpings.forEach { repo.savePumping(it) }
        payload.healthRecords.forEach { repo.saveHealthRecord(it) }
    }

    /**
     * Export encrypted backup to relays.
     * If a partner npub is set, also publishes a partner-encrypted copy so the
     * co-parent's app can merge the data.
     */
    suspend fun exportBackup(): Boolean {
        val signer = _signer.value ?: return false
        val selfOk = backupService.export(signer)

        // Dual-publish to partner if configured
        val partnerNpubVal = _partnerNpub.value
        if (selfOk && partnerNpubVal != null) {
            val partnerHex = npubToHex(partnerNpubVal)
            if (partnerHex != null) {
                backupService.exportToPartner(signer, partnerHex)
            }
        }
        return selfOk
    }

    /**
     * Send a DM to the other parent.
     */
    suspend fun sendMessage(text: String, recipientNpub: String): Boolean {
        val signer = _signer.value ?: return false
        return messaging.sendDirectMessage(text, signer, recipientNpub)
    }

    /**
     * Disconnect everything.
     */
    fun shutdown() {
        relayPool.disconnect()
        scope.cancel()
    }
}
