package com.turkbot.babytracker.nostr.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restart the relay sync service after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, RelaySyncService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
