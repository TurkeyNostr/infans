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

package com.turkbot.babytracker.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.ActiveSession
import com.turkbot.babytracker.reminder.ReminderScheduler
import kotlinx.coroutines.delay

/**
 * A live start/stop timer for breastfeeding and sleep sessions.
 * Shows elapsed time (HH:MM:SS) and a start/stop button.
 * On stop, calls [onStop] with the elapsed duration in minutes (rounded up to 1).
 *
 * If [alarmPresets] is non-empty, alarm preset chips are shown. Selecting a
 * preset and starting the timer schedules a background notification via
 * WorkManager — fires even if the app is not on screen.
 *
 * The timer survives navigation away and back, configuration changes
 * (rotation), and process death. The start timestamp is persisted to
 * SharedPreferences; elapsed time is always derived as now − startTime,
 * so it stays accurate regardless of how long the app was gone.
 *
 * ── Remote session sync ──
 *
 * If [remoteSession] is non-null, the timer shows as running with a
 * "Started by partner" badge. The local user can stop it — this calls
 * [onRemoteStop] instead of [onStop], so the ViewModel can notify the
 * partner via Nostr. When the partner stops the timer on their end, the
 * remoteSession becomes null and the timer clears automatically.
 *
 * A remote session takes priority over a local session — if both exist
 * (shouldn't happen in practice), the remote one is shown.
 *
 * @param remoteSession  session started by the partner, or null if none active
 * @param onStart        called when the user starts a local timer; receives the
 *                       start timestamp and selected alarm minutes so the
 *                       ViewModel can notify the partner via Nostr
 * @param onRemoteStop   called when the local user stops a partner-started timer;
 *                       receives the elapsed duration in minutes
 */
@Composable
fun LiveTimer(
    label: String,
    alarmPresets: List<Int> = emptyList(),
    remoteSession: ActiveSession? = null,
    onStop: (durationMinutes: Int) -> Unit,
    onStart: (startTime: Long, alarmMinutes: Int) -> Unit = { _, _ -> },
    onRemoteStop: (durationMinutes: Int) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("live_timer_prefs", Context.MODE_PRIVATE)
    }

    // Unique key per timer label so sleep and breast timers don't collide.
    val keyStart = "start_${label}"
    val keyAlarm = "alarm_${label}"

    // Restore persisted state on first composition.
    var startTime by remember {
        mutableLongStateOf(prefs.getLong(keyStart, 0L))
    }
    var alarmMinutes by remember {
        mutableStateOf(prefs.getInt(keyAlarm, 0))
    }
    // Running is derived: if startTime > 0, the timer is active.
    var localRunning by remember { mutableStateOf(startTime > 0L) }
    var elapsed by remember { mutableLongStateOf(0L) }

    // ── Remote session overrides local ──
    val isRemote = remoteSession != null
    val effectiveStartTime = remoteSession?.startTime ?: startTime
    val running = isRemote || localRunning

    // Tick every second while running (local or remote)
    LaunchedEffect(running, effectiveStartTime) {
        if (running) {
            while (true) {
                elapsed = System.currentTimeMillis() - effectiveStartTime
                delay(1000)
            }
        }
    }

    // When a remote session ends (partner stopped), clear local elapsed
    LaunchedEffect(remoteSession) {
        if (remoteSession == null && !localRunning) {
            elapsed = 0L
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "$label: ${formatElapsed(elapsed)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (running) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isRemote) {
                    Text(
                        text = "Started by partner",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (running) {
                Button(
                    onClick = {
                        val minutes = ((elapsed / 60000L).toInt()).coerceAtLeast(1)
                        if (isRemote) {
                            // Stopping a partner-started timer — notify partner
                            onRemoteStop(minutes)
                        } else {
                            // Stopping our own local timer
                            localRunning = false
                            onStop(minutes)
                            elapsed = 0L
                            startTime = 0L
                            // Clear persisted state
                            prefs.edit()
                                .remove(keyStart)
                                .remove(keyAlarm)
                                .apply()
                            // Cancel any pending alarm
                            if (alarmMinutes > 0) {
                                ReminderScheduler.cancelTimerAlarm(context)
                                alarmMinutes = 0
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop & Log")
                }
            } else {
                Button(
                    onClick = {
                        startTime = System.currentTimeMillis()
                        elapsed = 0L
                        localRunning = true
                        // Persist start time so timer survives process death
                        prefs.edit()
                            .putLong(keyStart, startTime)
                            .putInt(keyAlarm, alarmMinutes)
                            .apply()
                        // Schedule alarm if a preset is selected
                        if (alarmMinutes > 0) {
                            ReminderScheduler.scheduleTimerAlarm(context, label, alarmMinutes)
                        }
                        // Notify partner that a timer started
                        onStart(startTime, alarmMinutes)
                    }
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start Timer")
                }
            }
        }

        // ── Alarm presets (only shown for local timer, not remote) ──
        if (alarmPresets.isNotEmpty() && !isRemote) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    modifier = Modifier.height(16.dp).width(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Alarm:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                alarmPresets.forEach { mins ->
                    FilterChip(
                        selected = alarmMinutes == mins,
                        onClick = {
                            alarmMinutes = if (alarmMinutes == mins) 0 else mins
                        },
                        enabled = !localRunning,
                        label = { Text("${mins}m") }
                    )
                }
                if (alarmMinutes > 0 && localRunning) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "alarm at ${alarmMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
           else "%02d:%02d".format(m, s)
}
