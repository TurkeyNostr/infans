package com.turkbot.babytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.Feeding
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FEED_TYPES = listOf("bottle", "breast", "solids")
private val BOTTLE_UNITS = listOf("ml", "fl_oz")
private val BREAST_SIDES = listOf("left", "right", "both")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: BabyViewModel) {
    val feedings by viewModel.feedings.collectAsState()

    // ── Form state ────────────────────────────────────────
    var selectedType by remember { mutableStateOf("bottle") }
    var amountText by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("ml") }
    var selectedSide by remember { mutableStateOf("left") }
    var durationText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var sideExpanded by remember { mutableStateOf(false) }

    // Filter today's feedings
    val todayFeedings = remember(feedings) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        feedings.filter { it.time >= startOfDay }.sortedByDescending { it.time }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Log Feeding",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // ── Type FilterChips ──────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FEED_TYPES.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = {
                        selectedType = type
                        amountText = ""
                        durationText = ""
                    },
                    label = { Text(Units.feedTypeLabel(type)) }
                )
            }
        }

        // ── Conditional inputs per type ───────────────────
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
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    BOTTLE_UNITS.forEach { unit ->
                        FilterChip(
                            selected = selectedUnit == unit,
                            onClick = { selectedUnit = unit },
                            label = { Text(if (unit == "ml") "ml" else "fl oz") }
                        )
                    }
                }
            }

            "breast" -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedSide.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Side") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { sideExpanded = !sideExpanded }) {
                                    Text(if (sideExpanded) "▲" else "▼")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = sideExpanded,
                            onDismissRequest = { sideExpanded = false }
                        ) {
                            BREAST_SIDES.forEach { side ->
                                DropdownMenuItem(
                                    text = { Text(side.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        selectedSide = side
                                        sideExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                    label = { Text("Duration (minutes)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            "solids" -> {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (g)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Note ──────────────────────────────────────────
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // ── Log Feeding button ────────────────────────────
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
                // Reset form
                amountText = ""
                durationText = ""
                noteText = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log Feeding")
        }

        HorizontalDivider()

        // ── Today's feedings ──────────────────────────────
        Text(
            text = "Today",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (todayFeedings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No feedings logged today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(todayFeedings, key = { it.id }) { feeding ->
                    FeedingCard(
                        feeding = feeding,
                        onDelete = { viewModel.deleteFeeding(feeding.id) }
                    )
                }
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
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Units.feedTypeLabel(feeding.type),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (displayAmount.isNotBlank()) {
                    Text(
                        text = displayAmount,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = timeFormat.format(Date(feeding.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!feeding.note.isNullOrBlank()) {
                    Text(
                        text = feeding.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
