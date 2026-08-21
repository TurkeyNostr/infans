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

package com.turkbot.babytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Sleep
import com.turkbot.babytracker.ui.components.EditTimestampDialog
import com.turkbot.babytracker.ui.components.LiveTimer
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(viewModel: BabyViewModel) {
    val sleeps by viewModel.sleeps.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var startText by remember {
        mutableStateOf(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis()))
        )
    }
    var hoursText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var editingSleep by remember { mutableStateOf<Sleep?>(null) }

    fun parseStartToEpoch(timeStr: String): Long {
        val today = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
            .substringBefore(" ")
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .parse("$today $timeStr")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Form card ──────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Log Sleep Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // ── Live timer ──
                    LiveTimer(
                        label = "Sleep",
                        alarmPresets = listOf(30, 45, 60, 90)
                    ) { minutes ->
                        viewModel.addSleep(
                            start = System.currentTimeMillis() - minutes * 60_000L,
                            duration = minutes,
                            note = null
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Or enter manually:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text("Start time (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { hoursText = it.filter { c -> c.isDigit() } },
                            label = { Text("Hours") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                            label = { Text("Minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val totalMinutes = (hoursText.toIntOrNull() ?: 0) * 60 + (minutesText.toIntOrNull() ?: 0)
                    Button(
                        onClick = {
                            if (totalMinutes > 0) {
                                viewModel.addSleep(
                                    start = parseStartToEpoch(startText),
                                    duration = totalMinutes,
                                    note = noteText.ifBlank { null }
                                )
                                hoursText = ""
                                minutesText = ""
                                noteText = ""
                                startText = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(System.currentTimeMillis()))
                            }
                        },
                        enabled = totalMinutes > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Bedtime, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Log Sleep")
                    }
                }
            }
        }

        // ── Sleep sessions list ────────────────────────────
        if (sleeps.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        "No sleep sessions logged yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        } else {
            items(sleeps, key = { it.id }) { sleep ->
                SleepCard(
                    sleep = sleep,
                    timeFormat = timeFormat,
                    onDelete = { viewModel.deleteSleep(sleep.id) },
                    onEditTime = { editingSleep = sleep }
                )
            }
        }
    }
        // ── Edit timestamp dialog ──────────────────────────
        editingSleep?.let { sleep ->
            EditTimestampDialog(
                initialEpochMillis = sleep.start,
                onDismiss = { editingSleep = null },
                onSave = { newTime ->
                    viewModel.updateSleepStart(sleep.id, newTime)
                    editingSleep = null
                }
            )
        }
    }
}

@Composable
private fun SleepCard(
    sleep: Sleep,
    timeFormat: SimpleDateFormat,
    onDelete: () -> Unit,
    onEditTime: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    timeFormat.format(Date(sleep.start)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    Units.fmtDuration(sleep.duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!sleep.note.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        sleep.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditTime) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit time",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete sleep session",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
