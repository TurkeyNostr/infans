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

package com.turkbot.babytracker.nostr.relay

import android.util.Log
import com.turkbot.babytracker.debug.DebugLogger as Dbg
import com.turkbot.babytracker.debug.DebugLogger.Category as Cat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.serialization.json.*

/**
 * Manages a single WebSocket connection to a Nostr relay.
 * Supports publishing events and subscribing to events with filters.
 *
 * Multiple relays are used in parallel for redundancy (like Runstr: damus, nos.lol, etc).
 */
class RelayConnection(
    val url: String,
    private val client: OkHttpClient
) {
    private var ws: WebSocket? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(RelayState.DISCONNECTED)
    val state: StateFlow<RelayState> = _state

    // Incoming events are pushed to this channel
    val events = Channel<NostrEventWrapper>(Channel.BUFFERED)

    // Pending subscription filters: subId → JSON filter
    private val subscriptions = mutableMapOf<String, String>()

    fun connect() {
        if (_state.value == RelayState.CONNECTED || _state.value == RelayState.CONNECTING) return
        _state.value = RelayState.CONNECTING

        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("Relay", "Connected to $url")
                _state.value = RelayState.CONNECTED
                // Resubscribe
                subscriptions.forEach { (subId, filter) ->
                    webSocket.send("""["REQ","$subId",$filter]""")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val arr = Json.parseToJsonElement(text).jsonArray
                    val type = arr[0].jsonPrimitive.content
                    when (type) {
                        "EVENT" -> {
                            val subId = arr[1].jsonPrimitive.content
                            val eventObj = arr[2].jsonObject
                            val event = RelayEvent.fromJsonObject(eventObj)
                            scope.launch { events.send(NostrEventWrapper(subId, event, url)) }
                        }
                        "OK" -> {
                            val eventId = arr[1].jsonPrimitive.content
                            val success = arr[2].jsonPrimitive.boolean
                            if (success) {
                                Dbg.info(Cat.RELAY, "Event accepted by $url")
                            } else {
                                val msg = arr.getOrNull(3)?.jsonPrimitive?.content
                                Log.w("Relay", "Event $eventId rejected by $url: $msg")
                                Dbg.warn(Cat.RELAY, "Event rejected by $url${if (msg != null) ": $msg" else ""}")
                            }
                        }
                        "EOSE" -> {
                            // End of stored events for subscription
                        }
                        "NOTICE" -> {
                            Log.d("Relay", "Notice from $url: ${arr.getOrNull(1)?.jsonPrimitive?.content}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Relay", "Parse error from $url: ${e.message}", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("Relay", "Disconnected from $url: $reason")
                _state.value = RelayState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("Relay", "Connection failure to $url: ${t.message}", t)
                _state.value = RelayState.ERROR
                // Auto-reconnect with delay
                scope.launch {
                    delay(5000)
                    connect()
                }
            }
        })
    }

    fun disconnect() {
        ws?.close(1000, "Goodbye")
        ws = null
        _state.value = RelayState.DISCONNECTED
    }

    /**
     * Publish a signed event to this relay.
     * If the WebSocket is not connected, the event is silently dropped —
     * we log a warning so the user can see in the debug log that the
     * publish never reached the relay.
     */
    fun publish(eventJson: String) {
        val w = ws
        if (w == null || _state.value != RelayState.CONNECTED) {
            Log.w("Relay", "Publish attempted on $url but not connected (state=${_state.value})")
            Dbg.warn(Cat.RELAY, "Publish to $url dropped — not connected")
            return
        }
        w.send("""["EVENT",$eventJson]""")
    }

    /**
     * Subscribe to events matching a filter.
     */
    fun subscribe(subId: String, filter: String) {
        subscriptions[subId] = filter
        ws?.send("""["REQ","$subId",$filter]""")
    }

    /**
     * Unsubscribe.
     */
    fun unsubscribe(subId: String) {
        subscriptions.remove(subId)
        ws?.send("""["CLOSE","$subId"]""")
    }

    /**
     * Snapshot of active subscription IDs + filters (for relay reconfiguration).
     */
    fun getActiveSubscriptions(): List<Pair<String, String>> = subscriptions.toList()
}

enum class RelayState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

data class NostrEventWrapper(
    val subscriptionId: String,
    val event: RelayEvent,
    val relayUrl: String
)
