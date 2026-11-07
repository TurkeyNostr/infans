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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.R
import com.turkbot.babytracker.data.entities.Feeding
import com.turkbot.babytracker.util.Units
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Unit tokens (first element) are data-layer identifiers stored in the DB;
// the display label is resolved from resources at composition time.
private val BOTTLE_UNITS = listOf("ml" to R.string.units_ml, "fl_oz" to R.string.units_fl_oz)
private val SOLID_UNITS = listOf("g" to R.string.units_g, "oz" to R.string.units_oz)
private val BREAST_SIDES = listOf("left", "right", "both")

/** Localized label for a breast side token ("left"/"right"/"both"). */
@Composable
private fun breastSideLabel(side: String): String = when (side) {
    "left" -> stringResource(R.string.side_left)
    "right" -> stringResource(R.string.side_right)
    else -> stringResource(R.string.side_both)
}

/**
 * Edit dialog for a feeding entry. Allows editing the quantity fields
 * (amount/unit for bottle+solids, side/duration for breast) and the note.
 * Timestamp editing is NOT included here — that's handled separately via
 * [EditTimestampDialog]. This dialog focuses on fixing wrong amounts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFeedingDialog(
    feeding: Feeding,
    onDismiss: () -> Unit,
    onSave: (amount: Double?, unit: String?, breastSide: String?, duration: Int?, note: String?) -> Unit
) {
    // Initialise fields from the existing feeding
    var amountText by remember { mutableStateOf(feeding.amount?.let { Units.fmtAmount(it, feeding.unit ?: "ml") } ?: "") }
    var selectedUnit by remember { mutableStateOf(feeding.unit ?: "ml") }
    var selectedSide by remember { mutableStateOf(feeding.breastSide ?: "left") }
    var durationText by remember { mutableStateOf(feeding.duration?.toString() ?: "") }
    var noteText by remember { mutableStateOf(feeding.note ?: "") }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.edit_feeding_title, Units.feedTypeLabel(feeding.type)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Conditional inputs per feeding type
                when (feeding.type) {
                    "bottle" -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text(stringResource(R.string.edit_feeding_amount)) },
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
                                    ) { Text(stringResource(label)) }
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
                                    Text(breastSideLabel(side))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.edit_feeding_duration)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "solids" -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text(stringResource(R.string.edit_feeding_amount)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            SingleChoiceSegmentedButtonRow {
                                SOLID_UNITS.forEachIndexed { i, (unit, label) ->
                                    SegmentedButton(
                                        selected = selectedUnit == unit,
                                        onClick = { selectedUnit = unit },
                                        shape = SegmentedButtonDefaults.itemShape(i, SOLID_UNITS.size)
                                    ) { Text(stringResource(label)) }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.edit_feeding_note_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.edit_feeding_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val amount = amountText.toDoubleOrNull()
                        val duration = durationText.toIntOrNull()
                        val unit = when (feeding.type) {
                            "bottle" -> selectedUnit
                            "solids" -> selectedUnit
                            else -> null
                        }
                        onSave(
                            amount,
                            unit,
                            if (feeding.type == "breast") selectedSide else null,
                            duration,
                            noteText.ifBlank { null }
                        )
                    }) {
                        Text(stringResource(R.string.edit_feeding_save))
                    }
                }
            }
        }
    }
}
