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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Weight
import com.turkbot.babytracker.ui.components.EditTimestampDialog
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import com.turkbot.babytracker.util.UnitPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(viewModel: BabyViewModel) {
    val weights by viewModel.weights.collectAsState()
    var weightInput by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var weightUnit by remember { mutableStateOf(UnitPreferences.defaultWeightUnit(context)) }
    var heightInput by remember { mutableStateOf("") }
    var heightUnit by remember { mutableStateOf(UnitPreferences.defaultHeightUnit(context)) }
    var headCircInput by remember { mutableStateOf("") }
    var headCircUnit by remember { mutableStateOf(UnitPreferences.defaultHeightUnit(context)) }
    var editingWeight by remember { mutableStateOf<Weight?>(null) }

    val weightUnits = listOf("kg", "lb", "oz")
    val heightUnits = listOf("cm", "in")

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
                        "Log Measurement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Weight value + unit segmented buttons
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        weightUnits.forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = weightUnit == unit,
                                onClick = { weightUnit = unit },
                                shape = SegmentedButtonDefaults.itemShape(index, weightUnits.size)
                            ) { Text(unit) }
                        }
                    }

                    // Height (optional) + unit segmented buttons
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it },
                        label = { Text("Height (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        heightUnits.forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = heightUnit == unit,
                                onClick = { heightUnit = unit },
                                shape = SegmentedButtonDefaults.itemShape(index, heightUnits.size)
                            ) { Text(unit) }
                        }
                    }

                    // Head circumference (optional) + unit segmented buttons
                    OutlinedTextField(
                        value = headCircInput,
                        onValueChange = { headCircInput = it },
                        label = { Text("Head Circ. (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        heightUnits.forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = headCircUnit == unit,
                                onClick = { headCircUnit = unit },
                                shape = SegmentedButtonDefaults.itemShape(index, heightUnits.size)
                            ) { Text(unit) }
                        }
                    }

                    Button(
                        onClick = {
                            val weightValue = weightInput.trim().toDoubleOrNull()
                            if (weightValue != null) {
                                val valueKg = Units.toKg(weightValue, weightUnit)
                                val heightCm: Double? = heightInput.trim().toDoubleOrNull()?.let {
                                    Units.toCm(it, heightUnit)
                                }
                                val hUnit = if (heightCm != null) heightUnit else null
                                val headCircCm: Double? = headCircInput.trim().toDoubleOrNull()?.let {
                                    Units.toCm(it, headCircUnit)
                                }
                                val hcUnit = if (headCircCm != null) headCircUnit else null
                                viewModel.addWeight(valueKg, weightUnit, heightCm, hUnit, headCircCm, hcUnit)
                                weightInput = ""
                                heightInput = ""
                                headCircInput = ""
                            }
                        },
                        enabled = weightInput.trim().toDoubleOrNull() != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MonitorWeight, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Measurement")
                    }
                }
            }
        }

        // ── Weight history ─────────────────────────────────
        if (weights.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        "No weight measurements recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        } else {
            items(weights, key = { it.id }) { weight ->
                WeightCard(
                    weight = weight,
                    onDelete = { viewModel.deleteWeight(weight.id) },
                    onEditTime = { editingWeight = weight }
                )
            }
        }
    }
        // ── Edit timestamp dialog ──────────────────────────
        editingWeight?.let { weight ->
            EditTimestampDialog(
                initialEpochMillis = weight.date,
                onDismiss = { editingWeight = null },
                onSave = { newTime ->
                    viewModel.updateWeightDate(weight.id, newTime)
                    editingWeight = null
                }
            )
        }
    }
}

@Composable
private fun WeightCard(
    weight: Weight,
    onDelete: () -> Unit,
    onEditTime: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val formattedDate = remember(weight.date) {
        dateFormat.format(Date(weight.date))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MonitorWeight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    Units.fromKg(weight.value, weight.unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (weight.height != null && weight.heightUnit != null) {
                    Text(
                        "Height: ${Units.fmtHeight(weight.height, weight.heightUnit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (weight.headCirc != null && weight.headCircUnit != null) {
                    Text(
                        "Head: ${Units.fmtHeight(weight.headCirc, weight.headCircUnit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditTime) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit date",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete weight measurement",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
