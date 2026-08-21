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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turkbot.babytracker.data.entities.Weight
import com.turkbot.babytracker.ui.viewmodel.BabyViewModel
import com.turkbot.babytracker.util.Units
import com.turkbot.babytracker.util.WhoPercentiles

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(viewModel: BabyViewModel) {
    val weights by viewModel.weights.collectAsState()
    val activeChild by viewModel.activeChild.collectAsState()
    var unit by remember { mutableStateOf("kg") }

    // Theme-aware colors for the chart
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
                text = activeChild!!.name,
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

    // Precompute WHO band path data for ages 0..24
    val bandData = remember(gender) { buildWhoBandData(gender) }

    // Convert baby weights to (ageMonths, displayValue) in selected unit
    val points = remember(weights, dob, unit) {
        weights
            .map { w -> Units.ageInMonths(dob, w.date) to kgToUnit(w.value, unit) }
            .filter { it.first in MIN_AGE..MAX_AGE }
            .sortedBy { it.first }
    }

    // Y range: combine WHO band max and baby point max, round up nicely
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

/** Build WHO band data for integer ages 0..24 (converted to display unit done at draw time). */
private fun buildWhoBandData(gender: String): List<BandPoint> {
    return (0..24).mapNotNull { age ->
        val bands = WhoPercentiles.getBands(gender, age.toDouble())
        if (bands != null) BandPoint(age.toDouble(), bands.first, bands.second, bands.third)
        else null
    }
}

/** Convert kg to the display unit numeric value. */
private fun kgToUnit(kg: Double, unit: String): Double = when (unit) {
    "kg" -> kg
    "lb" -> Units.kgToLb(kg)
    "oz" -> Units.kgToOz(kg)
    else -> kg
}

/** Round up to a nice axis maximum. */
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

    // ── Grid lines + Y axis labels ──────────────────
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

    // ── X axis labels (0, 6, 12, 18, 24) ───────────
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

    // X axis title
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

    // ── Axis frame ─────────────────────────────────
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

    // ── WHO P3-P97 filled band ─────────────────────
    val bandPath = Path().apply {
        // Top edge: P97 left→right
        bandData.forEachIndexed { i, bp ->
            val x = xForAge(bp.age)
            val y = yForValue(kgToUnit(bp.p97, unit))
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        // Bottom edge: P3 right→left
        bandData.asReversed().forEach { bp ->
            val x = xForAge(bp.age)
            val y = yForValue(kgToUnit(bp.p3, unit))
            lineTo(x, y)
        }
        close()
    }
    drawPath(bandPath, color = colors.band)

    // ── P50 dashed line ────────────────────────────
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

    // ── P3 / P97 dotted lines ──────────────────────
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

    // ── Baby weight line ───────────────────────────
    val babyPath = Path().apply {
        points.forEachIndexed { i, (age, v) ->
            val x = xForAge(age)
            val y = yForValue(v)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(babyPath, color = colors.babyLine, style = Stroke(width = 2.5f))

    // ── Baby weight dots ───────────────────────────
    points.forEach { (age, v) ->
        val x = xForAge(age)
        val y = yForValue(v)
        drawCircle(color = colors.babyDot, radius = 4f, center = Offset(x, y))
        drawCircle(color = Color.White, radius = 2f, center = Offset(x, y))
    }

    // ── Latest value label ─────────────────────────
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
    // Place label above-right of the dot, clamp to chart bounds
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

/** Format an axis tick value for the current unit. */
private fun formatAxisValue(v: Double, unit: String): String = when (unit) {
    "oz" -> "${v.toInt()}"
    else -> "${"%.1f".format(v)}"
}
