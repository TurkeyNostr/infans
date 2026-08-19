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

package com.turkbot.babytracker.util

/**
 * Unit conversion utilities — same conversions as the PWA.
 * 1 fl oz = 29.5735 ml
 * 1 in = 2.54 cm
 * 1 kg = 2.20462 lb
 * 1 kg = 35.274 oz
 */
object Units {

    // ── Feeding ────────────────────────────────────────
    private const val FL_OZ_TO_ML = 29.5735

    fun flOzToMl(flOz: Double): Double = flOz * FL_OZ_TO_ML
    fun mlToFlOz(ml: Double): Double = ml / FL_OZ_TO_ML

    /** Convert amount to ml for aggregation. */
    fun amountToMl(amount: Double, unit: String): Double = when (unit) {
        "ml" -> amount
        "fl_oz" -> flOzToMl(amount)
        else -> amount
    }

    /** Format amount for display in the entered unit. */
    fun fmtAmount(amount: Double, unit: String): String = when (unit) {
        "ml" -> "${amount.toInt()} ml"
        "fl_oz" -> "${"%.1f".format(amount)} fl oz"
        "min" -> "${amount.toInt()} min"
        "g" -> "${amount.toInt()} g"
        else -> amount.toString()
    }

    fun feedTypeLabel(type: String): String = when (type) {
        "bottle" -> "🍼 Bottle"
        "breast" -> "🤱 Breast"
        "solids" -> "🍽️ Solids"
        else -> type
    }

    // ── Weight ─────────────────────────────────────────
    private const val KG_TO_LB = 2.20462
    private const val KG_TO_OZ = 35.274

    fun kgToLb(kg: Double): Double = kg * KG_TO_LB
    fun kgToOz(kg: Double): Double = kg * KG_TO_OZ
    fun lbToKg(lb: Double): Double = lb / KG_TO_LB
    fun ozToKg(oz: Double): Double = oz / KG_TO_OZ

    /** Convert any weight unit to kg (internal storage). */
    fun toKg(value: Double, unit: String): Double = when (unit) {
        "kg" -> value
        "lb" -> lbToKg(value)
        "oz" -> ozToKg(value)
        else -> value
    }

    /** Format kg in the user's preferred display unit. */
    fun fromKg(kg: Double, unit: String): String = when (unit) {
        "kg" -> "${"%.1f".format(kg)} kg"
        "lb" -> "${"%.1f".format(kgToLb(kg))} lb"
        "oz" -> "${"%.0f".format(kgToOz(kg))} oz"
        else -> "${"%.1f".format(kg)} kg"
    }

    // ── Height ─────────────────────────────────────────
    private const val IN_TO_CM = 2.54

    fun inToCm(inches: Double): Double = inches * IN_TO_CM
    fun cmToIn(cm: Double): Double = cm / IN_TO_CM

    fun toCm(value: Double, unit: String): Double = when (unit) {
        "cm" -> value
        "in" -> inToCm(value)
        else -> value
    }

    fun fmtHeight(cm: Double, unit: String): String = when (unit) {
        "cm" -> "${"%.1f".format(cm)} cm"
        "in" -> "${"%.1f".format(cmToIn(cm))} in"
        else -> "${"%.1f".format(cm)} cm"
    }

    // ── Duration ───────────────────────────────────────
    fun fmtDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    // ── Age ────────────────────────────────────────────
    fun ageInMonths(dob: Long?, now: Long = System.currentTimeMillis()): Double {
        if (dob == null) return 0.0
        val diffMs = now - dob
        val days = diffMs / (1000.0 * 60 * 60 * 24)
        return days / 30.4375 // average days per month
    }

    fun ageFromDOB(dob: Long?, now: Long = System.currentTimeMillis()): String {
        if (dob == null) return "—"
        val diffMs = now - dob
        val days = (diffMs / (1000.0 * 60 * 60 * 24)).toInt()
        val months = days / 30
        val remainingDays = days % 30
        return if (months > 0) "${months}mo ${remainingDays}d" else "${days}d"
    }
}
