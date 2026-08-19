package com.turkbot.babytracker.nostr.relay

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps relay connections alive for incoming DMs
 * even when the app is backgrounded (same pattern as nospeak).
 */
class RelaySyncService : Service() {

    private var pool: RelayPool? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Baby Tracker — syncing"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Relay pool is managed by NostrManager — this service just keeps the process alive
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Baby Tracker Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Nostr relay connection alive for messages"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Baby Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "baby_tracker_sync"
        private const val NOTIF_ID = 1
    }
}
