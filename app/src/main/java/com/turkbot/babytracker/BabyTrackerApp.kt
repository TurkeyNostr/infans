package com.turkbot.babytracker

import android.app.Application
import com.turkbot.babytracker.nostr.NostrManager

/**
 * Application class — initializes Nostr manager on startup.
 */
class BabyTrackerApp : Application() {
    lateinit var nostrManager: NostrManager
        private set

    override fun onCreate() {
        super.onCreate()
        nostrManager = NostrManager(this)
    }
}
