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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

/**
 * Reusable bar chart — vertical bars with value labels above and
 * period labels below. The last bar is highlighted (today/current period).
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val maxVal = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val lastIdx = values.lastIndex
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val barGradient = Brush.verticalGradient(
        listOf(barColor, barColor.copy(alpha = 0.5f))
    )
    val lastGradient = Brush.verticalGradient(
        listOf(barColor, barColor.copy(alpha = 0.7f))
    )

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val w = size.width
        val h = size.height
        val baseline = h - 36f
        val maxBarH = baseline - 24f
        val leftPad = 15f
        val rightPad = 15f
        val areaW = w - leftPad - rightPad
        val n = values.size
        if (n == 0) return@Canvas
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
                i == lastIdx -> 1f
                v == 0.0 -> 0.15f
                else -> 0.7f
            }
            val gradient = if (i == lastIdx) lastGradient else barGradient
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

            // Period label below bar
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    labels[i],
                    x + barW / 2,
                    baseline + 26f,
                    android.graphics.Paint().apply {
                        color = if (i == lastIdx) barColor.toArgb() else onSurfaceVariant.copy(alpha = 0.6f).toArgb()
                        textSize = 26f
                        isFakeBoldText = i == lastIdx
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}
