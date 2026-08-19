package com.turkbot.babytracker.nostr.relay

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

/**
 * Manages multiple relay connections in parallel.
 * Events from all relays are merged into a single flow.
 * Publishes go to all connected relays for redundancy.
 */
class RelayPool(
    relayUrls: List<String>,
    private val client: OkHttpClient
) {
    private val connections = relayUrls.map { RelayConnection(it, client) }

    private val _events = MutableSharedFlow<NostrEventWrapper>(extraBufferCapacity = 64)
    val events: SharedFlow<NostrEventWrapper> = _events

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Collect from all connections
    private var collectJob: Job? = null

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
        }
    }

    fun disconnect() {
        collectJob?.cancel()
        connections.forEach { it.disconnect() }
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
}
