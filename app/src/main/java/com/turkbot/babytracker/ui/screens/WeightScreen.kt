package com.turkbot.babytracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Weight
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(viewModel: BabyViewModel) {
    val weights by viewModel.weights.collectAsState()
    var weightInput by remember { mutableStateOf("") }
    var weightUnit by remember { mutableStateOf("kg") }
    var weightUnitExpanded by remember { mutableStateOf(false) }
    var heightInput by remember { mutableStateOf("") }
    var heightUnit by remember { mutableStateOf("cm") }
    var heightUnitExpanded by remember { mutableStateOf(false) }

    val weightUnits = listOf("kg", "lb", "oz")
    val heightUnits = listOf("cm", "in")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Log Weight and Height",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // ── Weight input row ──────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = weightInput,
                onValueChange = { weightInput = it },
                label = { Text("Weight") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            ExposedDropdownMenuBox(
                expanded = weightUnitExpanded,
                onExpandedChange = { weightUnitExpanded = it },
                modifier = Modifier.weight(0.5f)
            ) {
                OutlinedTextField(
                    value = weightUnit,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unit") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = weightUnitExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = weightUnitExpanded,
                    onDismissRequest = { weightUnitExpanded = false }
                ) {
                    weightUnits.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit) },
                            onClick = {
                                weightUnit = unit
                                weightUnitExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Height input row (optional) ───────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = heightInput,
                onValueChange = { heightInput = it },
                label = { Text("Height (optional)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            ExposedDropdownMenuBox(
                expanded = heightUnitExpanded,
                onExpandedChange = { heightUnitExpanded = it },
                modifier = Modifier.weight(0.5f)
            ) {
                OutlinedTextField(
                    value = heightUnit,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unit") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = heightUnitExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = heightUnitExpanded,
                    onDismissRequest = { heightUnitExpanded = false }
                ) {
                    heightUnits.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit) },
                            onClick = {
                                heightUnit = unit
                                heightUnitExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val weightValue = weightInput.trim().toDoubleOrNull()
                if (weightValue != null) {
                    val valueKg = Units.toKg(weightValue, weightUnit)
                    val heightCm: Double? = heightInput.trim().toDoubleOrNull()?.let {
                        Units.toCm(it, heightUnit)
                    }
                    val hUnit = if (heightCm != null) heightUnit else null
                    viewModel.addWeight(valueKg, weightUnit, heightCm, hUnit)
                    weightInput = ""
                    heightInput = ""
                }
            },
            enabled = weightInput.trim().toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Measurement")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Weight history ────────────────────────────────
        if (weights.isEmpty()) {
            Text(
                text = "No weight measurements recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(weights, key = { it.id }) { weight ->
                    WeightCard(
                        weight = weight,
                        onDelete = { viewModel.deleteWeight(weight.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightCard(
    weight: Weight,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d yyyy", Locale.getDefault()) }
    val formattedDate = remember(weight.date) {
        dateFormat.format(Date(weight.date))
    }

    Card(
        colors = CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "\uD83D\uDCCE",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Units.fromKg(weight.value, weight.unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (weight.height != null && weight.heightUnit != null) {
                    Text(
                        text = Units.fmtHeight(weight.height, weight.heightUnit),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete weight measurement"
                )
            }
        }
    }
}
