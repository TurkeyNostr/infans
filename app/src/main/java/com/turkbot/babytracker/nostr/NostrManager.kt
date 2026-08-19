package com.turkbot.babytracker.nostr

import android.content.Context
import android.util.Log
import com.turkbot.babytracker.data.entities.ChatMessage
import com.turkbot.babytracker.data.repo.BabyRepository
import com.turkbot.babytracker.data.repo.BackupPayload
import com.turkbot.babytracker.nostr.crypto.NostrKeyPair
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import com.turkbot.babytracker.nostr.crypto.SecureKeyStore
import com.turkbot.babytracker.nostr.events.BackupService
import com.turkbot.babytracker.nostr.messaging.GiftWrapMessaging
import com.turkbot.babytracker.nostr.relay.RelayEvent
import com.turkbot.babytracker.nostr.relay.RelayPool
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Central Nostr manager — owns the relay pool, keys, backup service, and messaging.
 * Handles incoming gift-wrapped DMs and encrypted backups.
 */
class NostrManager(context: Context) {

    private val appContext = context.applicationContext
    private val keyStore = SecureKeyStore(appContext)
    private val repo = BabyRepository(appContext)

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSocket stays open
        .build()

    // Default relays — nos.lol is user's preferred relay
    private val relayUrls = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.primal.net"
    )

    val relayPool = RelayPool(relayUrls, httpClient)
    private val backupService = BackupService(appContext, relayPool)
    private val messaging = GiftWrapMessaging(relayPool)

    private val _keys = MutableStateFlow<NostrKeyPair?>(null)
    val keys: StateFlow<NostrKeyPair?> = _keys

    private val _relayConnected = MutableStateFlow(false)
    val relayConnected: StateFlow<Boolean> = _relayConnected

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "NostrManager"
        const val SUB_DMS = "baby_dm_sub"
        const val SUB_BACKUP = "baby_backup_sub"
    }

    /**
     * Initialize: load keys, connect to relays, subscribe.
     */
    suspend fun initialize() {
        // Load stored keys
        val stored = keyStore.getKeyPair()
        if (stored != null) {
            _keys.value = stored
            connectAndSubscribe(stored)
        }
    }

    /**
     * Generate a new Nostr identity for this parent.
     */
    suspend fun generateIdentity(): NostrKeyPair {
        val newKeys = NostrKeys.generate()
        keyStore.saveKeyPair(newKeys)
        _keys.value = newKeys
        connectAndSubscribe(newKeys)
        return newKeys
    }

    /**
     * Import an existing identity via nsec.
     */
    suspend fun importIdentity(nsec: String): NostrKeyPair? {
        return try {
            val priv = NostrKeys.decodeNsec(nsec)
            val keys = NostrKeys.fromPrivateKey(priv)
            keyStore.saveKeyPair(keys)
            _keys.value = keys
            connectAndSubscribe(keys)
            keys
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import nsec", e)
            null
        }
    }

    private fun connectAndSubscribe(keys: NostrKeyPair) {
        relayPool.connect()

        // Subscribe to gift-wrapped DMs (kind 1059) addressed to us
        val myPubkeyHex = NostrKeys.toHex(keys.publicKey)
        val dmFilter = """{"kinds":[1059],"#p":["$myPubkeyHex"],"limit":100}"""
        relayPool.subscribe(SUB_DMS, dmFilter)

        // Subscribe to our own encrypted backups (kind 30078)
        val backupFilter = """{"kinds":[30078],"authors":["$myPubkeyHex"],"#d":["${BackupService.BACKUP_D_TAG}"],"limit":1}"""
        relayPool.subscribe(SUB_BACKUP, backupFilter)

        // Listen for incoming events
        scope.launch {
            relayPool.events.collect { wrapper ->
                when (wrapper.event.kind) {
                    GiftWrapMessaging.KIND_GIFT_WRAP -> {
                        if (wrapper.subscriptionId == SUB_DMS) {
                            handleIncomingDM(wrapper.event, keys)
                        }
                    }
                    BackupService.BACKUP_KIND -> {
                        if (wrapper.subscriptionId == SUB_BACKUP) {
                            handleBackupEvent(wrapper.event, keys)
                        }
                    }
                }
            }
        }
    }

    /**
     * Decrypt and store an incoming gift-wrapped DM.
     */
    private suspend fun handleIncomingDM(event: RelayEvent, keys: NostrKeyPair) {
        val unwrapped = messaging.unwrapGiftWrap(event, keys) ?: return
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
    private suspend fun handleBackupEvent(event: RelayEvent, keys: NostrKeyPair) {
        val payload = backupService.decryptBackup(event.content, keys) ?: return
        restoreFromPayload(payload)
        Log.d(TAG, "Restored backup: ${payload.feedings.size} feedings, ${payload.sleeps.size} sleeps")
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
    }

    /**
     * Export encrypted backup to relays.
     */
    suspend fun exportBackup(): Boolean {
        val keys = _keys.value ?: return false
        return backupService.export(keys)
    }

    /**
     * Send a DM to the other parent.
     */
    suspend fun sendMessage(text: String, recipientNpub: String): Boolean {
        val keys = _keys.value ?: return false
        return messaging.sendDirectMessage(text, keys, recipientNpub)
    }

    /**
     * Get the partner's npub from DataStore (set during pairing).
     */
    var partnerNpub: String? = null

    /**
     * Disconnect everything.
     */
    fun shutdown() {
        relayPool.disconnect()
        scope.cancel()
    }
}
