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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A live start/stop timer for breastfeeding and sleep sessions.
 * Shows elapsed time (HH:MM:SS) and a start/stop button.
 * On stop, calls [onStop] with the elapsed duration in minutes (rounded up to 1).
 */
@Composable
fun LiveTimer(
    label: String,
    onStop: (durationMinutes: Int) -> Unit
) {
    var running by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }

    // Tick every second while running
    LaunchedEffect(running) {
        while (running) {
            elapsed = System.currentTimeMillis() - startTime
            delay(1000)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ${formatElapsed(elapsed)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
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
                }
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Start Timer")
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
