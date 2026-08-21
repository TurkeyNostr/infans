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
 */
@Composable
fun LiveTimer(
    label: String,
    alarmPresets: List<Int> = emptyList(),
    onStop: (durationMinutes: Int) -> Unit
) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var alarmMinutes by remember { mutableStateOf(0) }

    // Tick every second while running
    LaunchedEffect(running) {
        while (running) {
            elapsed = System.currentTimeMillis() - startTime
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ${formatElapsed(elapsed)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            if (running) {
                Button(
                    onClick = {
                        running = false
                        val minutes = ((elapsed / 60000L).toInt()).coerceAtLeast(1)
                        onStop(minutes)
                        elapsed = 0L
                        startTime = 0L
                        // Cancel any pending alarm
                        if (alarmMinutes > 0) {
                            ReminderScheduler.cancelTimerAlarm(context)
                            alarmMinutes = 0
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
                        running = true
                        // Schedule alarm if a preset is selected
                        if (alarmMinutes > 0) {
                            ReminderScheduler.scheduleTimerAlarm(context, label, alarmMinutes)
                        }
                    }
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start Timer")
                }
            }
        }

        // ── Alarm presets ──
        if (alarmPresets.isNotEmpty()) {
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
                        enabled = !running,
                        label = { Text("${mins}m") }
                    )
                }
                if (alarmMinutes > 0 && running) {
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
