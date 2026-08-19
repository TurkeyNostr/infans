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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turkbot.babytracker.data.entities.*
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SummaryScreen(
    viewModel: BabyViewModel,
    onNavigateToWeight: () -> Unit = {},
    onNavigateToCharts: () -> Unit = {},
    onNavigateToMilestones: () -> Unit = {}
) {
    val feedings by viewModel.feedings.collectAsState()
    val sleeps by viewModel.sleeps.collectAsState()
    val weights by viewModel.weights.collectAsState()
    val milestones by viewModel.milestones.collectAsState()
    val child by viewModel.activeChild.collectAsState()

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    val todayFeedings = feedings.filter { f ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(f.time)) == today
    }
    val todaySleeps = sleeps.filter { s ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(s.start)) == today
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
        // ── Hero child info card ──────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        child?.name ?: "No child selected",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (child?.dob != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Age: ${Units.ageFromDOB(child!!.dob)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
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
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Default.Bedtime,
                    label = "Sleep Today",
                    value = Units.fmtDuration(todaySleeps.sumOf { it.duration }),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Feeding trend ──────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
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
                )
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
                    onDelete = { viewModel.deleteFeeding(f.id) }
                )
            }
            items(todaySleeps) { s ->
                ActivityRow(
                    icon = Icons.Default.Bedtime,
                    title = "Sleep · ${Units.fmtDuration(s.duration)}",
                    subtitle = timeFmt.format(Date(s.start)),
                    onDelete = { viewModel.deleteSleep(s.id) }
                )
            }
        }
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
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
private fun ActivityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            drawRoundRect(
                color = barColor.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(6f, 6f)
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
