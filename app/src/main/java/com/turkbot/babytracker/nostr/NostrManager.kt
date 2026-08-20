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
import com.turkbot.babytracker.nostr.nip05.Nip05Resolver
import com.turkbot.babytracker.nostr.relay.RelayEvent
import com.turkbot.babytracker.nostr.relay.RelayPool
import com.turkbot.babytracker.nostr.relay.RelayState
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

    // Effective relays — always use defaults to ensure both parents are on the
    // same relay set. Previously we saved NIP-65 relays per-user, which caused
    // the two phones to end up on different relays — partner sync and DMs never
    // crossed. Stored relay preferences are now ignored.
    private val effectiveRelays: List<String> = defaultRelays

    val relayPool = RelayPool(effectiveRelays, httpClient)
    private val backupService = BackupService(appContext, relayPool)
    private val messaging = GiftWrapMessaging(relayPool)
    val nip05Resolver = Nip05Resolver(httpClient, relayPool)

    private val _signer = MutableStateFlow<NostrSigner?>(null)
    val signer: StateFlow<NostrSigner?> = _signer

    private val _relayConnected = MutableStateFlow(false)
    val relayConnected: StateFlow<Boolean> = _relayConnected

    /** Per-relay connection states (url → state) for the relay checker UI. */
    fun relayStates(): List<Pair<String, RelayState>> = relayPool.relayStates()

    /**
     * Check whether the partner has published any kind 30078 events to our
     * shared relays. Returns a map of relay URL → Boolean (true if the relay
     * returned at least one event from the partner).
     */
    suspend fun checkPartnerReachable(): Map<String, Boolean> {
        val partnerNpubVal = _partnerNpub.value ?: return emptyMap()
        val partnerHex = npubToHex(partnerNpubVal) ?: return emptyMap()
        val filter = """{"kinds":[30078],"authors":["$partnerHex"],"limit":1}"""
        val subId = "partner_check"
        val seen = mutableSetOf<String>()

        val job = scope.launch {
            relayPool.events.collect { wrapper ->
                if (wrapper.subscriptionId == subId) {
                    seen.add(wrapper.relayUrl)
                }
            }
        }

        relayPool.subscribe(subId, filter)

        // Give all relays 5 seconds to respond
        kotlinx.coroutines.delay(5_000)
        relayPool.unsubscribe(subId)
        job.cancel()

        // Build results: true for relays that returned events, false for others
        val results = mutableMapOf<String, Boolean>()
        relayPool.relayStates().forEach { (url, _) ->
            results[url] = seen.contains(url)
        }
        return results.toMap()
    }
    val currentRelays: StateFlow<List<String>> = MutableStateFlow(effectiveRelays)

    private val _partnerNpub = MutableStateFlow<String?>(keyStore.getPartnerNpub())
    val partnerNpub: StateFlow<String?> = _partnerNpub

    private val _partnerNip05 = MutableStateFlow<String?>(keyStore.getPartnerNip05())
    val partnerNip05: StateFlow<String?> = _partnerNip05

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
                val pkg = keyStore.getSignerPackage()
                if (npub != null && pkg != null) {
                    val pubHex = npubToHex(npub)
                    if (pubHex != null) {
                        val signer = AmberSigner(npub, pubHex, pkg)
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
     * Log in with an external NIP-55 signer (e.g. Amber). Requests the pubkey
     * via the `nostrsigner:` URI scheme; the private key never enters this app.
     *
     * After login, the signer's package name is stored so all subsequent
     * sign/encrypt/decrypt requests are addressed to it.
     *
     * Returns the npub on success, null on failure or user rejection.
     */
    suspend fun loginWithAmber(): String? {
        return try {
            val loginResult = AmberSigner.requestPubkey()
            if (loginResult == null || loginResult.npub.isBlank()) {
                Log.e(TAG, "Signer returned empty pubkey")
                return null
            }
            // Store npub + signer package name
            keyStore.saveAmberNpub(loginResult.npub, loginResult.signerPackage)
            val signer = AmberSigner(
                npub = loginResult.npub,
                pubkeyHexStr = loginResult.pubkeyHex,
                signerPackage = loginResult.signerPackage
            )
            _signer.value = signer
            connectAndSubscribe(signer)
            scope.launch { fetchAndApplyNip65Relays(loginResult.pubkeyHex) }
            loginResult.npub
        } catch (e: Exception) {
            Log.e(TAG, "External signer login failed", e)
            null
        }
    }

    /**
     * Check if a NIP-55 external signer (e.g. Amber) is installed.
     * Per NIP-55: query for activities that handle the `nostrsigner:` URI scheme.
     */
    fun isAmberInstalled(): Boolean {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("nostrsigner:")
        )
        return appContext.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
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
     * Also clears any cached partner NIP-05.
     */
    fun setPartnerNpub(npub: String?) {
        val trimmed = npub?.trim()?.takeIf { it.startsWith("npub1") }
        keyStore.savePartnerNpub(trimmed)
        _partnerNpub.value = trimmed
        keyStore.savePartnerNip05(null)
        _partnerNip05.value = null
        if (trimmed != null) {
            Log.d(TAG, "Partner npub set: ${trimmed.take(20)}...")
        }
    }

    /**
     * Set partner by accepting either an npub (npub1...) or a NIP-05 identifier
     * (name@domain). If a NIP-05 is given, it is resolved via DNS to an npub first.
     *
     * @return true if the partner was set successfully, false if resolution failed
     */
    suspend fun setPartnerIdentifier(input: String): Boolean {
        val trimmed = input.trim()
        return if (trimmed.startsWith("npub1")) {
            setPartnerNpub(trimmed)
            true
        } else if (nip05Resolver.isNip05(trimmed)) {
            val npub = nip05Resolver.resolve(trimmed)
            if (npub != null) {
                setPartnerNpub(npub)
                // Save the NIP-05 as the display name
                keyStore.savePartnerNip05(trimmed)
                _partnerNip05.value = trimmed
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    /**
     * Fetch the partner's NIP-05 from their kind 0 metadata on relays and cache it.
     * Call this after partner npub is set (e.g. via npub paste) to populate the
     * human-readable display name.
     */
    suspend fun refreshPartnerNip05() {
        val partnerNpub = _partnerNpub.value ?: return
        val partnerHex = npubToHex(partnerNpub) ?: return
        val nip05 = nip05Resolver.fetchNip05(partnerHex)
        if (nip05 != null) {
            keyStore.savePartnerNip05(nip05)
            _partnerNip05.value = nip05
            Log.d(TAG, "Partner NIP-05: $nip05")
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
        scope.launch {
            _connectAndSubscribeInternal(signer)
        }
    }

    private suspend fun _connectAndSubscribeInternal(signer: NostrSigner) {
        // Start the event collector BEFORE connecting/subscribing so we don't
        // miss any events that arrive immediately after subscription.
        val myPubkeyHex = signer.pubkeyHex

        scope.launch {
            relayPool.events.collect { wrapper ->
                try {
                    when (wrapper.event.kind) {
                        GiftWrapMessaging.KIND_GIFT_WRAP -> {
                            if (wrapper.subscriptionId == SUB_DMS) {
                                handleIncomingDM(wrapper.event, signer)
                            }
                        }
                        BackupService.BACKUP_KIND -> {
                            when (wrapper.subscriptionId) {
                                SUB_BACKUP -> handleBackupEvent(wrapper.event, signer)
                                SUB_PARTNER_SYNC,
                                SUB_PARTNER_SYNC + "_author" -> handlePartnerSyncEvent(wrapper.event, signer)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling relay event kind=${wrapper.event.kind}", e)
                }
            }
        }

        // Now connect and subscribe — the collector is already listening
        relayPool.connect()

        // Subscribe to gift-wrapped DMs (kind 1059) addressed to us
        val dmFilter = """{"kinds":[1059],"#p":["$myPubkeyHex"],"limit":100}"""
        relayPool.subscribe(SUB_DMS, dmFilter)

        // Subscribe to our own encrypted backups (kind 30078 authored by us).
        // We don't filter by #d tag because some relays don't support combining
        // authors + tag filters. We filter by d-tag client-side instead.
        val backupFilter = """{"kinds":[30078],"authors":["$myPubkeyHex"],"limit":50}"""
        relayPool.subscribe(SUB_BACKUP, backupFilter)

        // Subscribe to partner sync events (kind 30078 where #p = our pubkey).
        // We don't filter by #d tag here because many relays don't support querying
        // by multiple different tag types simultaneously. Instead we fetch all
        // kind 30078 events that p-tag us and filter by d-tag client-side.
        val partnerSyncFilter = """{"kinds":[30078],"#p":["$myPubkeyHex"],"limit":50}"""
        relayPool.subscribe(SUB_PARTNER_SYNC, partnerSyncFilter)

        // Also subscribe to ALL kind 30078 events authored by our partner, in case
        // the relay doesn't index #p tags on kind 30078. We filter client-side.
        val partnerNpubVal = _partnerNpub.value
        if (partnerNpubVal != null) {
            val partnerHex = npubToHex(partnerNpubVal)
            if (partnerHex != null) {
                val partnerAuthorFilter = """{"kinds":[30078],"authors":["$partnerHex"],"limit":50}"""
                relayPool.subscribe(SUB_PARTNER_SYNC + "_author", partnerAuthorFilter)
            }
        }

        // If partner is set but we don't have their NIP-05 yet, fetch it
        if (_partnerNpub.value != null && _partnerNip05.value == null) {
            scope.launch { refreshPartnerNip05() }
        }
    }

    /**
     * Fetch the user's NIP-65 relay list (kind 10002).
     * We do NOT auto-reconfigure to NIP-65 relays anymore — that caused the two
     * phones to end up on different relay sets, so partner sync events and DMs
     * never crossed. Instead, NIP-65 is informational only; the default relays
     * (damus, nos.lol, primal) are used for all communication.
     */
    private suspend fun fetchAndApplyNip65Relays(pubkeyHex: String) {
        val relays = fetchNip65Relays(pubkeyHex)
        if (relays.isNotEmpty()) {
            Log.d(TAG, "NIP-65: found ${relays.size} relays for user (not auto-applying — using defaults for partner sync)")
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

            // Start the collector BEFORE subscribing so we don't miss events
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
            // Subscribe after collector is ready
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
     *
     * Skips messages already in the DB (by event ID) to avoid redundant Amber
     * decrypt prompts on reconnect. With Amber as signer, each decrypt launches
     * an Activity, so dedup is critical to prevent prompt storms.
     */
    private suspend fun handleIncomingDM(event: RelayEvent, signer: NostrSigner) {
        // Skip already-stored messages — avoids redundant Amber decrypt prompts
        if (repo.messageExists(event.id)) {
            Log.d(TAG, "DM ${event.id.take(12)} already stored — skipping")
            return
        }

        // Get expected partner hex for early sender filtering. If partner is not
        // configured, skip all DMs — no point decrypting messages we'll reject.
        val expectedPartnerHex = _partnerNpub.value?.let { npubToHex(it) }
        if (expectedPartnerHex == null) {
            Log.w(TAG, "DM received but no partner npub configured — ignoring")
            return
        }

        // Unwrap with sender filter: only does the second (expensive) Amber decrypt
        // if the seal's pubkey matches our partner. Non-partner DMs still require
        // the first decrypt (sender identity is hidden inside the seal by NIP-17),
        // but skip the second decrypt entirely.
        val unwrapped = messaging.unwrapGiftWrap(event, signer, expectedPartnerHex) ?: return

        // Double-check: unwrapGiftWrap already filtered, but verify for safety
        if (unwrapped.senderPubkeyHex != expectedPartnerHex) {
            Log.w(TAG, "DM from non-partner pubkey — ignoring")
            return
        }

        val chatMsg = ChatMessage(
            id = event.id,
            senderPubkey = unwrapped.senderPubkeyHex,
            senderNpub = unwrapped.senderNpub,
            content = unwrapped.content,
            createdAt = unwrapped.createdAt
        )
        repo.saveMessage(chatMsg)
        Log.d(TAG, "Stored DM from partner")
    }

    /**
     * Decrypt and restore a backup event.
     * Only processes events with d-tag = BACKUP_D_TAG (our self-backup).
     */
    private suspend fun handleBackupEvent(event: RelayEvent, signer: NostrSigner) {
        val dTag = event.tags.firstOrNull { it.isNotEmpty() && it[0] == "d" }?.getOrNull(1)
        if (dTag != BackupService.BACKUP_D_TAG) {
            Log.d(TAG, "Kind 30078 from us but d-tag is '$dTag' (not self-backup) — ignoring")
            return
        }
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
        // Check the d-tag client-side — we couldn't filter by it in the relay
        // subscription because many relays don't support multi-tag queries.
        val dTag = event.tags.firstOrNull { it.isNotEmpty() && it[0] == "d" }?.getOrNull(1)
        if (dTag != BackupService.PARTNER_SYNC_D_TAG) {
            Log.d(TAG, "Kind 30078 p-tagged us but d-tag is '$dTag' (not partner sync) — ignoring")
            return
        }

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
     * Only allows sending to the configured partner npub — blocks sends to
     * arbitrary recipients to prevent accidental data leakage.
     */
    suspend fun sendMessage(text: String, recipientNpub: String): Boolean {
        val signer = _signer.value ?: return false

        // Enforce partner-only messaging
        val configuredPartner = _partnerNpub.value
        if (configuredPartner == null || configuredPartner != recipientNpub.trim()) {
            Log.w(TAG, "Blocked send to non-partner recipient — only the configured partner is allowed")
            return false
        }

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
