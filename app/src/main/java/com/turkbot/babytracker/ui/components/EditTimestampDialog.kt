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

package com.turkbot.babytracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * A dialog that lets the user pick a new date + time for an event.
 *
 * @param initialEpochMillis the current timestamp being edited
 * @param onDismiss called when the dialog is cancelled
 * @param onSave called with the new epoch millis when the user confirms
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTimestampDialog(
    initialEpochMillis: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    // Initialise pickers from the existing timestamp
    val initialCal = remember { Calendar.getInstance().apply { timeInMillis = initialEpochMillis } }

    val datePickerState = remember {
        DatePickerState(
            initialSelectedDateMillis = initialEpochMillis,
            initialDisplayMode = DisplayMode.Picker,
            locale = Locale.getDefault()
        )
    }
    val timePickerState = remember {
        TimePickerState(
            initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = initialCal.get(Calendar.MINUTE),
            is24Hour = true
        )
    }

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
                    "Edit Date & Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // ── Date picker ────────────────────────────────
                Text(
                    "Date",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                DatePicker(
                    state = datePickerState,
                    showModeToggle = false,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ── Time picker ────────────────────────────────
                Text(
                    "Time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(
                        state = timePickerState,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // ── Buttons ────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        // Combine selected date + time into epoch millis.
                        // DatePicker.selectedDateMillis is UTC midnight for the
                        // selected calendar date. Extract the date parts in UTC
                        // (so the user's selection is preserved), then combine
                        // with the time-picker hour/minute in the local timezone.
                        val dateMillis = datePickerState.selectedDateMillis
                        if (dateMillis != null) {
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            utcCal.timeInMillis = dateMillis
                            val cal = Calendar.getInstance().apply {
                                clear()
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE, timePickerState.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onSave(cal.timeInMillis)
                        }
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
