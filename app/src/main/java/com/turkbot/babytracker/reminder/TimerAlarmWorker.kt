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

package com.turkbot.babytracker.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * One-time worker that fires a high-priority timer alarm notification
 * with sound and vibration. Scheduled by [ReminderScheduler.scheduleTimerAlarm].
 */
class TimerAlarmWorker(
    context: Context,
    workerParams: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "baby_tracker_timer_alarm"
        const val CHANNEL_NAME = "Infans Timer Alarms"
        const val WORK_NAME = "timer_alarm"
        const val KEY_LABEL = "timer_label"
        const val KEY_MINUTES = "timer_minutes"
        const val NOTIF_ID = 2001
    }

    override suspend fun doWork(): Result {
        val label = inputData.getString(KEY_LABEL) ?: "Timer"
        val minutes = inputData.getInt(KEY_MINUTES, 0)
        showNotification(label, minutes)
        vibrate()
        return Result.success()
    }

    private fun showNotification(label: String, minutes: Int) {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ $label Timer")
            .setContentText("$minutes minutes are up!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, notification)
    }

    private fun vibrate() {
        val vibrator = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Pattern: wait 0, vibrate 400ms, pause 200, vibrate 400, pause 200, vibrate 400
            val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 400, 200, 400, 200, 400), -1)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarms for breastfeeding and sleep timers"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
