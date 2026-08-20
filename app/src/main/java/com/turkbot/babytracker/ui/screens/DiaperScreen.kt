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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Diaper
import com.turkbot.babytracker.ui.components.EditTimestampDialog
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DIAPER_CONTENTS = listOf(
    "wet" to "Wet",
    "dirty" to "Dirty",
    "mixed" to "Mixed",
    "dry" to "Dry"
)
private val DIAPER_COLORS = listOf(
    "yellow" to "Yellow",
    "brown" to "Brown",
    "green" to "Green",
    "black" to "Black"
)

private fun contentsLabel(contents: String): String = when (contents) {
    "wet" -> "💧 Wet"
    "dirty" -> "💩 Dirty"
    "mixed" -> "💩💧 Mixed"
    "dry" -> " Dry"
    else -> contents.replaceFirstChar { it.uppercase() }
}

private fun colorLabel(color: String): String = when (color) {
    "yellow" -> "Yellow"
    "brown" -> "Brown"
    "green" -> "Green"
    "black" -> "Black"
    else -> color
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperScreen(viewModel: BabyViewModel, onSaved: () -> Unit = {}) {
    val diapers by viewModel.diapers.collectAsState()

    var selectedContents by remember { mutableStateOf("wet") }
    var selectedColor by remember { mutableStateOf("yellow") }
    var colorExpanded by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var editingDiaper by remember { mutableStateOf<Diaper?>(null) }

    val todayDiapers = remember(diapers) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        diapers.filter { it.time >= startOfDay }.sortedByDescending { it.time }
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
                        "Log Diaper Change",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // FilterChips for contents type (4 options)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DIAPER_CONTENTS.forEach { (type, label) ->
                            FilterChip(
                                selected = selectedContents == type,
                                onClick = { selectedContents = type },
                                label = { Text(label) }
                            )
                        }
                    }

                    // Color dropdown — only for dirty or mixed
                    if (selectedContents == "dirty" || selectedContents == "mixed") {
                        ExposedDropdownMenuBox(
                            expanded = colorExpanded,
                            onExpandedChange = { colorExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = colorLabel(selectedColor),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Color") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = colorExpanded
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = colorExpanded,
                                onDismissRequest = { colorExpanded = false }
                            ) {
                                DIAPER_COLORS.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedColor = value
                                            colorExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val color = if (selectedContents == "dirty" || selectedContents == "mixed") {
                                selectedColor
                            } else {
                                null
                            }
                            viewModel.addDiaper(
                                contents = selectedContents,
                                color = color,
                                note = noteText.ifBlank { null }
                            )
                            noteText = ""
                            onSaved()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Spa, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Log Diaper")
                    }
                }
            }
        }

        // ── Today's diapers header ─────────────────────────
        item {
            Text(
                "Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (todayDiapers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        "No diaper changes logged today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        } else {
            items(todayDiapers, key = { it.id }) { diaper ->
                DiaperCard(
                    diaper = diaper,
                    onDelete = { viewModel.deleteDiaper(diaper.id) },
                    onEditTime = { editingDiaper = diaper }
                )
            }
        }
    }
        // ── Edit timestamp dialog ──────────────────────────
        editingDiaper?.let { diaper ->
            EditTimestampDialog(
                initialEpochMillis = diaper.time,
                onDismiss = { editingDiaper = null },
                onSave = { newTime ->
                    viewModel.updateDiaperTime(diaper.id, newTime)
                    editingDiaper = null
                }
            )
        }
    }
}

@Composable
private fun DiaperCard(
    diaper: Diaper,
    onDelete: () -> Unit,
    onEditTime: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

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
                Text(
                    contentsLabel(diaper.contents),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!diaper.color.isNullOrBlank()) {
                    Text(
                        colorLabel(diaper.color),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    timeFormat.format(Date(diaper.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!diaper.note.isNullOrBlank()) {
                    Text(
                        diaper.note,
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
