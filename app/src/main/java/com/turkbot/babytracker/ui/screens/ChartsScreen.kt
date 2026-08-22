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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turkbot.babytracker.data.entities.*
import com.turkbot.babytracker.ui.components.BarChart
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import com.turkbot.babytracker.util.WhoPercentiles
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

private const val MIN_AGE = 0.0
private const val MAX_AGE = 24.0

private data class ChartColors(
    val band: Color,
    val p50: Color,
    val p3p97: Color,
    val babyLine: Color,
    val babyDot: Color,
    val axis: Color,
    val grid: Color,
    val labelBg: Color
)

// ── Period + metric enums for activity trends ──────────

private enum class Period(val label: String, val buckets: Int) {
    DAILY("Daily", 7),       // last 7 days
    WEEKLY("Weekly", 8),     // last 8 weeks
    MONTHLY("Monthly", 6),   // last 6 months
    YEARLY("Yearly", 5)      // last 5 years
}

private enum class Metric(val label: String, val unit: String) {
    FEEDINGS("Feedings", "count"),
    SLEEP("Sleep", "hours"),
    DIAPERS("Diapers", "count"),
    BATHS("Baths", "count"),
    PUMPING("Pumping", "ml"),
    WEIGHT("Weight", "kg"),
    MILESTONES("Milestones", "count"),
    HEALTH("Health Records", "count")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(viewModel: BabyViewModel) {
    val weights by viewModel.weights.collectAsState()
    val feedings by viewModel.feedings.collectAsState()
    val sleeps by viewModel.sleeps.collectAsState()
    val diapers by viewModel.diapers.collectAsState()
    val baths by viewModel.baths.collectAsState()
    val pumpings by viewModel.pumpings.collectAsState()
    val milestones by viewModel.milestones.collectAsState()
    val healthRecords by viewModel.healthRecords.collectAsState()
    val activeChild by viewModel.activeChild.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }  // 0 = growth, 1 = activity

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Tab row ──────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Growth") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Activity") }
            )
        }

        if (selectedTab == 0) {
            GrowthTab(weights, activeChild)
        } else {
            ActivityTab(
                feedings = feedings,
                sleeps = sleeps,
                diapers = diapers,
                baths = baths,
                pumpings = pumpings,
                weights = weights,
                milestones = milestones,
                healthRecords = healthRecords
            )
        }
    }
}

// ── Growth tab (original chart) ────────────────────────

@Composable
private fun GrowthTab(weights: List<Weight>, activeChild: Child?) {
    var unit by remember { mutableStateOf("kg") }

    val colorScheme = MaterialTheme.colorScheme
    val chartColors = remember(colorScheme) {
        ChartColors(
            band = colorScheme.primary.copy(alpha = 0.13f),
            p50 = colorScheme.primary,
            p3p97 = colorScheme.outline,
            babyLine = colorScheme.primary,
            babyDot = colorScheme.primary,
            axis = colorScheme.outlineVariant,
            grid = colorScheme.outlineVariant.copy(alpha = 0.3f),
            labelBg = colorScheme.surface.copy(alpha = 0.9f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Weight-for-age",
            style = MaterialTheme.typography.titleLarge
        )
        if (activeChild != null) {
            Text(
                text = activeChild.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))

        // Unit selector
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("kg", "lb", "oz").forEach { u ->
                FilterChip(
                    selected = unit == u,
                    onClick = { unit = u },
                    label = { Text(u) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val child = activeChild
        val dob = child?.dob
        val gender = child?.gender

        if (dob == null || gender == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (dob == null && gender == null)
                        "Set date of birth and gender to see WHO growth chart."
                    else if (dob == null)
                        "Set date of birth to see WHO growth chart."
                    else
                        "Set gender to see WHO growth chart.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            GrowthChart(
                weights = weights,
                dob = dob,
                gender = gender,
                unit = unit,
                colors = chartColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Legend
        LegendRow(chartColors)

        Spacer(Modifier.height(12.dp))

        // Latest value summary
        if (weights.isNotEmpty() && dob != null) {
            val latest = weights.maxByOrNull { it.date }!!
            val ageMo = Units.ageInMonths(dob, latest.date)
            val display = Units.fromKg(latest.value, unit)
            val ageStr = if (ageMo < 1) "${(ageMo * 30.4375).toInt()}d"
                         else "${"%.1f".format(ageMo)} mo"
            Text(
                text = "Latest: $display at $ageStr",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Activity tab ───────────────────────────────────────

@Composable
private fun ActivityTab(
    feedings: List<Feeding>,
    sleeps: List<Sleep>,
    diapers: List<Diaper>,
    baths: List<Bath>,
    pumpings: List<Pumping>,
    weights: List<Weight>,
    milestones: List<Milestone>,
    healthRecords: List<HealthRecord>
) {
    var selectedPeriod by remember { mutableStateOf(Period.DAILY) }
    var selectedMetric by remember { mutableStateOf(Metric.FEEDINGS) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Period selector ─────────────────────────────
        item {
            Text(
                "Activity Trends",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Period.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label) }
                    )
                }
            }
        }

        // ── Metric selector ─────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Metric.entries.forEach { metric ->
                    FilterChip(
                        selected = selectedMetric == metric,
                        onClick = { selectedMetric = metric },
                        label = { Text(metric.label) }
                    )
                }
            }
        }

        // ── Chart card ──────────────────────────────────
        item {
            val (values, labels) = remember(
                selectedPeriod, selectedMetric,
                feedings, sleeps, diapers, baths, pumpings, weights, milestones, healthRecords
            ) {
                aggregateActivity(
                    period = selectedPeriod,
                    metric = selectedMetric,
                    feedings = feedings,
                    sleeps = sleeps,
                    diapers = diapers,
                    baths = baths,
                    pumpings = pumpings,
                    weights = weights,
                    milestones = milestones,
                    healthRecords = healthRecords
                )
            }

            val barColor = when (selectedMetric) {
                Metric.FEEDINGS -> MaterialTheme.colorScheme.primary
                Metric.SLEEP -> MaterialTheme.colorScheme.tertiary
                Metric.DIAPERS -> MaterialTheme.colorScheme.secondary
                Metric.BATHS -> MaterialTheme.colorScheme.primary
                Metric.PUMPING -> MaterialTheme.colorScheme.tertiary
                Metric.WEIGHT -> MaterialTheme.colorScheme.secondary
                Metric.MILESTONES -> MaterialTheme.colorScheme.primary
                Metric.HEALTH -> MaterialTheme.colorScheme.tertiary
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${selectedMetric.label} — ${selectedPeriod.label}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        selectedMetric.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (values.all { it == 0.0 }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No ${selectedMetric.label.lowercase()} recorded in this period",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        BarChart(
                            values = values,
                            labels = labels,
                            barColor = barColor
                        )
                    }
                }
            }
        }
    }
}

// ── Aggregation logic ──────────────────────────────────

/**
 * Aggregate activity data into buckets for the given period + metric.
 * Returns (values, labels) for the BarChart.
 */
private fun aggregateActivity(
    period: Period,
    metric: Metric,
    feedings: List<Feeding>,
    sleeps: List<Sleep>,
    diapers: List<Diaper>,
    baths: List<Bath>,
    pumpings: List<Pumping>,
    weights: List<Weight>,
    milestones: List<Milestone>,
    healthRecords: List<HealthRecord>
): Pair<List<Double>, List<String>> {
    val now = Calendar.getInstance()
    val n = period.buckets
    val values = DoubleArray(n)
    val labels = ArrayList<String>(n)

    // Build bucket boundaries (oldest → newest)
    val buckets = ArrayList<Pair<Calendar, Calendar>>(n) // [start, end)
    val fmt = when (period) {
        Period.DAILY -> SimpleDateFormat("EEE", Locale.getDefault())
        Period.WEEKLY -> SimpleDateFormat("M/d", Locale.getDefault())
        Period.MONTHLY -> SimpleDateFormat("MMM", Locale.getDefault())
        Period.YEARLY -> SimpleDateFormat("yyyy", Locale.getDefault())
    }

    for (i in 0 until n) {
        val idx = n - 1 - i  // i=0 → newest bucket, i=n-1 → oldest
        val (start, end) = bucketRange(period, now, idx)
        buckets.add(start to end)
    }
    // buckets is now oldest → newest

    for (i in 0 until n) {
        val (start, end) = buckets[i]
        val count: Double = when (metric) {
            Metric.FEEDINGS -> feedings.count { it.time in start.timeInMillis until end.timeInMillis }.toDouble()
            Metric.SLEEP -> {
                sleeps.filter { it.start in start.timeInMillis until end.timeInMillis }
                    .sumOf { it.duration / 60.0 }  // minutes → hours
            }
            Metric.DIAPERS -> diapers.count { it.time in start.timeInMillis until end.timeInMillis }.toDouble()
            Metric.BATHS -> baths.count { it.time in start.timeInMillis until end.timeInMillis }.toDouble()
            Metric.PUMPING -> {
                pumpings.filter { it.time in start.timeInMillis until end.timeInMillis }
                    .sumOf { it.amount }
            }
            Metric.WEIGHT -> weights.count { it.date in start.timeInMillis until end.timeInMillis }.toDouble()
            Metric.MILESTONES -> milestones.count { it.date in start.timeInMillis until end.timeInMillis }.toDouble()
            Metric.HEALTH -> healthRecords.count { it.time in start.timeInMillis until end.timeInMillis }.toDouble()
        }
        values[i] = count

        // Label: use the start of the bucket
        labels.add(fmt.format(Date(start.timeInMillis)))
    }

    return values.toList() to labels
}

/**
 * Get the [start, end) calendar range for a bucket that is `idx` periods
 * before the current period. idx=0 = current period, idx=1 = previous, etc.
 */
private fun bucketRange(period: Period, now: Calendar, idx: Int): Pair<Calendar, Calendar> {
    var start = now.clone() as Calendar
    var end = now.clone() as Calendar

    when (period) {
        Period.DAILY -> {
            start.add(Calendar.DAY_OF_YEAR, -idx)
            start.set(Calendar.HOUR_OF_DAY, 0)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
            end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        }
        Period.WEEKLY -> {
            // Align to Monday
            start.add(Calendar.WEEK_OF_YEAR, -idx)
            start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            start.set(Calendar.HOUR_OF_DAY, 0)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
            end = (start.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, 1) }
        }
        Period.MONTHLY -> {
            start.add(Calendar.MONTH, -idx)
            start.set(Calendar.DAY_OF_MONTH, 1)
            start.set(Calendar.HOUR_OF_DAY, 0)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
            end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        }
        Period.YEARLY -> {
            start.add(Calendar.YEAR, -idx)
            start.set(Calendar.MONTH, Calendar.JANUARY)
            start.set(Calendar.DAY_OF_MONTH, 1)
            start.set(Calendar.HOUR_OF_DAY, 0)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
            end = (start.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
        }
    }

    return start to end
}

// ── Legend composables ─────────────────────────────────

@Composable
private fun LegendRow(colors: ChartColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendSwatch(color = colors.band, label = "P3–P97")
        LegendDashedLine(color = colors.p50, label = "P50")
        LegendDottedLine(color = colors.p3p97, label = "P3/P97")
        LegendSwatch(color = colors.babyDot, label = "Baby")
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = color,
            modifier = Modifier.size(14.dp),
            shape = RoundedCornerShape(3.dp)
        ) {}
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LegendDashedLine(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp, 14.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LegendDottedLine(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp, 14.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f))
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ── Growth chart canvas ──────────────────────────────

@Composable
private fun GrowthChart(
    weights: List<Weight>,
    dob: Long,
    gender: String,
    unit: String,
    colors: ChartColors,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    val bandData = remember(gender) { buildWhoBandData(gender) }

    val points = remember(weights, dob, unit) {
        weights
            .map { w -> Units.ageInMonths(dob, w.date) to kgToUnit(w.value, unit) }
            .filter { it.first in MIN_AGE..MAX_AGE }
            .sortedBy { it.first }
    }

    val yMax = remember(bandData, points, unit) {
        val whoMax = bandData.maxOf { it.p97 }
        val babyMax = points.maxByOrNull { it.second }?.second ?: 0.0
        niceCeil(maxOf(whoMax, babyMax) * 1.1)
    }
    val yMin = 0.0

    Canvas(modifier = modifier) {
        drawGrowthChart(
            bandData = bandData,
            points = points,
            yMax = yMax,
            yMin = yMin,
            unit = unit,
            colors = colors,
            textMeasurer = textMeasurer
        )
    }
}

private data class BandPoint(
    val age: Double,
    val p3: Double,
    val p50: Double,
    val p97: Double
)

private fun buildWhoBandData(gender: String): List<BandPoint> {
    return (0..24).mapNotNull { age ->
        val bands = WhoPercentiles.getBands(gender, age.toDouble())
        if (bands != null) BandPoint(age.toDouble(), bands.first, bands.second, bands.third)
        else null
    }
}

private fun kgToUnit(kg: Double, unit: String): Double = when (unit) {
    "kg" -> kg
    "lb" -> Units.kgToLb(kg)
    "oz" -> Units.kgToOz(kg)
    else -> kg
}

private fun niceCeil(value: Double): Double {
    if (value <= 0) return 1.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(value)))
    val normalized = value / magnitude
    val nice = when {
        normalized <= 1 -> 1.0
        normalized <= 2 -> 2.0
        normalized <= 5 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

// ── DrawScope drawing ────────────────────────────────

private fun DrawScope.drawGrowthChart(
    bandData: List<BandPoint>,
    points: List<Pair<Double, Double>>,
    yMax: Double,
    yMin: Double,
    unit: String,
    colors: ChartColors,
    textMeasurer: TextMeasurer
) {
    if (size.width <= 0f || size.height <= 0f) return

    val padLeft = 44f
    val padRight = 16f
    val padTop = 12f
    val padBottom = 28f

    val chartLeft = padLeft
    val chartTop = padTop
    val chartRight = size.width - padRight
    val chartBottom = size.height - padBottom
    val chartWidth = chartRight - chartLeft
    val chartHeight = chartBottom - chartTop

    if (chartWidth <= 0f || chartHeight <= 0f) return

    val xRange = MAX_AGE - MIN_AGE
    val yRange = (yMax - yMin).coerceAtLeast(0.001)

    fun xForAge(age: Double): Float =
        chartLeft + ((age - MIN_AGE) / xRange * chartWidth).toFloat()

    fun yForValue(v: Double): Float =
        chartBottom - ((v - yMin) / yRange * chartHeight).toFloat()

    val ySteps = 5
    for (i in 0..ySteps) {
        val v = yMin + (yMax - yMin) * i / ySteps
        val y = yForValue(v)
        drawLine(
            color = colors.grid,
            start = Offset(chartLeft, y),
            end = Offset(chartRight, y),
            strokeWidth = 1f
        )
        val label = formatAxisValue(v, unit)
        val layout = textMeasurer.measure(
            buildAnnotatedString { append(label) },
            style = TextStyle(fontSize = 9.sp, color = colors.axis)
        )
        drawText(
            layout,
            topLeft = Offset(
                (chartLeft - layout.size.width - 4f).coerceAtLeast(0f),
                y - layout.size.height / 2f
            )
        )
    }

    listOf(0, 6, 12, 18, 24).forEach { age ->
        val x = xForAge(age.toDouble())
        drawLine(
            color = colors.grid,
            start = Offset(x, chartTop),
            end = Offset(x, chartBottom),
            strokeWidth = 1f
        )
        val layout = textMeasurer.measure(
            buildAnnotatedString { append("$age") },
            style = TextStyle(fontSize = 9.sp, color = colors.axis)
        )
        drawText(
            layout,
            topLeft = Offset(
                x - layout.size.width / 2f,
                chartBottom + 4f
            )
        )
    }

    val xTitle = textMeasurer.measure(
        buildAnnotatedString { append("Age (months)") },
        style = TextStyle(fontSize = 9.sp, color = colors.axis)
    )
    drawText(
        xTitle,
        topLeft = Offset(
            chartLeft + chartWidth / 2f - xTitle.size.width / 2f,
            chartBottom + 16f
        )
    )

    drawLine(
        color = colors.axis,
        start = Offset(chartLeft, chartTop),
        end = Offset(chartLeft, chartBottom),
        strokeWidth = 1.5f
    )
    drawLine(
        color = colors.axis,
        start = Offset(chartLeft, chartBottom),
        end = Offset(chartRight, chartBottom),
        strokeWidth = 1.5f
    )

    if (bandData.isEmpty()) return

    val bandPath = Path().apply {
        bandData.forEachIndexed { i, bp ->
            val x = xForAge(bp.age)
            val y = yForValue(kgToUnit(bp.p97, unit))
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        bandData.asReversed().forEach { bp ->
            val x = xForAge(bp.age)
            val y = yForValue(kgToUnit(bp.p3, unit))
            lineTo(x, y)
        }
        close()
    }
    drawPath(bandPath, color = colors.band)

    val p50Path = Path().apply {
        bandData.forEachIndexed { i, bp ->
            val x = xForAge(bp.age)
            val y = yForValue(kgToUnit(bp.p50, unit))
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(
        p50Path,
        color = colors.p50,
        style = Stroke(
            width = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
        )
    )

    val dottedEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f))
    listOf({ bp: BandPoint -> bp.p3 }, { bp: BandPoint -> bp.p97 }).forEach { sel ->
        val path = Path().apply {
            bandData.forEachIndexed { i, bp ->
                val x = xForAge(bp.age)
                val y = yForValue(kgToUnit(sel(bp), unit))
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path, color = colors.p3p97, style = Stroke(width = 1.5f, pathEffect = dottedEffect))
    }

    if (points.isEmpty()) return

    val babyPath = Path().apply {
        points.forEachIndexed { i, (age, v) ->
            val x = xForAge(age)
            val y = yForValue(v)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(babyPath, color = colors.babyLine, style = Stroke(width = 2.5f))

    points.forEach { (age, v) ->
        val x = xForAge(age)
        val y = yForValue(v)
        drawCircle(color = colors.babyDot, radius = 4f, center = Offset(x, y))
        drawCircle(color = Color.White, radius = 2f, center = Offset(x, y))
    }

    val latest = points.last()
    val lx = xForAge(latest.first)
    val ly = yForValue(latest.second)
    val label = Units.fromKg(
        when (unit) {
            "lb" -> Units.lbToKg(latest.second)
            "oz" -> Units.ozToKg(latest.second)
            else -> latest.second
        },
        unit
    )
    val labelLayout = textMeasurer.measure(
        buildAnnotatedString { append(label) },
        style = TextStyle(
            fontSize = 10.sp,
            color = colors.babyDot,
            background = colors.labelBg
        )
    )
    var labelX = lx + 8f
    var labelY = ly - labelLayout.size.height - 6f
    if (labelX + labelLayout.size.width > chartRight) {
        labelX = lx - labelLayout.size.width - 8f
    }
    if (labelY < chartTop) {
        labelY = ly + 8f
    }
    drawText(labelLayout, topLeft = Offset(labelX, labelY))
}

private fun formatAxisValue(v: Double, unit: String): String = when (unit) {
    "oz" -> "${v.toInt()}"
    else -> "${"%.1f".format(v)}"
}
