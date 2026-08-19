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

package com.turkbot.babytracker.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that fires a "time to feed" reminder notification.
 * The interval is configured via input data (key "interval_minutes").
 */
class FeedingReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "baby_tracker_reminders"
        const val CHANNEL_NAME = "Infans Reminders"
        const val KEY_INTERVAL_MIN = "interval_minutes"
        const val WORK_NAME = "feeding_reminder"
    }

    override suspend fun doWork(): Result {
        val interval = inputData.getInt(KEY_INTERVAL_MIN, 150) // default 2.5h
        showNotification(interval)
        return Result.success()
    }

    private fun showNotification(intervalMin: Int) {
        val hours = intervalMin / 60
        val mins = intervalMin % 60
        val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🍼 Feeding Reminder")
            .setContentText("It's been $timeStr since the last feeding — time to feed baby?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }
}

/**
 * Manages scheduling and cancelling the periodic feeding reminder.
 */
object ReminderScheduler {

    /**
     * Schedule a periodic feeding reminder at the given interval (in minutes).
     * Replaces any existing reminder with the new interval.
     */
    fun schedule(context: Context, intervalMinutes: Int) {
        if (intervalMinutes <= 0) {
            cancel(context)
            return
        }

        ensureChannel(context)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()

        val request = PeriodicWorkRequestBuilder<FeedingReminderWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(workDataOf(FeedingReminderWorker.KEY_INTERVAL_MIN to intervalMinutes))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FeedingReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Cancel the feeding reminder.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(FeedingReminderWorker.WORK_NAME)
    }

    /**
     * Create the notification channel (required on API 26+).
     */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FeedingReminderWorker.CHANNEL_ID,
                FeedingReminderWorker.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to feed and care for baby"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
