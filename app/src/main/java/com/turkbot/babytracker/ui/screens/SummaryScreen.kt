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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.*
import com.turkbot.babytracker.ui.components.EditTimestampDialog
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SummaryScreen(
    viewModel: BabyViewModel,
    onNavigateToWeight: () -> Unit = {},
    onNavigateToCharts: () -> Unit = {},
    onNavigateToMilestones: () -> Unit = {},
    onNavigateToDiaper: () -> Unit = {},
    onNavigateToPumping: () -> Unit = {},
    onNavigateToHealth: () -> Unit = {}
) {
    val feedings by viewModel.feedings.collectAsState()
    val sleeps by viewModel.sleeps.collectAsState()
    val weights by viewModel.weights.collectAsState()
    val milestones by viewModel.milestones.collectAsState()
    val diapers by viewModel.diapers.collectAsState()
    val pumpings by viewModel.pumpings.collectAsState()
    val healthRecords by viewModel.healthRecords.collectAsState()
    val child by viewModel.activeChild.collectAsState()
    val children by viewModel.children.collectAsState()
    var editingFeeding by remember { mutableStateOf<Feeding?>(null) }
    var editingSleep by remember { mutableStateOf<Sleep?>(null) }

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    val todayFeedings = feedings.filter { f ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(f.time)) == today
    }
    val todaySleeps = sleeps.filter { s ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(s.start)) == today
    }

    // Last activity timestamps for "last fed" / "last slept" / "last diaper"
    val lastFeeding = feedings.maxByOrNull { it.time }
    val lastSleep = sleeps.maxByOrNull { it.start }
    val lastDiaper = diapers.maxByOrNull { it.time }

    val todayDiapers = diapers.filter { d ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(d.time)) == today
    }

    // Elapsed time since last event
    fun timeAgo(time: Long): String {
        val mins = ((System.currentTimeMillis() - time) / 60000).toInt()
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ${mins % 60}m ago"
            else -> "${mins / 1440}d ago"
        }
    }

    // 7-day trend data
    val days = (0..6).map { i ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }.reversed()

    val dayLabels = days.map { d ->
        val cal = Calendar.getInstance()
        cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(d)!!
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "S"
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "T"
            Calendar.FRIDAY -> "F"
            Calendar.SATURDAY -> "S"
            else -> "?"
        }
    }

    val feedCounts = days.map { day ->
        feedings.count { f ->
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(f.time)) == day
        }
    }
    val sleepHours = days.map { day ->
        val daySleeps = sleeps.filter { s ->
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(s.start)) == day
        }
        daySleeps.sumOf { it.duration } / 60.0
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Hero child info card — gradient fill ──────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        child?.name ?: "No child selected",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    if (child?.dob != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Age: ${Units.ageFromDOB(child!!.dob)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    // Multi-child selector — show dropdown if more than 1 child
                    if (children.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        var showChildMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(
                                onClick = { showChildMenu = true },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "Switch child ▾",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            DropdownMenu(
                                expanded = showChildMenu,
                                onDismissRequest = { showChildMenu = false }
                            ) {
                                children.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.name) },
                                        onClick = {
                                            viewModel.selectChild(c.id)
                                            showChildMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Last activity stats ────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Last Activity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    LastActivityRow(
                        icon = Icons.Default.Restaurant,
                        label = "Last Feeding",
                        value = lastFeeding?.let { "${Units.feedTypeLabel(it.type)} · ${timeAgo(it.time)}" } ?: "—",
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                    LastActivityRow(
                        icon = Icons.Default.Bedtime,
                        label = "Last Sleep",
                        value = lastSleep?.let { "${Units.fmtDuration(it.duration)} · ${timeAgo(it.start)}" } ?: "—",
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )
                    LastActivityRow(
                        icon = Icons.Default.Spa,
                        label = "Last Diaper",
                        value = lastDiaper?.let { "${it.contents.replaceFirstChar { c -> c.uppercase() }} · ${timeAgo(it.time)}" } ?: "—",
                        iconTint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // ── Today's totals ─────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Today's Totals",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    // Total bottle intake (ml)
                    val totalMl = todayFeedings
                        .filter { it.type == "bottle" && it.amount != null && it.unit != null }
                        .sumOf { f -> Units.amountToMl(f.amount!!, f.unit!!) }
                    val totalBreastMin = todayFeedings
                        .filter { it.type == "breast" }
                        .sumOf { it.duration ?: 0 }
                    val totalSleepMin = todaySleeps.sumOf { it.duration }
                    val totalDiapers = todayDiapers.size

                    TotalsRow(
                        icon = Icons.Default.Restaurant,
                        label = "Feedings",
                        value = if (todayFeedings.isEmpty()) "—" else {
                            val parts = mutableListOf<String>()
                            if (totalMl > 0) parts.add("${totalMl.toInt()} ml")
                            if (totalBreastMin > 0) parts.add("${totalBreastMin} min breast")
                            if (parts.isEmpty()) "${todayFeedings.size} session${if (todayFeedings.size > 1) "s" else ""}"
                            else parts.joinToString(" · ")
                        },
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                    TotalsRow(
                        icon = Icons.Default.Bedtime,
                        label = "Sleep",
                        value = if (totalSleepMin > 0) Units.fmtDuration(totalSleepMin) else "—",
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )
                    TotalsRow(
                        icon = Icons.Default.Spa,
                        label = "Diapers",
                        value = if (totalDiapers > 0) "$totalDiapers" else "—",
                        iconTint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // ── Quick action chips ─────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.MonitorWeight,
                    label = "Weight",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToWeight
                )
                QuickActionCard(
                    icon = Icons.Default.ShowChart,
                    label = "Charts",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToCharts
                )
                QuickActionCard(
                    icon = Icons.Default.EmojiEvents,
                    label = "Milestones",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMilestones
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Spa,
                    label = "Diaper",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToDiaper
                )
                QuickActionCard(
                    icon = Icons.Default.WaterDrop,
                    label = "Pumping",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToPumping
                )
                QuickActionCard(
                    icon = Icons.Default.HealthAndSafety,
                    label = "Health",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToHealth
                )
            }
        }

        // ── Today's stats ──────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.Restaurant,
                    label = "Feedings Today",
                    value = todayFeedings.size.toString(),
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    icon = Icons.Default.Bedtime,
                    label = "Sleep Today",
                    value = Units.fmtDuration(todaySleeps.sumOf { it.duration }),
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.Spa,
                    label = "Diapers Today",
                    value = todayDiapers.size.toString(),
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.secondary
                )
                StatCard(
                    icon = Icons.Default.WaterDrop,
                    label = "Pumping Today",
                    value = pumpings.count { p ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(p.time)) == today
                    }.toString(),
                    modifier = Modifier.weight(1f),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // ── Feeding trend ──────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Feeding Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "feedings per day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    BarChart(
                        values = feedCounts.map { it.toDouble() },
                        labels = dayLabels,
                        barColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ── Sleep trend ────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Sleep Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "hours per day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    BarChart(
                        values = sleepHours,
                        labels = dayLabels,
                        barColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // ── Today's activity list ──────────────────────────
        item {
            Text(
                "Today's Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (todayFeedings.isEmpty() && todaySleeps.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        "No activity logged today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        } else {
            items(todayFeedings) { f ->
                ActivityRow(
                    icon = Icons.Default.Restaurant,
                    title = Units.feedTypeLabel(f.type),
                    subtitle = "${timeFmt.format(Date(f.time))}" +
                        (if (f.amount != null && f.unit != null) " · ${Units.fmtAmount(f.amount, f.unit)}" else ""),
                    onDelete = { viewModel.deleteFeeding(f.id) },
                    onEditTime = { editingFeeding = f },
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }
            items(todaySleeps) { s ->
                ActivityRow(
                    icon = Icons.Default.Bedtime,
                    title = "Sleep · ${Units.fmtDuration(s.duration)}",
                    subtitle = timeFmt.format(Date(s.start)),
                    onDelete = { viewModel.deleteSleep(s.id) },
                    onEditTime = { editingSleep = s },
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }

    // ── Edit timestamp dialogs ──────────────────────────
    editingFeeding?.let { feeding ->
        EditTimestampDialog(
            initialEpochMillis = feeding.time,
            onDismiss = { editingFeeding = null },
            onSave = { newTime ->
                viewModel.updateFeedingTime(feeding.id, newTime)
                editingFeeding = null
            }
        )
    }
    editingSleep?.let { sleep ->
        EditTimestampDialog(
            initialEpochMillis = sleep.start,
            onDismiss = { editingSleep = null },
            onSave = { newTime ->
                viewModel.updateSleepStart(sleep.id, newTime)
                editingSleep = null
            }
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LastActivityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TotalsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ActivityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onDelete: () -> Unit,
    onEditTime: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEditTime != null) {
                IconButton(onClick = onEditTime) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit time", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BarChart(values: List<Double>, labels: List<String>, barColor: Color) {
    val maxVal = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val todayIdx = values.lastIndex
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val barGradient = Brush.verticalGradient(
        listOf(barColor, barColor.copy(alpha = 0.5f))
    )
    val todayGradient = Brush.verticalGradient(
        listOf(barColor, barColor.copy(alpha = 0.7f))
    )

    Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
        val w = size.width
        val h = size.height
        val baseline = h - 32f
        val maxBarH = baseline - 20f
        val leftPad = 15f
        val rightPad = 15f
        val areaW = w - leftPad - rightPad
        val n = values.size
        val slotW = areaW / n
        val gap = slotW * 0.25f
        val barW = slotW - gap

        // Axis line
        drawLine(
            color = onSurfaceVariant.copy(alpha = 0.2f),
            start = Offset(leftPad, baseline),
            end = Offset(w - rightPad, baseline),
            strokeWidth = 1f
        )

        values.forEachIndexed { i, v ->
            val barH = (v / maxVal * maxBarH).toFloat().coerceAtLeast(2f)
            val x = leftPad + i * slotW + gap / 2
            val y = baseline - barH

            val alpha = when {
                i == todayIdx -> 1f
                v == 0.0 -> 0.15f
                else -> 0.7f
            }
            val gradient = if (i == todayIdx) todayGradient else barGradient
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(6f, 6f),
                alpha = alpha
            )

            // Value label above bar
            if (v > 0) {
                drawIntoCanvas {
                    it.nativeCanvas.drawText(
                        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v),
                        x + barW / 2,
                        y - 4f,
                        android.graphics.Paint().apply {
                            color = barColor.toArgb()
                            textSize = 28f
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }

            // Day label below bar
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    labels[i],
                    x + barW / 2,
                    baseline + 24f,
                    android.graphics.Paint().apply {
                        color = if (i == todayIdx) barColor.toArgb() else onSurfaceVariant.copy(alpha = 0.6f).toArgb()
                        textSize = 26f
                        isFakeBoldText = i == todayIdx
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}
