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

import android.content.Context

/**
 * Global measurement unit preference (metric vs imperial).
 * Stored in SharedPreferences as "unit_system" = "metric" | "imperial".
 * Individual screens read this to set their default unit segments.
 */
object UnitPreferences {

    private const val PREFS_NAME = "baby_tracker_prefs"
    private const val KEY = "unit_system"

    enum class System { METRIC, IMPERIAL }

    fun getSystem(context: Context): System {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY, "metric")) {
            "imperial" -> System.IMPERIAL
            else -> System.METRIC
        }
    }

    fun setSystem(context: Context, system: System) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, system.name.lowercase()).apply()
    }

    // ── Default units per system ───────────────────────

    fun defaultFeedUnit(context: Context): String =
        if (getSystem(context) == System.IMPERIAL) "fl_oz" else "ml"

    fun defaultSolidUnit(context: Context): String =
        if (getSystem(context) == System.IMPERIAL) "oz" else "g"

    fun defaultPumpUnit(context: Context): String =
        if (getSystem(context) == System.IMPERIAL) "fl_oz" else "ml"

    fun defaultWeightUnit(context: Context): String =
        if (getSystem(context) == System.IMPERIAL) "lb" else "kg"

    fun defaultHeightUnit(context: Context): String =
        if (getSystem(context) == System.IMPERIAL) "in" else "cm"

    fun defaultTempUnit(context: Context): String =
        if (getSystem(context) == System.IMPERIAL) "F" else "C"
}
