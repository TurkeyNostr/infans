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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Feeding
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import com.turkbot.babytracker.util.UnitPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FEED_TYPES = listOf("bottle" to "Bottle", "breast" to "Breast", "solids" to "Solids")
private val BOTTLE_UNITS = listOf("ml" to "ml", "fl_oz" to "fl oz")
private val BREAST_SIDES = listOf("left", "right", "both")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: BabyViewModel) {
    val feedings by viewModel.feedings.collectAsState()

    var selectedType by remember { mutableStateOf("bottle") }
    var amountText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedUnit by remember { mutableStateOf(UnitPreferences.defaultFeedUnit(context)) }
    var selectedSide by remember { mutableStateOf("left") }
    var durationText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val todayFeedings = remember(feedings) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        feedings.filter { it.time >= startOfDay }.sortedByDescending { it.time }
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
                        "Log Feeding",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Segmented button for feed type
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        FEED_TYPES.forEachIndexed { index, (type, label) ->
                            SegmentedButton(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    amountText = ""
                                    durationText = ""
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, FEED_TYPES.size)
                            ) {
                                Text(label)
                            }
                        }
                    }

                    // Conditional inputs per type
                    when (selectedType) {
                        "bottle" -> {
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
                                    BOTTLE_UNITS.forEachIndexed { i, (unit, label) ->
                                        SegmentedButton(
                                            selected = selectedUnit == unit,
                                            onClick = { selectedUnit = unit },
                                            shape = SegmentedButtonDefaults.itemShape(i, BOTTLE_UNITS.size)
                                        ) { Text(label) }
                                    }
                                }
                            }
                        }

                        "breast" -> {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                BREAST_SIDES.forEachIndexed { index, side ->
                                    SegmentedButton(
                                        selected = selectedSide == side,
                                        onClick = { selectedSide = side },
                                        shape = SegmentedButtonDefaults.itemShape(index, BREAST_SIDES.size)
                                    ) {
                                        Text(side.replaceFirstChar { it.uppercase() })
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = durationText,
                                onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                                label = { Text("Duration (minutes)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        "solids" -> {
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Amount (g)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
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
                            val amount = amountText.toDoubleOrNull()
                            val duration = durationText.toIntOrNull()
                            val unit = when (selectedType) {
                                "bottle" -> selectedUnit
                                "solids" -> "g"
                                else -> null
                            }
                            viewModel.addFeeding(
                                type = selectedType,
                                amount = amount,
                                unit = unit,
                                breastSide = if (selectedType == "breast") selectedSide else null,
                                duration = duration,
                                note = noteText.ifBlank { null }
                            )
                            amountText = ""
                            durationText = ""
                            noteText = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Log Feeding")
                    }
                }
            }
        }

        // ── Today's feedings header ────────────────────────
        item {
            Text(
                "Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (todayFeedings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        "No feedings logged today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        } else {
            items(todayFeedings, key = { it.id }) { feeding ->
                FeedingCard(
                    feeding = feeding,
                    onDelete = { viewModel.deleteFeeding(feeding.id) }
                )
            }
        }
    }
}

@Composable
private fun FeedingCard(
    feeding: Feeding,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val displayAmount = when (feeding.type) {
        "breast" -> {
            val side = feeding.breastSide?.replaceFirstChar { it.uppercase() } ?: ""
            val dur = feeding.duration?.let { Units.fmtAmount(it.toDouble(), "min") } ?: ""
            "$side · $dur"
        }
        else -> {
            val amt = feeding.amount
            val unit = feeding.unit
            if (amt != null && unit != null) Units.fmtAmount(amt, unit) else ""
        }
    }

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
                    Units.feedTypeLabel(feeding.type),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (displayAmount.isNotBlank()) {
                    Text(
                        displayAmount,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    timeFormat.format(Date(feeding.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!feeding.note.isNullOrBlank()) {
                    Text(
                        feeding.note,
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
