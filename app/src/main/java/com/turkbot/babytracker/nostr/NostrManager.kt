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
import com.turkbot.babytracker.debug.DebugLogger as Dbg
import com.turkbot.babytracker.debug.DebugLogger.Category as Cat
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Effective relays — start with defaults. After login, NIP-65 relays
    // (kind 10002) are fetched and applied if the user has any set.
    // Defaults (damus, nos.lol, primal) are used when no NIP-65 list exists.
    private val effectiveRelays: List<String> = defaultRelays

    val relayPool = RelayPool(effectiveRelays, httpClient)
    private val backupService = BackupService(appContext, relayPool)
    private val messaging = GiftWrapMessaging(relayPool)
    val nip05Resolver = Nip05Resolver(httpClient, relayPool)

    private val _signer = MutableStateFlow<NostrSigner?>(null)
    val signer: StateFlow<NostrSigner?> = _signer

    /** Whether at least one relay is connected — backed by RelayPool's live flow. */
    val relayConnected: StateFlow<Boolean> = relayPool.anyConnected

    /** Current user's own NIP-05, fetched from kind 0 metadata after login. */
    private val _myNip05 = MutableStateFlow<String?>(null)
    val myNip05: StateFlow<String?> = _myNip05

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

    /** Event IDs currently being decrypted — prevents the same event from
     *  triggering multiple Amber prompts when 3 relays each return it. */
    private val inflightEvents = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Serializes ALL Amber signer operations (encrypt, sign, decrypt) to
     *  prevent prompt interleaving between exports, DM sends, and incoming
     *  event handlers. Without this, a relay echoing back our just-published
     *  backup triggers a decrypt prompt mid-export — the user approves the
     *  wrong operation and the partner-sync event is silently lost. */
    private val amberMutex = Mutex()

    /** Wall-clock seconds of our most recent self-backup publish. Used to
     *  skip the relay's echo of our own just-published event — decrypting
     *  it wastes an Amber prompt and risks interleaving with the partner-
     *  sync export that follows. Zero on a fresh install (cross-device
     *  restore processes all events). */
    @Volatile
    private var lastSelfExportTime: Long = 0L

    /** Debounce job for exportBackup — coalesces rapid calls into a single
     *  export so the user isn't bombarded with Amber prompts when entering
     *  multiple data items in quick succession (e.g. feeding + diaper + sleep).
     *  Without this, 3 quick entries = 12 Amber prompts (4 per export). */
    private var pendingExportJob: Job? = null

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
                    scope.launch { refreshMyNip05() }
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
                        scope.launch { refreshMyNip05() }
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
        scope.launch { refreshMyNip05() }
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
            scope.launch { refreshMyNip05() }
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
            scope.launch { refreshMyNip05() }
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
            // Clear processed-events cache and timestamps so we re-fetch partner data fresh
            keyStore.clearProcessedEvents()
            // Re-subscribe to partner events immediately with no 'since' filter
            // (clearProcessedEvents reset lastBackupTime to 0)
            scope.launch {
                val partnerHex = npubToHex(trimmed)
                if (partnerHex != null) {
                    // Unsubscribe old partner-author sub if it exists
                    relayPool.unsubscribe(SUB_PARTNER_SYNC + "_author")
                    // Re-subscribe with full history (no since) so we fetch all partner data
                    val myPubkeyHex = _signer.value?.pubkeyHex
                    if (myPubkeyHex != null) {
                        val partnerSyncDTag = BackupService.PARTNER_SYNC_D_TAG
                        relayPool.subscribe(
                            SUB_PARTNER_SYNC + "_author",
                            """{"kinds":[30078],"authors":["$partnerHex"],"#d":["$partnerSyncDTag"],"limit":10}"""
                        )
                        // Also re-subscribe the #p filter in case the relay indexes p-tags
                        relayPool.unsubscribe(SUB_PARTNER_SYNC)
                        relayPool.subscribe(
                            SUB_PARTNER_SYNC,
                            """{"kinds":[30078],"#p":["$myPubkeyHex"],"#d":["$partnerSyncDTag"],"limit":10}"""
                        )
                    }
                }
            }
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

    /** Fetch and cache our own NIP-05 from kind 0 metadata. */
    suspend fun refreshMyNip05() {
        val signerVal = _signer.value ?: return
        val nip05 = nip05Resolver.fetchNip05(signerVal.pubkeyHex)
        if (nip05 != null) {
            _myNip05.value = nip05
            Log.d(TAG, "My NIP-05: $nip05")
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
        Dbg.info(Cat.RELAY, "Connecting to relays and starting subscriptions")

        scope.launch {
            relayPool.events.collect { wrapper ->
                try {
                    when (wrapper.event.kind) {
                        GiftWrapMessaging.KIND_GIFT_WRAP -> {
                            if (wrapper.subscriptionId == SUB_DMS) {
                                // Launch in a separate coroutine so the event
                                // collector isn't blocked while waiting for
                                // amberMutex — other events keep flowing.
                                scope.launch { handleIncomingDM(wrapper.event, signer) }
                            }
                        }
                        BackupService.BACKUP_KIND -> {
                            when (wrapper.subscriptionId) {
                                SUB_BACKUP -> {
                                    // Skip the relay's echo of our own just-
                                    // published self-backup — decrypting it
                                    // wastes an Amber prompt and interleaves
                                    // with the partner-sync export that
                                    // follows in the same exportBackup() call.
                                    if (wrapper.event.pubkey == myPubkeyHex &&
                                        wrapper.event.createdAt >= lastSelfExportTime) {
                                        Dbg.info(Cat.SYNC, "Skipping self-backup echo from relay (just published)")
                                    } else {
                                        scope.launch { handleBackupEvent(wrapper.event, signer) }
                                    }
                                }
                                SUB_PARTNER_SYNC,
                                SUB_PARTNER_SYNC + "_author" -> {
                                    scope.launch { handlePartnerSyncEvent(wrapper.event, signer) }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling relay event kind=${wrapper.event.kind}", e)
                Dbg.exception(Cat.GENERAL, "Error handling relay event kind=${wrapper.event.kind}", e)
                }
            }
        }

        // Now connect and subscribe — the collector is already listening
        relayPool.connect()

        // Subscribe to gift-wrapped DMs (kind 1059) addressed to us.
        // NO 'since' filter: NIP-17 gift wraps have RANDOMIZED timestamps
        // (up to 2 days in the past), so a since-filter would drop new messages
        // whose randomized created_at falls before our cursor. Dedup is handled
        // client-side via repo.messageExists() + inflightEvents.
        // Limit 20: each DM requires 2 sequential Amber nip44_decrypt calls (gift
        // wrap → seal → rumor), so 20 DMs = 40 prompts max on first launch.
        val dmFilter = """{"kinds":[1059],"#p":["$myPubkeyHex"],"limit":20}"""
        relayPool.subscribe(SUB_DMS, dmFilter)
        Dbg.info(Cat.RELAY, "Subscribed to DMs (no since-filter — gift wrap timestamps are randomized)")

        // Subscribe to our own encrypted backups (kind 30078 authored by us,
        // d-tag = "baby-tracker-backup"). Filtering by #d server-side avoids
        // pulling down our partner-sync events and other unrelated 30078s.
        // Use 'since' to avoid re-processing old backups on every reconnect.
        val lastBackupTime = keyStore.getLastBackupTime()
        val backupDTag = BackupService.BACKUP_D_TAG
        val backupFilter = if (lastBackupTime > 0) {
            """{"kinds":[30078],"authors":["$myPubkeyHex"],"#d":["$backupDTag"],"since":$lastBackupTime,"limit":10}"""
        } else {
            """{"kinds":[30078],"authors":["$myPubkeyHex"],"#d":["$backupDTag"],"limit":10}"""
        }
        relayPool.subscribe(SUB_BACKUP, backupFilter)

        // Subscribe to partner sync events (kind 30078 where #p = our pubkey,
        // d-tag = "baby-tracker-sync"). Filtering by #d server-side avoids
        // pulling down unrelated 30078 events that happen to p-tag us.
        // Uses a SEPARATE cursor from self-backup — self-backup events advance
        // lastBackupTime, which must not skip partner events published earlier.
        val partnerSyncDTag = BackupService.PARTNER_SYNC_D_TAG
        val lastPartnerSyncTime = keyStore.getLastPartnerSyncTime()
        val partnerSyncFilter = if (lastPartnerSyncTime > 0) {
            """{"kinds":[30078],"#p":["$myPubkeyHex"],"#d":["$partnerSyncDTag"],"since":$lastPartnerSyncTime,"limit":10}"""
        } else {
            """{"kinds":[30078],"#p":["$myPubkeyHex"],"#d":["$partnerSyncDTag"],"limit":10}"""
        }
        relayPool.subscribe(SUB_PARTNER_SYNC, partnerSyncFilter)

        // Also subscribe to partner-sync events authored by our partner, in case
        // the relay doesn't index #p tags on kind 30078. Filter by #d so we only
        // get their partner-sync events, not their self-backups.
        val partnerNpubVal = _partnerNpub.value
        if (partnerNpubVal != null) {
            val partnerHex = npubToHex(partnerNpubVal)
            if (partnerHex != null) {
                val partnerAuthorFilter = if (lastPartnerSyncTime > 0) {
                    """{"kinds":[30078],"authors":["$partnerHex"],"#d":["$partnerSyncDTag"],"since":$lastPartnerSyncTime,"limit":10}"""
                } else {
                    """{"kinds":[30078],"authors":["$partnerHex"],"#d":["$partnerSyncDTag"],"limit":10}"""
                }
                relayPool.subscribe(SUB_PARTNER_SYNC + "_author", partnerAuthorFilter)
            }
        }

        // If partner is set but we don't have their NIP-05 yet, fetch it
        if (_partnerNpub.value != null && _partnerNip05.value == null) {
            scope.launch { refreshPartnerNip05() }
        }
    }

    /**
     * Fetch the user's NIP-65 relay list (kind 10002) and reconfigure the pool.
     * Uses NIP-65 relays if found; falls back to defaults if not.
     */
    private suspend fun fetchAndApplyNip65Relays(pubkeyHex: String) {
        val relays = fetchNip65Relays(pubkeyHex)
        if (relays.isNotEmpty()) {
            Log.d(TAG, "NIP-65: found ${relays.size} relays for user, applying")
        Dbg.info(Cat.RELAY, "NIP-65: found ${relays.size} relays, merging with defaults")
            applyRelays(relays)
            // Re-subscribe partner sync on the new relays if partner is configured
            val partnerNpubVal = _partnerNpub.value
            if (partnerNpubVal != null) {
                val partnerHex = npubToHex(partnerNpubVal)
                if (partnerHex != null) {
                    val lastPartnerSyncTime = keyStore.getLastPartnerSyncTime()
                    val partnerSyncDTag = BackupService.PARTNER_SYNC_D_TAG
                    val partnerAuthorFilter = if (lastPartnerSyncTime > 0) {
                        """{"kinds":[30078],"authors":["$partnerHex"],"#d":["$partnerSyncDTag"],"since":$lastPartnerSyncTime,"limit":10}"""
                    } else {
                        """{"kinds":[30078],"authors":["$partnerHex"],"#d":["$partnerSyncDTag"],"limit":10}"""
                    }
                    relayPool.subscribe(SUB_PARTNER_SYNC + "_author", partnerAuthorFilter)
                }
            }
        } else {
            Log.d(TAG, "NIP-65: no relay list found, keeping default relays")
            Dbg.info(Cat.RELAY, "NIP-65: no relay list found, keeping default relays")
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
     * Merges NIP-65 relays with default relays so both parents always share
     * at least the default relays — preventing disjoint relay sets where
     * partner data can't be found.
     */
    private fun applyRelays(urls: List<String>) {
        if (urls.isEmpty()) return
        val sanitized = urls
            .filter { it.startsWith("wss://") || it.startsWith("ws://") }
            .distinct()
        if (sanitized.isEmpty()) return

        // Merge NIP-65 relays with defaults — guarantees both parents share
        // at least the default relays even if their NIP-65 lists differ
        val merged = (sanitized + defaultRelays).distinct()

        keyStore.saveRelays(merged)
        relayPool.reconfigure(merged)
        (currentRelays as MutableStateFlow).value = merged
        Log.d(TAG, "Relays updated to: $merged (NIP-65 + defaults merged)")
        Dbg.info(Cat.RELAY, "Relays reconfigured: ${merged.size} total (NIP-65 + defaults merged)")
    }

    /**
     * Fetch a pubkey's NIP-65 relay list using a dedicated subscription ID.
     * Used for checking the partner's relays.
     */
    private suspend fun fetchNip65ForPubkey(pubkeyHex: String, subId: String): List<String> {
        return withTimeoutOrNull(10_000) {
            val filter = """{"kinds":[10002],"authors":["$pubkeyHex"],"limit":1}"""
            val deferred = CompletableDeferred<List<String>>()

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
                deferred.await()
            } finally {
                relayPool.unsubscribe(subId)
                job.cancel()
            }
        } ?: emptyList()
    }

    /**
     * Compare our relay list against the partner's NIP-65 relay list.
     * Returns our relays, the partner's relays, and the overlap.
     */
    suspend fun checkPartnerRelayMatch(): RelayMatchResult {
        val partnerNpubVal = _partnerNpub.value ?: return RelayMatchResult(emptyList(), emptyList(), emptyList())
        val partnerHex = npubToHex(partnerNpubVal) ?: return RelayMatchResult(emptyList(), emptyList(), emptyList())

        val myRelays = currentRelays.value
        val partnerRelays = fetchNip65ForPubkey(partnerHex, "partner_nip65_check")

        val mySet = myRelays.map { it.removeSuffix("/") }.toSet()
        val partnerSet = partnerRelays.map { it.removeSuffix("/") }.toSet()
        val overlap = mySet.intersect(partnerSet).toList()

        return RelayMatchResult(myRelays, partnerRelays, overlap)
    }

    /**
     * Check whether the configured partner has us configured back.
     *
     * Fetches the partner's latest kind-30078 event with d="baby-tracker-sync"
     * and inspects its p-tags. If our pubkey is in the p-tags, they have us as
     * partner. Returns a [PartnerStatus] result.
     */
    suspend fun checkPartnerStatus(): PartnerStatus {
        val partnerNpubVal = _partnerNpub.value
            ?: return PartnerStatus.NoPartner
        val partnerHex = npubToHex(partnerNpubVal)
            ?: return PartnerStatus.NoPartner
        val myHex = _signer.value?.pubkeyHex
            ?: return PartnerStatus.NoPartner

        val dTag = BackupService.PARTNER_SYNC_D_TAG
        val filter = """{"kinds":[30078],"authors":["$partnerHex"],"#d":["$dTag"],"limit":1}"""
        val subId = "partner_status_check"

        var foundEvent: RelayEvent? = null
        val job = scope.launch {
            relayPool.events.collect { wrapper ->
                if (wrapper.subscriptionId == subId) {
                    foundEvent = wrapper.event
                }
            }
        }

        relayPool.subscribe(subId, filter)
        kotlinx.coroutines.delay(5_000)
        relayPool.unsubscribe(subId)
        job.cancel()

        val event = foundEvent
            ?: return PartnerStatus.NoInfansData

        // Check if our pubkey is in the p-tags
        val pTags = event.tags
            .filter { it.isNotEmpty() && it[0] == "p" }
            .mapNotNull { it.getOrNull(1) }
        val hasUs = pTags.contains(myHex)

        return if (hasUs) PartnerStatus.Mutual
        else PartnerStatus.HasDifferentPartner(pTags)
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
            Dbg.info(Cat.DM, "DM already stored — skipping decrypt")
            return
        }
        // Skip events we already attempted — failed decrypts must NOT retry,
        // otherwise a rejected Amber prompt or non-partner gift wrap loops
        // forever as each of 3 relays re-delivers the same event.
        if (keyStore.isEventProcessed(event.id)) {
            Log.d(TAG, "DM ${event.id.take(12)} already processed — skipping")
            Dbg.info(Cat.DM, "DM already processed — skipping")
            return
        }
        // Prevent duplicate decrypts when 3 relays return the same event
        if (!inflightEvents.add(event.id)) {
            Log.d(TAG, "DM ${event.id.take(12)} already being decrypted — skipping")
            Dbg.info(Cat.DM, "DM already being decrypted — skipping")
            return
        }
        try {
            // Get expected partner hex for early sender filtering. If partner is not
            // configured, skip all DMs — no point decrypting messages we'll reject.
            val expectedPartnerHex = _partnerNpub.value?.let { npubToHex(it) }
            if (expectedPartnerHex == null) {
                Log.w(TAG, "DM received but no partner npub configured — ignoring")
                Dbg.warn(Cat.DM, "DM received but no partner configured — ignoring")
                return
            }

            // unwrapGiftWrap does 2 sequential Amber nip44_decrypt calls.
            // AmberBridge already serializes all Amber calls via its own mutex,
            // so we do NOT hold amberMutex here — otherwise incoming decrypts
            // block user-initiated sends/exports behind a queue of Amber prompts.
            val unwrapped = messaging.unwrapGiftWrap(event, signer, expectedPartnerHex) ?: return

            if (unwrapped.senderPubkeyHex != expectedPartnerHex) {
                Log.w(TAG, "DM from non-partner pubkey — ignoring")
                Dbg.warn(Cat.DM, "DM from non-partner sender — ignoring")
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
            Dbg.info(Cat.DM, "DM received from partner — stored")

            keyStore.saveLastDmTime(System.currentTimeMillis() / 1000)
        } finally {
            // Mark as processed regardless of outcome — failed decrypts must
            // not retry on the next relay delivery, or we get a prompt storm.
            keyStore.markEventProcessed(event.id)
            inflightEvents.remove(event.id)
        }
    }

    /**
     * Decrypt and restore a backup event.
     * Only processes events with d-tag = BACKUP_D_TAG (our self-backup).
     * The relay subscription already filters by #d, but we check client-side
     * as a safety net in case a relay doesn't honour the #d filter.
     */
    private suspend fun handleBackupEvent(event: RelayEvent, signer: NostrSigner) {
        val dTag = event.tags.firstOrNull { it.isNotEmpty() && it[0] == "d" }?.getOrNull(1)
        if (dTag != BackupService.BACKUP_D_TAG) {
            Log.d(TAG, "Kind 30078 from us but d-tag is '$dTag' (not self-backup) — ignoring")
            Dbg.info(Cat.SYNC, "Kind 30078 self-authored but d-tag not backup — ignoring")
            return
        }
        // Skip events we've already decrypted — every relay returns the same
        // event, and re-decrypting triggers a fresh Amber prompt each time.
        if (keyStore.isEventProcessed(event.id)) {
            Log.d(TAG, "Backup event ${event.id.take(12)}… already processed — skipping decrypt")
            Dbg.info(Cat.SYNC, "Backup event already processed — skipping decrypt")
            return
        }
        // Prevent duplicate decrypts when 3 relays return the same event
        if (!inflightEvents.add(event.id)) {
            Log.d(TAG, "Backup event ${event.id.take(12)}… already being decrypted — skipping")
            Dbg.info(Cat.SYNC, "Backup event already being decrypted — skipping")
            return
        }
        try {
            // decryptBackup does an Amber nip44_decrypt. AmberBridge already
            // serializes all Amber calls via its own mutex, so we do NOT hold
            // amberMutex here — incoming decrypts must not block sends/exports.
            val payload = backupService.decryptBackup(event.content, signer) ?: return
            keyStore.saveLastBackupTime(event.createdAt)
            restoreFromPayload(payload)
            Log.d(TAG, "Restored backup: ${payload.feedings.size} feedings, ${payload.sleeps.size} sleeps")
            Dbg.info(Cat.SYNC, "Backup restored: ${payload.children.size} children, ${payload.feedings.size} feedings, ${payload.sleeps.size} sleeps, ${payload.weights.size} weights")
        } finally {
            // Mark as processed regardless of outcome — failed decrypts must
            // not retry on the next relay delivery, or we get a prompt storm.
            keyStore.markEventProcessed(event.id)
            inflightEvents.remove(event.id)
        }
    }

    /**
     * Decrypt a partner sync event and merge their data into our local database.
     * The partner encrypted this payload to our pubkey — we decrypt with our key.
     *
     * Security: verifies the event pubkey matches our configured partner npub
     * before decrypting, preventing arbitrary relay-injected data merges.
     */
    private suspend fun handlePartnerSyncEvent(event: RelayEvent, signer: NostrSigner) {
        // Check the d-tag client-side as a safety net — the relay subscription
        // already filters by #d, but relays may not honour it in all cases.
        val dTag = event.tags.firstOrNull { it.isNotEmpty() && it[0] == "d" }?.getOrNull(1)
        if (dTag != BackupService.PARTNER_SYNC_D_TAG) {
            Log.d(TAG, "Kind 30078 (partner sync sub) but d-tag is '$dTag' — ignoring")
            Dbg.info(Cat.SYNC, "Kind 30078 partner-sync sub but wrong d-tag — ignoring")
            return
        }

        // Skip events we've already decrypted — each relay returns the same
        // event, and re-decrypting triggers a fresh Amber prompt each time.
        if (keyStore.isEventProcessed(event.id)) {
            Log.d(TAG, "Partner sync event ${event.id.take(12)}… already processed — skipping decrypt")
            Dbg.info(Cat.SYNC, "Partner sync event already processed — skipping decrypt")
            return
        }
        // Prevent duplicate decrypts when 3 relays return the same event
        if (!inflightEvents.add(event.id)) {
            Log.d(TAG, "Partner sync event ${event.id.take(12)}… already being decrypted — skipping")
            Dbg.info(Cat.SYNC, "Partner sync event already being decrypted — skipping")
            return
        }
        try {
            // Verify the sender is our configured partner
            val expectedPartnerHex = _partnerNpub.value?.let { npubToHex(it) }
            if (expectedPartnerHex == null) {
                Log.w(TAG, "Partner sync event received but no partner npub configured — ignoring")
                Dbg.warn(Cat.SYNC, "Partner sync event received but no partner configured — ignoring")
                return
            }
            if (event.pubkey != expectedPartnerHex) {
                Log.w(TAG, "Partner sync event from unexpected pubkey ${event.pubkey.take(20)}... — ignoring")
                Dbg.warn(Cat.SYNC, "Partner sync event from unexpected sender — ignoring")
                return
            }

            // Verify the event signature to prevent pubkey spoofing
            val nostrEvent = NostrEvent(
                id = event.id, pubkey = event.pubkey, created_at = event.createdAt,
                kind = event.kind, tags = event.tags, content = event.content, sig = event.sig
            )
            if (!nostrEvent.verifySignature()) {
                Log.w(TAG, "Partner sync event signature verification failed — ignoring")
                Dbg.warn(Cat.SYNC, "Partner sync event signature verification failed — ignoring")
                return
            }

            // decryptPartnerBackup does an Amber nip44_decrypt. AmberBridge
            // already serializes all Amber calls via its own mutex, so we do
            // NOT hold amberMutex here — incoming decrypts must not block
            // user-initiated sends/exports.
            val payload = backupService.decryptPartnerBackup(event.content, signer, expectedPartnerHex) ?: return
            keyStore.saveLastPartnerSyncTime(event.createdAt)
            restoreFromPayload(payload)
            Log.d(TAG, "Partner sync: merged ${payload.feedings.size} feedings, ${payload.sleeps.size} sleeps from partner")
            Dbg.info(Cat.SYNC, "Partner sync restored: ${payload.children.size} children, ${payload.feedings.size} feedings, ${payload.sleeps.size} sleeps, ${payload.weights.size} weights")
        } finally {
            // Mark as processed regardless of outcome — failed decrypts must
            // not retry on the next relay delivery, or we get a prompt storm.
            keyStore.markEventProcessed(event.id)
            inflightEvents.remove(event.id)
        }
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
     * Restore data from a [BackupPayload] (e.g. from a local JSON backup file).
     * Public entry point for the "import from file" feature.
     */
    suspend fun restoreFromBackupPayload(payload: BackupPayload) {
        restoreFromPayload(payload)
    }

    /**
     * Export encrypted backup to relays.
     * If a partner npub is set, also publishes a partner-encrypted copy so the
     * co-parent's app can merge the data.
     *
     * Debounced: rapid calls (e.g. entering feeding + diaper + sleep in quick
     * succession) coalesce into a single export after a 2-second quiet period.
     * With Amber as signer, each export costs 2-4 user-approved prompts, so
     * batching is critical to prevent prompt fatigue.
     */
    suspend fun exportBackup(): Boolean {
        val signer = _signer.value ?: return false

        // Skip if no relays are connected — the publish would be silently
        // dropped and the user would approve Amber prompts for nothing.
        if (!relayPool.anyConnected.value) {
            Dbg.warn(Cat.SYNC, "Skipping export — no relays connected")
            return false
        }

        // Cancel any pending debounced export and start a new timer.
        // This coalesces rapid calls into one export after 2 seconds of quiet.
        pendingExportJob?.cancel()
        val deferred = CompletableDeferred<Boolean>()
        pendingExportJob = scope.launch {
            delay(2_000) // 2-second debounce window
            try {
                val result = amberMutex.withLock {
                    lastSelfExportTime = System.currentTimeMillis() / 1000
                    _exportBackupLocked(signer)
                }
                deferred.complete(result)
            } catch (e: Exception) {
                deferred.complete(false)
            }
        }
        return deferred.await()
    }

    private suspend fun _exportBackupLocked(signer: NostrSigner): Boolean {
        val selfOk = backupService.export(signer)
        Dbg.info(Cat.SYNC, "Self-backup publish ${if (selfOk) "sent to relays" else "failed"}")

        // Dual-publish to partner if configured
        val partnerNpubVal = _partnerNpub.value
        if (selfOk && partnerNpubVal != null) {
            val partnerHex = npubToHex(partnerNpubVal)
            if (partnerHex != null) {
                val partnerOk = backupService.exportToPartner(signer, partnerHex)
                Dbg.info(Cat.SYNC, "Partner-sync publish ${if (partnerOk) "sent to relays" else "failed"}")
            } else {
                Dbg.warn(Cat.SYNC, "Partner npub set but could not decode to hex — skipping partner sync")
            }
        } else if (selfOk && partnerNpubVal == null) {
            Dbg.info(Cat.SYNC, "No partner configured — skipping partner-sync publish")
        }
        return selfOk
    }

    /**
     * Delete all data stored on relays. Kind 30078 is replaceable (addressed
     * by pubkey + d-tag), so publishing an empty replacement with the same
     * d-tag overwrites the old event. Relays drop the old content.
     *
     * Deletes both:
     *   - Self-backup (d-tag "baby-tracker-backup", authored by us)
     *   - Partner sync (d-tag "baby-tracker-sync", authored by us)
     *
     * Returns true if both deletions were published (relays accepted them).
     */
    suspend fun deleteRelayData(): Boolean {
        val signer = _signer.value ?: return false

        // Serialize Amber signing with all other operations
        return amberMutex.withLock {
            var ok = true

            // Delete self-backup: publish empty replacement with same d-tag
            try {
                val selfDelete = NostrEvent.createSigned(
                    kind = BackupService.BACKUP_KIND,
                    content = "",
                    signer = signer,
                    tags = listOf(
                        listOf("d", BackupService.BACKUP_D_TAG),
                        listOf("client", "Infans", "1.0.0"),
                        listOf("deleted", "true")
                    )
                )
                relayPool.publish(selfDelete.toJsonObject())
                Log.d(TAG, "Deleted self-backup on relays")
                Dbg.info(Cat.SYNC, "Deleted self-backup on relays")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete self-backup", e)
                ok = false
            }

            // Delete partner sync: publish empty replacement with same d-tag
            try {
                val partnerTags = mutableListOf(
                    listOf("d", BackupService.PARTNER_SYNC_D_TAG),
                    listOf("client", "Infans", "1.0.0"),
                    listOf("deleted", "true")
                )
                // Keep the p-tag so relays that indexed by #p can still see it's gone
                _partnerNpub.value?.let { npub ->
                    npubToHex(npub)?.let { hex ->
                        partnerTags.add(listOf("p", hex))
                    }
                }

                val partnerDelete = NostrEvent.createSigned(
                    kind = BackupService.BACKUP_KIND,
                    content = "",
                    signer = signer,
                    tags = partnerTags
                )
                relayPool.publish(partnerDelete.toJsonObject())
                Log.d(TAG, "Deleted partner sync on relays")
                Dbg.info(Cat.SYNC, "Deleted partner sync on relays")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete partner sync", e)
                ok = false
            }

            // Clear the processed-events cache so we don't skip future events
            // that reuse the same d-tag address
            keyStore.clearProcessedEvents()

            ok
        }
    }

    /**
     * Send a DM to the other parent.
     * Only allows sending to the configured partner npub — blocks sends to
     * arbitrary recipients to prevent accidental data leakage.
     */
    suspend fun sendMessage(text: String, recipientNpub: String): Boolean {
        val signer = _signer.value ?: return false
        Dbg.info(Cat.DM, "Send button pressed — sending message")

        // Enforce partner-only messaging
        val configuredPartner = _partnerNpub.value
        if (configuredPartner == null || configuredPartner != recipientNpub.trim()) {
            Log.w(TAG, "Blocked send to non-partner recipient — only the configured partner is allowed")
            Dbg.warn(Cat.DM, "Blocked send to non-partner recipient")
            return false
        }

        // Serialize with all other Amber operations (exports, incoming handlers)
        return amberMutex.withLock {
            messaging.sendDirectMessage(text, signer, recipientNpub)
        }
    }

    /**
     * Disconnect everything.
     */
    fun shutdown() {
        pendingExportJob?.cancel()
        relayPool.disconnect()
        scope.cancel()
    }
}

data class RelayMatchResult(
    val myRelays: List<String>,
    val partnerRelays: List<String>,
    val overlap: List<String>
)

sealed class PartnerStatus {
    /** No partner npub configured. */
    object NoPartner : PartnerStatus()
    /** Partner has no Infans data on any relay (not running the app). */
    object NoInfansData : PartnerStatus()
    /** Partner has us configured — mutual relationship. */
    object Mutual : PartnerStatus()
    /** Partner runs Infans but has someone else (or nobody) configured as partner. */
    data class HasDifferentPartner(val pTags: List<String>) : PartnerStatus()
}
