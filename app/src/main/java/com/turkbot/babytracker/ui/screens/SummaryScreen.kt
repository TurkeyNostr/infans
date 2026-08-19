package com.turkbot.babytracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turkbot.babytracker.data.entities.*
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SummaryScreen(viewModel: BabyViewModel) {
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
    val todayMilestones = milestones.filter { m ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(m.date)) == today
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Child info
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(child?.name ?: "No child selected", style = MaterialTheme.typography.titleLarge)
                    if (child?.dob != null) {
                        Text("Age: ${Units.ageFromDOB(child!!.dob)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Summary stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStatCard("Feedings Today", todayFeedings.size.toString(), Modifier.weight(1f))
                SummaryStatCard("Sleep Today", Units.fmtDuration(todaySleeps.sumOf { it.duration }), Modifier.weight(1f))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStatCard("Milestones Today", todayMilestones.size.toString(), Modifier.weight(1f))
                SummaryStatCard("Weight Records", weights.size.toString(), Modifier.weight(1f))
            }
        }

        // Feeding trend bar chart
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Feeding Trend (7 days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("feedings per day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    BarChart(values = feedCounts.map { it.toDouble() }, labels = dayLabels, barColor = Color(0xFF6750A4))
                }
            }
        }

        // Sleep trend bar chart
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sleep Trend (7 days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("hours per day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    BarChart(values = sleepHours, labels = dayLabels, barColor = Color(0xFF5B7CFA))
                }
            }
        }

        // Today's feedings list
        item {
            Text("Today's Feedings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (todayFeedings.isEmpty()) {
            item { Text("No feedings logged today", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(todayFeedings) { f ->
                FeedingRow(f, timeFmt, onDelete = { viewModel.deleteFeeding(f.id) })
            }
        }

        // Today's sleep list
        item {
            Text("Today's Sleep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (todaySleeps.isEmpty()) {
            item { Text("No sleep logged today", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(todaySleeps) { s ->
                SleepRow(s, timeFmt, onDelete = { viewModel.deleteSleep(s.id) })
            }
        }
    }
}

@Composable
private fun SummaryStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FeedingRow(f: Feeding, timeFmt: SimpleDateFormat, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${Units.feedTypeLabel(f.type)} · ${timeFmt.format(Date(f.time))}", style = MaterialTheme.typography.bodyMedium)
                if (f.amount != null && f.unit != null) {
                    Text(Units.fmtAmount(f.amount, f.unit), style = MaterialTheme.typography.bodySmall)
                }
                if (f.note != null) {
                    Text(f.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun SleepRow(s: Sleep, timeFmt: SimpleDateFormat, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("😴 ${timeFmt.format(Date(s.start))} · ${Units.fmtDuration(s.duration)}", style = MaterialTheme.typography.bodyMedium)
                if (s.note != null) {
                    Text(s.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun BarChart(values: List<Double>, labels: List<String>, barColor: Color) {
    val maxVal = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val todayIdx = values.lastIndex

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
            color = Color(0xFFCCCCCC),
            start = Offset(leftPad, baseline),
            end = Offset(w - rightPad, baseline),
            strokeWidth = 1f
        )

        values.forEachIndexed { i, v ->
            val barH = (v / maxVal * maxBarH).toFloat().coerceAtLeast(2f)
            val x = leftPad + i * slotW + gap / 2
            val y = baseline - barH

            // Bar (today = full opacity, zero-value = 0.15, other = 0.7)
            val alpha = when {
                i == todayIdx -> 1f
                v == 0.0 -> 0.15f
                else -> 0.7f
            }
            drawRoundRect(
                color = barColor.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
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
                        color = if (i == todayIdx) barColor.toArgb() else Color(0xFF999999).toArgb()
                        textSize = 26f
                        isFakeBoldText = i == todayIdx
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}
