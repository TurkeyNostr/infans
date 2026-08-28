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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Vaccine
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.HapticController
import com.turkbot.babytracker.util.Units
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Common vaccine types for the dropdown
private val VACCINE_TYPES = listOf(
    "BCG", "Hepatitis B", "DTaP", "Polio (IPV)", "Hib",
    "Rotavirus", "Pneumococcal (PCV)", "MMR", "Varicella",
    "Hepatitis A", "HPV", "Meningococcal", "Influenza", "Other"
)

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineSection(viewModel: BabyViewModel) {
    val vaccines by viewModel.vaccines.collectAsState()
    val context = LocalContext.current
    val signer by viewModel.signer.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingVaccine by remember { mutableStateOf<Vaccine?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        // ── Header + add button ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Vaccine Records",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Vaccines, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text(" Add Vaccine")
                }
            }
        }

        // ── Upcoming vaccines ──
        val now = System.currentTimeMillis()
        val upcoming = vaccines.filter { it.nextDueDate != null && it.nextDueDate > now }
            .sortedBy { it.nextDueDate }
        val past = vaccines.filter { it.nextDueDate == null || it.nextDueDate <= now }
            .sortedByDescending { it.dateAdministered }

        if (upcoming.isNotEmpty()) {
            item {
                Text(
                    "Upcoming",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            items(upcoming, key = { it.id }) { vaccine ->
                VaccineCard(
                    vaccine = vaccine,
                    myPubkeyHex = signer?.pubkeyHex,
                    isUpcoming = true,
                    onDelete = { viewModel.deleteVaccine(vaccine.id) },
                    onEditDate = { editingVaccine = vaccine }
                )
            }
        }

        if (past.isNotEmpty()) {
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (upcoming.isNotEmpty()) 8.dp else 0.dp)
                )
            }
            items(past, key = { it.id }) { vaccine ->
                VaccineCard(
                    vaccine = vaccine,
                    myPubkeyHex = signer?.pubkeyHex,
                    isUpcoming = false,
                    onDelete = { viewModel.deleteVaccine(vaccine.id) },
                    onEditDate = { editingVaccine = vaccine }
                )
            }
        }

        if (vaccines.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        "No vaccine records yet. Tap \"Add Vaccine\" to log one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        }
    }

    // ── Add vaccine dialog ──
    if (showAddDialog) {
        AddVaccineDialog(
            onDismiss = { showAddDialog = false },
            onSave = { type, dateMs, nextMs, dose, note ->
                viewModel.addVaccine(type, dateMs, nextMs, dose, note)
                HapticController.click(context)
                showAddDialog = false
            }
        )
    }

    // ── Edit date dialog ──
    editingVaccine?.let { vaccine ->
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = vaccine.dateAdministered
        )
        DatePickerDialog(
            onDismissRequest = { editingVaccine = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        viewModel.updateVaccineDate(vaccine.id, it)
                    }
                    editingVaccine = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingVaccine = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun VaccineCard(
    vaccine: Vaccine,
    myPubkeyHex: String?,
    isUpcoming: Boolean,
    onDelete: () -> Unit,
    onEditDate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUpcoming)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    vaccine.vaccineType +
                        (vaccine.doseNumber?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Given: ${dateFormat.format(Date(vaccine.dateAdministered))}" +
                        (Units.fmtAuthor(vaccine.authorPubkey, myPubkeyHex)?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (vaccine.nextDueDate != null) {
                    val dueText = if (isUpcoming) "Next due: " else "Next was due: "
                    Text(
                        dueText + dateFormat.format(Date(vaccine.nextDueDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUpcoming)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!vaccine.note.isNullOrBlank()) {
                    Text(
                        vaccine.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditDate) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit date",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVaccineDialog(
    onDismiss: () -> Unit,
    onSave: (type: String, dateMs: Long, nextMs: Long?, dose: String?, note: String?) -> Unit
) {
    var selectedType by remember { mutableStateOf(VACCINE_TYPES[0]) }
    var doseText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    val administeredDateState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    var showNextDatePicker by remember { mutableStateOf(false) }
    var nextDateMs by remember { mutableStateOf<Long?>(null) }

    if (showNextDatePicker) {
        val nextDateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showNextDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    nextDateMs = nextDateState.selectedDateMillis
                    showNextDatePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showNextDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = nextDateState)
        }
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val dateMs = administeredDateState.selectedDateMillis ?: System.currentTimeMillis()
                    onSave(selectedType, dateMs, nextDateMs, doseText.ifBlank { null }, noteText.ifBlank { null })
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Vaccine Record", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            // Vaccine type dropdown — using OutlinedTextField for simplicity
            OutlinedTextField(
                value = selectedType,
                onValueChange = { selectedType = it },
                label = { Text("Vaccine type") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Quick-pick chips for common types
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                VACCINE_TYPES.take(5).forEach { type ->
                    TextButton(
                        onClick = { selectedType = type },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(type, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text("Date Administered", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            DatePicker(state = administeredDateState)

            // Next due date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Next Due Date", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showNextDatePicker = true }) {
                    Text(nextDateMs?.let { dateFormat.format(Date(it)) } ?: "Set date")
                }
                if (nextDateMs != null) {
                    TextButton(onClick = { nextDateMs = null }) { Text("Clear") }
                }
            }

            OutlinedTextField(
                value = doseText,
                onValueChange = { doseText = it },
                label = { Text("Dose number (optional)") },
                placeholder = { Text("e.g. 1, 2, Booster") },
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
        }
    }
}
