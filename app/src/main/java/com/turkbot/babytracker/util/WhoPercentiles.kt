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

package com.turkbot.babytracker.util

/**
 * WHO weight-for-age percentile tables (boys and girls, 0–24 months).
 * Values in kg. Source: WHO Child Growth Standards.
 *
 * Each entry: [ageMonths, P3, P50, P97]
 */
object WhoPercentiles {

    // ── Weight-for-age (kg) ─────────────────────────────

    val boys = listOf(
        doubleArrayOf(0.0, 2.5, 3.3, 4.4),
        doubleArrayOf(1.0, 3.1, 4.4, 5.8),
        doubleArrayOf(2.0, 4.4, 5.6, 7.0),
        doubleArrayOf(3.0, 5.1, 6.4, 8.0),
        doubleArrayOf(4.0, 5.6, 7.0, 8.7),
        doubleArrayOf(5.0, 6.0, 7.5, 9.3),
        doubleArrayOf(6.0, 6.4, 7.9, 9.8),
        doubleArrayOf(7.0, 6.7, 8.3, 10.3),
        doubleArrayOf(8.0, 6.9, 8.6, 10.7),
        doubleArrayOf(9.0, 7.1, 8.9, 11.0),
        doubleArrayOf(10.0, 7.4, 9.2, 11.4),
        doubleArrayOf(11.0, 7.6, 9.4, 11.7),
        doubleArrayOf(12.0, 7.7, 9.6, 12.0),
        doubleArrayOf(15.0, 8.3, 10.3, 12.8),
        doubleArrayOf(18.0, 8.8, 10.9, 13.6),
        doubleArrayOf(21.0, 9.2, 11.5, 14.3),
        doubleArrayOf(24.0, 9.6, 12.2, 15.0),
    )

    val girls = listOf(
        doubleArrayOf(0.0, 2.4, 3.2, 4.2),
        doubleArrayOf(1.0, 3.0, 4.2, 5.5),
        doubleArrayOf(2.0, 3.9, 5.1, 6.6),
        doubleArrayOf(3.0, 4.5, 5.8, 7.5),
        doubleArrayOf(4.0, 5.0, 6.4, 8.2),
        doubleArrayOf(5.0, 5.4, 6.9, 8.8),
        doubleArrayOf(6.0, 5.7, 7.3, 9.3),
        doubleArrayOf(7.0, 6.0, 7.6, 9.8),
        doubleArrayOf(8.0, 6.3, 7.9, 10.2),
        doubleArrayOf(9.0, 6.5, 8.2, 10.6),
        doubleArrayOf(10.0, 6.7, 8.5, 11.0),
        doubleArrayOf(11.0, 6.9, 8.7, 11.3),
        doubleArrayOf(12.0, 7.1, 8.9, 11.6),
        doubleArrayOf(15.0, 7.6, 9.6, 12.4),
        doubleArrayOf(18.0, 8.1, 10.2, 13.2),
        doubleArrayOf(21.0, 8.6, 10.8, 13.9),
        doubleArrayOf(24.0, 9.0, 11.5, 14.5),
    )

    // ── Head circumference-for-age (cm) ─────────────────

    val boysHeadCirc = listOf(
        doubleArrayOf(0.0, 32.4, 34.5, 36.6),
        doubleArrayOf(1.0, 35.4, 37.3, 39.2),
        doubleArrayOf(2.0, 37.0, 39.1, 41.2),
        doubleArrayOf(3.0, 38.1, 40.5, 42.9),
        doubleArrayOf(4.0, 39.0, 41.6, 44.2),
        doubleArrayOf(5.0, 39.7, 42.6, 45.5),
        doubleArrayOf(6.0, 40.3, 43.3, 46.3),
        doubleArrayOf(7.0, 40.9, 44.0, 47.1),
        doubleArrayOf(8.0, 41.4, 44.5, 47.6),
        doubleArrayOf(9.0, 41.8, 45.0, 48.2),
        doubleArrayOf(10.0, 42.2, 45.4, 48.6),
        doubleArrayOf(11.0, 42.5, 45.8, 49.1),
        doubleArrayOf(12.0, 42.8, 46.1, 49.4),
        doubleArrayOf(15.0, 43.5, 46.8, 50.1),
        doubleArrayOf(18.0, 44.0, 47.4, 50.8),
        doubleArrayOf(21.0, 44.4, 47.9, 51.4),
        doubleArrayOf(24.0, 44.7, 48.3, 51.9),
    )

    val girlsHeadCirc = listOf(
        doubleArrayOf(0.0, 32.1, 33.9, 35.7),
        doubleArrayOf(1.0, 34.7, 36.5, 38.3),
        doubleArrayOf(2.0, 36.2, 38.3, 40.4),
        doubleArrayOf(3.0, 37.3, 39.5, 41.7),
        doubleArrayOf(4.0, 38.1, 40.6, 43.1),
        doubleArrayOf(5.0, 38.7, 41.5, 44.3),
        doubleArrayOf(6.0, 39.2, 42.2, 45.2),
        doubleArrayOf(7.0, 39.7, 42.8, 45.9),
        doubleArrayOf(8.0, 40.1, 43.3, 46.5),
        doubleArrayOf(9.0, 40.5, 43.7, 46.9),
        doubleArrayOf(10.0, 40.8, 44.1, 47.4),
        doubleArrayOf(11.0, 41.1, 44.4, 47.7),
        doubleArrayOf(12.0, 41.3, 44.7, 48.1),
        doubleArrayOf(15.0, 42.0, 45.4, 48.8),
        doubleArrayOf(18.0, 42.5, 45.9, 49.3),
        doubleArrayOf(21.0, 42.9, 46.3, 49.7),
        doubleArrayOf(24.0, 43.2, 46.6, 50.0),
    )

    /**
     * Get interpolated P3/P50/P97 weight-for-age for a given age in months.
     */
    fun getBands(gender: String?, ageMonths: Double): Triple<Double, Double, Double>? {
        val table = if (gender == "girl") girls else boys
        return interpolateFromTable(table, ageMonths)
    }

    /**
     * Get interpolated P3/P50/P97 head circumference-for-age (cm) for a given age.
     */
    fun getHeadCircBands(gender: String?, ageMonths: Double): Triple<Double, Double, Double>? {
        val table = if (gender == "girl") girlsHeadCirc else boysHeadCirc
        return interpolateFromTable(table, ageMonths)
    }

    private fun interpolateFromTable(table: List<DoubleArray>, ageMonths: Double): Triple<Double, Double, Double>? {
        if (ageMonths < 0 || ageMonths > 24) return null

        var lower: DoubleArray? = null
        var upper: DoubleArray? = null
        for (entry in table) {
            if (entry[0] <= ageMonths) lower = entry
            if (entry[0] >= ageMonths && upper == null) upper = entry
        }
        if (lower == null) lower = table.first()
        if (upper == null) upper = table.last()
        if (lower === upper) return Triple(lower[1], lower[2], lower[3])

        val t = (ageMonths - lower[0]) / (upper[0] - lower[0])
        val p3 = lower[1] + t * (upper[1] - lower[1])
        val p50 = lower[2] + t * (upper[2] - lower[2])
        val p97 = lower[3] + t * (upper[3] - lower[3])
        return Triple(p3, p50, p97)
    }
}
