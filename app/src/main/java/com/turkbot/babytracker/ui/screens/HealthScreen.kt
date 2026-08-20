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

package com.turkbot.babytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.HealthRecord
import com.turkbot.babytracker.ui.components.EditTimestampDialog
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(viewModel: BabyViewModel, onSaved: () -> Unit = {}) {
    val healthRecords by viewModel.healthRecords.collectAsState()

    var temperatureText by remember { mutableStateOf("") }
    var medicationText by remember { mutableStateOf("") }
    var doseText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<HealthRecord?>(null) }

    val todayRecords = remember(healthRecords) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        healthRecords.filter { it.time >= startOfDay }.sortedByDescending { it.time }
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
                        "Log Health Record",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = temperatureText,
                        onValueChange = {
                            temperatureText = it.filter { c -> c.isDigit() || c == '.' }
                            showError = false
                        },
                        label = { Text("Temperature (°C)") },
                        suffix = { Text("°C") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = medicationText,
                        onValueChange = {
                            medicationText = it
                            showError = false
                        },
                        label = { Text("Medication (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = doseText,
                        onValueChange = { doseText = it },
                        label = { Text("Dose (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showError) {
                        Text(
                            "Enter at least a temperature or medication",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val temp = temperatureText.toDoubleOrNull()
                            val med = medicationText.ifBlank { null }
                            if (temp == null && med == null) {
                                showError = true
                                return@Button
                            }
                            viewModel.addHealthRecord(
                                temperature = temp,
                                medication = med,
                                dose = doseText.ifBlank { null },
                                note = noteText.ifBlank { null }
                            )
                            temperatureText = ""
                            medicationText = ""
                            doseText = ""
                            noteText = ""
                            showError = false
                            onSaved()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Log Record")
                    }
                }
            }
        }

        // ── Today's records header ─────────────────────────
        item {
            Text(
                "Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (todayRecords.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        "No health records logged today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        } else {
            items(todayRecords, key = { it.id }) { record ->
                HealthRecordCard(
                    record = record,
                    onDelete = { viewModel.deleteHealthRecord(record.id) },
                    onEditTime = { editingRecord = record }
                )
            }
        }
    }
        // ── Edit timestamp dialog ──────────────────────────
        editingRecord?.let { record ->
            EditTimestampDialog(
                initialEpochMillis = record.time,
                onDismiss = { editingRecord = null },
                onSave = { newTime ->
                    viewModel.updateHealthRecordTime(record.id, newTime)
                    editingRecord = null
                }
            )
        }
    }
}

@Composable
private fun HealthRecordCard(
    record: HealthRecord,
    onDelete: () -> Unit,
    onEditTime: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val feverThreshold = 38.0
    val isFever = record.temperature != null && record.temperature >= feverThreshold

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Temperature (red if fever)
                if (record.temperature != null) {
                    Text(
                        String.format(Locale.getDefault(), "%.1f°C", record.temperature),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isFever) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
                // Medication + dose
                if (!record.medication.isNullOrBlank()) {
                    val medText = if (!record.dose.isNullOrBlank()) {
                        "${record.medication} · ${record.dose}"
                    } else {
                        record.medication
                    }
                    Text(
                        medText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Time
                Text(
                    timeFormat.format(Date(record.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Note
                if (!record.note.isNullOrBlank()) {
                    Text(
                        record.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
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
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
