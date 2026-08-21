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

package com.turkbot.babytracker.nostr.nip05

import android.util.Log
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import com.turkbot.babytracker.nostr.events.NostrEvent
import com.turkbot.babytracker.nostr.relay.RelayPool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * NIP-05 resolution: convert between NIP-05 identifiers (name@domain) and npubs.
 *
 * Two operations:
 *   1. [resolve] — DNS lookup: GET https://domain/.well-known/nostr.json?name=name
 *      Returns the hex pubkey for the identifier, or null if not found.
 *
 *   2. [fetchNip05] — Relay lookup: fetches the user's kind 0 metadata event and
 *      extracts the nip05 field. Used to display a human-readable identifier for
 *      an npub we already know.
 *
 * Privacy note: [resolve] hits a third-party web server which sees your IP and the
 * identifier being looked up. [fetchNip05] uses Nostr relays only (no extra leak).
 */
class Nip05Resolver(
    private val httpClient: OkHttpClient,
    private val relayPool: RelayPool
) {
    companion object {
        private const val TAG = "Nip05"
        private var subCounter = 0
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Check whether a string looks like a NIP-05 identifier (contains '@' and
     * does not start with 'npub1').
     */
    fun isNip05(input: String): Boolean {
        return input.contains("@") && !input.startsWith("npub1")
    }

    /**
     * Resolve a NIP-05 identifier (name@domain) to an npub.
     *
     * Performs a GET to https://domain/.well-known/nostr.json?name=name
     * and matches the returned pubkey against the identifier.
     *
     * @return npub string, or null if resolution failed / pubkey mismatch
     */
    suspend fun resolve(identifier: String): String? = withContext(Dispatchers.IO) {
        val parts = identifier.trim().split("@", limit = 2)
        if (parts.size != 2) {
            Log.w(TAG, "Invalid NIP-05 format: $identifier")
            return@withContext null
        }
        val name = parts[0]
        val domain = parts[1]

        val url = "https://$domain/.well-known/nostr.json?name=$name"
        Log.d(TAG, "Resolving NIP-05: $identifier")

        try {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "NIP-05 lookup failed: HTTP ${response.code}")
                return@withContext null
            }

            val body = response.body?.string()
            response.close()
            if (body.isNullOrBlank()) return@withContext null

            val jsonObj = json.parseToJsonElement(body).jsonObject
            val namesMap = jsonObj["names"]?.jsonObject
            val hexPubkey = namesMap?.get(name)?.jsonPrimitive?.contentOrNull
            if (hexPubkey.isNullOrBlank()) {
                Log.w(TAG, "NIP-05: name '$name' not found in response")
                return@withContext null
            }

            // Convert hex pubkey to npub
            val pubBytes = NostrKeys.fromHex(hexPubkey!!)
            val npub = NostrKeys.encodeNpub(pubBytes)
            Log.d(TAG, "NIP-05 resolved: $identifier → ${npub.take(20)}...")
            npub
        } catch (e: Exception) {
            Log.e(TAG, "NIP-05 resolution error for $identifier", e)
            null
        }
    }

    /**
     * Fetch a pubkey's NIP-05 identifier from their kind 0 metadata event
     * on Nostr relays.
     *
     * Subscribes to kind 0 events from the given author, parses the content
     * JSON for the "nip05" field.
     *
     * @param pubkeyHex The hex pubkey to look up
     * @return NIP-05 identifier string (name@domain), or null if not found
     */
    suspend fun fetchNip05(pubkeyHex: String): String? = withTimeoutOrNull(10_000) {
        val filter = """{"kinds":[0],"authors":["$pubkeyHex"],"limit":1}"""
        val deferred = CompletableDeferred<String?>()

        // Use a unique subId per call so concurrent fetchNip05 calls (e.g.
        // refreshMyNip05 + refreshPartnerNip05 during init) don't overwrite
        // each other's subscription. Without this, the first kind-0 event
        // received completes whichever deferred is waiting — your partner's
        // NIP-05 could be stored as yours.
        val subId = "nip05_${subCounter++}"

        // Start collector BEFORE subscribing so we don't miss the event.
        val job = scope.launch {
            relayPool.events.collect { wrapper ->
                if (wrapper.subscriptionId == subId && wrapper.event.kind == 0) {
                    // Verify the event author matches the requested pubkey —
                    // a relay could return a kind-0 from a different author.
                    if (wrapper.event.pubkey != pubkeyHex) return@collect
                    try {
                        val metadata = json.parseToJsonElement(wrapper.event.content).jsonObject
                        val nip05 = metadata["nip05"]?.jsonPrimitive?.contentOrNull
                        if (!nip05.isNullOrBlank()) {
                            deferred.complete(nip05)
                        } else {
                            deferred.complete(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse kind 0 metadata", e)
                        deferred.complete(null)
                    }
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
    }
}
