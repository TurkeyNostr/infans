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

package com.turkbot.babytracker

import android.app.Application
import com.turkbot.babytracker.nostr.NostrManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class — initializes Nostr manager on startup.
 */
class BabyTrackerApp : Application() {
    lateinit var nostrManager: NostrManager
        private set

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        nostrManager = NostrManager(this)
        // Load stored identity (local key or Amber npub) and connect to relays
        appScope.launch { nostrManager.initialize() }
    }
}
