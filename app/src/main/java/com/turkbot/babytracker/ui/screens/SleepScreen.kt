package com.turkbot.babytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Sleep
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

    // ── Form state ───────────────────────────────────
    var startText by remember {
        mutableStateOf(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis()))
        )
    }
    var hoursText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ── Form Card ─────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Log Sleep Session",
                    style = MaterialTheme.typography.titleMedium
                )

                // Start time field (pre-filled with current time)
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("Start time (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Duration: hours + minutes side by side
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

                // Optional note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val h = hoursText.toIntOrNull() ?: 0
                        val m = minutesText.toIntOrNull() ?: 0
                        val totalMinutes = h * 60 + m
                        if (totalMinutes > 0) {
                            viewModel.addSleep(
                                start = parseStartToEpoch(startText),
                                duration = totalMinutes,
                                note = noteText.ifBlank { null }
                            )
                            // Reset form
                            hoursText = ""
                            minutesText = ""
                            noteText = ""
                            startText = SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(System.currentTimeMillis()))
                        }
                    },
                    enabled = (hoursText.toIntOrNull() ?: 0) * 60 + (minutesText.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Log Sleep")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Sleep sessions list ────────────────────────
        if (sleeps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No sleep sessions logged yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sleeps, key = { it.id }) { sleep ->
                    SleepCard(
                        sleep = sleep,
                        timeFormat = timeFormat,
                        onDelete = { viewModel.deleteSleep(sleep.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepCard(
    sleep: Sleep,
    timeFormat: SimpleDateFormat,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeFormat.format(Date(sleep.start)),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = Units.fmtDuration(sleep.duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!sleep.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = sleep.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete sleep session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
