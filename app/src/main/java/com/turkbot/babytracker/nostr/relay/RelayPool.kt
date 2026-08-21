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

package com.turkbot.babytracker.nostr.relay

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

/**
 * Manages multiple relay connections in parallel.
 * Events from all relays are merged into a single flow.
 * Publishes go to all connected relays for redundancy.
 *
 * Relay list can be swapped at runtime via [reconfigure] — e.g. when a user's
 * NIP-65 relay preferences are fetched after Amber sign-in.
 */
class RelayPool(
    initialRelays: List<String>,
    private val client: OkHttpClient
) {
    private var relayUrls = initialRelays
    private var connections = relayUrls.map { RelayConnection(it, client) }

    private val _events = MutableSharedFlow<NostrEventWrapper>(
        replay = 0,
        extraBufferCapacity = 256
    )
    val events: SharedFlow<NostrEventWrapper> = _events

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Collect from all connections
    private var collectJob: Job? = null

    val currentRelays: List<String> get() = relayUrls

    /** Backing flow for overall connection status. */
    private val _anyConnected = MutableStateFlow(false)
    val anyConnected: StateFlow<Boolean> = _anyConnected

    /** Recompute whether at least one relay is connected. Called after connect/reconfigure. */
    private fun refreshConnected() {
        _anyConnected.value = connections.any { it.state.value == RelayState.CONNECTED }
    }

    fun connect() {
        connections.forEach { it.connect() }
        collectJob?.cancel()
        collectJob = scope.launch {
            connections.forEach { conn ->
                launch {
                    for (wrapper in conn.events) {
                        _events.emit(wrapper)
                    }
                }
            }
            // Poll connection states and update the anyConnected flow
            launch {
                while (isActive) {
                    refreshConnected()
                    delay(2000)
                }
            }
        }
    }

    fun disconnect() {
        collectJob?.cancel()
        connections.forEach { it.disconnect() }
    }

    /**
     * Swap the relay set at runtime.
     * Disconnects old connections, creates new ones, reconnects, and re-subscribes
     * with all previously active subscription filters.
     */
    fun reconfigure(newUrls: List<String>) {
        if (newUrls.isEmpty() || newUrls == relayUrls) return

        // Capture active subscriptions before tearing down
        val activeSubs = connections.flatMap { it.getActiveSubscriptions() }

        disconnect()
        relayUrls = newUrls
        connections = newUrls.map { RelayConnection(it, client) }
        connect()

        // Re-apply subscriptions on the new relays
        activeSubs.forEach { (subId, filter) ->
            connections.forEach { it.subscribe(subId, filter) }
        }
    }

    fun publish(eventJson: String) {
        connections.forEach { it.publish(eventJson) }
    }

    fun subscribe(subId: String, filter: String) {
        connections.forEach { it.subscribe(subId, filter) }
    }

    fun unsubscribe(subId: String) {
        connections.forEach { it.unsubscribe(subId) }
    }

    fun connectedCount(): Int = connections.count { it.state.value == RelayState.CONNECTED }

    /** Per-relay connection states for the relay checker UI. */
    fun relayStates(): List<Pair<String, RelayState>> =
        connections.map { it.url to it.state.value }
}
