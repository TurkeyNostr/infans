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
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Pumping
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import com.turkbot.babytracker.util.UnitPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PUMP_UNITS = listOf("ml" to "ml", "fl_oz" to "fl oz")
private val PUMP_SIDES = listOf("left", "right", "both")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpingScreen(viewModel: BabyViewModel, onSaved: () -> Unit = {}) {
    val pumpings by viewModel.pumpings.collectAsState()

    var amountText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedUnit by remember { mutableStateOf(UnitPreferences.defaultPumpUnit(context)) }
    var selectedSide by remember { mutableStateOf("left") }
    var durationText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val todayPumpings = remember(pumpings) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        pumpings.filter { it.time >= startOfDay }.sortedByDescending { it.time }
    }

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
                        "Log Pumping Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Amount + unit segmented buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Amount") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        SingleChoiceSegmentedButtonRow {
                            PUMP_UNITS.forEachIndexed { i, (unit, label) ->
                                SegmentedButton(
                                    selected = selectedUnit == unit,
                                    onClick = { selectedUnit = unit },
                                    shape = SegmentedButtonDefaults.itemShape(i, PUMP_UNITS.size)
                                ) { Text(label) }
                            }
                        }
                    }

                    // Side segmented buttons
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        PUMP_SIDES.forEachIndexed { index, side ->
                            SegmentedButton(
                                selected = selectedSide == side,
                                onClick = { selectedSide = side },
                                shape = SegmentedButtonDefaults.itemShape(index, PUMP_SIDES.size)
                            ) {
                                Text(side.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }

                    // Duration (optional, minutes)
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                        label = { Text("Duration (minutes)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull()
                            if (amount != null) {
                                val amountMl = if (selectedUnit == "fl_oz") {
                                    Units.flOzToMl(amount)
                                } else {
                                    amount
                                }
                                val duration = durationText.toIntOrNull()
                                viewModel.addPumping(
                                    amountMl = amountMl,
                                    unit = selectedUnit,
                                    duration = duration,
                                    side = selectedSide,
                                    note = noteText.ifBlank { null }
                                )
                                amountText = ""
                                durationText = ""
                                noteText = ""
                                onSaved()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Log Pumping")
                    }
                }
            }
        }

        // ── Today's pumpings header ─────────────────────────
        item {
            Text(
                "Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (todayPumpings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        "No pumping sessions logged today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        } else {
            items(todayPumpings, key = { it.id }) { pumping ->
                PumpingCard(
                    pumping = pumping,
                    onDelete = { viewModel.deletePumping(pumping.id) }
                )
            }
        }
    }
}

@Composable
private fun PumpingCard(
    pumping: Pumping,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Convert stored ml to display unit
    val displayAmount = when (pumping.unit) {
        "fl_oz" -> Units.fmtAmount(Units.mlToFlOz(pumping.amount), "fl_oz")
        else -> Units.fmtAmount(pumping.amount, "ml")
    }
    val sideLabel = pumping.side?.replaceFirstChar { it.uppercase() } ?: ""
    val durationLabel = pumping.duration?.let { Units.fmtAmount(it.toDouble(), "min") } ?: ""

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
                    displayAmount,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                val subtitleParts = listOfNotNull(
                    sideLabel.ifBlank { null },
                    durationLabel.ifBlank { null }
                )
                if (subtitleParts.isNotEmpty()) {
                    Text(
                        subtitleParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    timeFormat.format(Date(pumping.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!pumping.note.isNullOrBlank()) {
                    Text(
                        pumping.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
