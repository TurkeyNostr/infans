package com.turkbot.babytracker.util

/**
 * WHO weight-for-age percentile tables (boys and girls, 0–24 months).
 * Values in kg. Source: WHO Child Growth Standards.
 *
 * Each entry: [ageMonths, P3, P50, P97]
 */
object WhoPercentiles {

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

    /**
     * Get interpolated P3/P50/P97 for a given age in months.
     */
    fun getBands(gender: String?, ageMonths: Double): Triple<Double, Double, Double>? {
        val table = if (gender == "girl") girls else boys
        if (ageMonths < 0 || ageMonths > 24) return null

        // Find surrounding entries for interpolation
        var lower: DoubleArray? = null
        var upper: DoubleArray? = null
        for (entry in table) {
            if (entry[0] <= ageMonths) lower = entry
            if (entry[0] >= ageMonths && upper == null) upper = entry
        }
        if (lower == null) lower = table.first()
        if (upper == null) upper = table.last()
        if (lower == upper) return Triple(lower[1], lower[2], lower[3])

        // Linear interpolation
        val t = (ageMonths - lower[0]) / (upper[0] - lower[0])
        val p3 = lower[1] + t * (upper[1] - lower[1])
        val p50 = lower[2] + t * (upper[2] - lower[2])
        val p97 = lower[3] + t * (upper[3] - lower[3])
        return Triple(p3, p50, p97)
    }
}
