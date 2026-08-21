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

package com.turkbot.babytracker.data

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.turkbot.babytracker.data.entities.*
import com.turkbot.babytracker.data.repo.BabyRepository
import com.turkbot.babytracker.util.UnitPreferences
import com.turkbot.babytracker.util.Units
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports all baby-tracker data to a paginated PDF document.
 *
 * Uses Android's built-in [PdfDocument] — no external dependencies.
 * Each section is grouped by child, with data tables for feedings, sleeps,
 * diapers, weights, pumpings, health records, and milestones.
 */
class PdfExporter(
    private val context: Context,
    private val repo: BabyRepository
) {
    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    private val displayTempUnit = UnitPreferences.defaultTempUnit(context)

    private val pageWidth = 595  // A4 width in points (1pt = 1/72 inch)
    private val pageHeight = 842 // A4 height in points
    private val margin = 36f     // 0.5 inch margin
    private val contentWidth = pageWidth - 2 * margin

    private var y = 0f
    private var page: PdfDocument.Page? = null
    private var canvas: android.graphics.Canvas? = null
    private var doc: PdfDocument? = null

    // Paints
    private val titlePaint = android.graphics.Paint().apply {
        textSize = 22f
        isFakeBoldText = true
        isAntiAlias = true
        color = android.graphics.Color.BLACK
    }
    private val headingPaint = android.graphics.Paint().apply {
        textSize = 15f
        isFakeBoldText = true
        isAntiAlias = true
        color = android.graphics.Color.BLACK
    }
    private val bodyPaint = android.graphics.Paint().apply {
        textSize = 11f
        isAntiAlias = true
        color = android.graphics.Color.BLACK
    }
    private val mutedPaint = android.graphics.Paint().apply {
        textSize = 10f
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#666666")
    }
    private val linePaint = android.graphics.Paint().apply {
        strokeWidth = 1f
        color = android.graphics.Color.parseColor("#CCCCCC")
    }

    /**
     * Generate the PDF and return a content Uri for sharing/opening.
     */
    suspend fun export(): Uri? {
        val children = repo.childrenList()
        if (children.isEmpty()) return null

        val feedings = repo.allFeedings()
        val sleeps = repo.allSleeps()
        val weights = repo.allWeights()
        val diapers = repo.allDiapers()
        val pumpings = repo.allPumpings()
        val healthRecords = repo.allHealthRecords()
        val milestones = repo.allMilestones()

        doc = PdfDocument()
        y = margin
        newPage()

        // ── Title ──────────────────────────────────────
        drawText("Infans — Baby Tracker Export", titlePaint)
        y += 6
        drawText("Generated ${dateFmt.format(Date())}", mutedPaint)
        y += 18

        // ── Per-child sections ─────────────────────────
        for (child in children) {
            // Section heading
            checkSpace(60f)
            drawLine()
            y += 6
            drawText("Child: ${child.name}", headingPaint)
            val dob = child.dob?.let { "Born ${dateFmt.format(Date(it))}" } ?: "DOB not set"
            val gender = child.gender?.replaceFirstChar { it.uppercase() } ?: "Gender not set"
            drawText("$dob  •  $gender", mutedPaint)
            y += 12

            val childFeedings = feedings.filter { it.childId == child.id }.sortedByDescending { it.time }
            val childSleeps = sleeps.filter { it.childId == child.id }.sortedByDescending { it.start }
            val childWeights = weights.filter { it.childId == child.id }.sortedByDescending { it.date }
            val childDiapers = diapers.filter { it.childId == child.id }.sortedByDescending { it.time }
            val childPumpings = pumpings.filter { it.childId == child.id }.sortedByDescending { it.time }
            val childHealth = healthRecords.filter { it.childId == child.id }.sortedByDescending { it.time }
            val childMilestones = milestones.filter { it.childId == child.id }.sortedByDescending { it.date }

            // Feedings
            if (childFeedings.isNotEmpty()) {
                drawSection("Feedings (${childFeedings.size})")
                childFeedings.forEach { f ->
                    val time = timeFmt.format(Date(f.time))
                    val detail = when (f.type) {
                        "bottle" -> "Bottle — ${fmtAmount(f.amount, f.unit)}"
                        "breast" -> {
                            val side = f.breastSide?.replaceFirstChar { it.uppercase() } ?: "?"
                            val dur = f.duration?.let { ", $it min" } ?: ""
                            "Breast ($side$dur)"
                        }
                        "solids" -> "Solids — ${fmtAmount(f.amount, f.unit)}"
                        else -> f.type
                    }
                    val note = f.note?.let { "  •  $it" } ?: ""
                    drawRow(time, "$detail$note")
                }
            }

            // Sleeps
            if (childSleeps.isNotEmpty()) {
                drawSection("Sleep (${childSleeps.size})")
                childSleeps.forEach { s ->
                    val time = timeFmt.format(Date(s.start))
                    val hrs = s.duration / 60.0
                    val dur = if (hrs >= 1) String.format("%.1f hrs", hrs) else "${s.duration} min"
                    val note = s.note?.let { "  •  $it" } ?: ""
                    drawRow(time, "$dur$note")
                }
            }

            // Diapers
            if (childDiapers.isNotEmpty()) {
                drawSection("Diapers (${childDiapers.size})")
                childDiapers.forEach { d ->
                    val time = timeFmt.format(Date(d.time))
                    val contents = d.contents.replaceFirstChar { it.uppercase() }
                    val color = d.color?.let { ", ${it.replaceFirstChar { c -> c.uppercase() }}" } ?: ""
                    val note = d.note?.let { "  •  $it" } ?: ""
                    drawRow(time, "$contents$color$note")
                }
            }

            // Weights
            if (childWeights.isNotEmpty()) {
                drawSection("Weight & Measurements (${childWeights.size})")
                childWeights.forEach { w ->
                    val date = dateFmt.format(Date(w.date))
                    val parts = mutableListOf<String>()
                    parts.add("${fmtWeight(w.value, w.unit)}")
                    w.height?.let { parts.add("${fmtHeight(it, w.heightUnit)}") }
                    w.headCirc?.let { parts.add("Head ${fmtHeight(it, w.headCircUnit)}") }
                    drawRow(date, parts.joinToString("  •  "))
                }
            }

            // Pumping
            if (childPumpings.isNotEmpty()) {
                drawSection("Pumping (${childPumpings.size})")
                childPumpings.forEach { p ->
                    val time = timeFmt.format(Date(p.time))
                    val amt = fmtAmount(p.amount, p.unit)
                    val dur = p.duration?.let { ", $it min" } ?: ""
                    val side = p.side?.let { ", ${it.replaceFirstChar { c -> c.uppercase() }}" } ?: ""
                    val note = p.note?.let { "  •  $it" } ?: ""
                    drawRow(time, "$amt$dur$side$note")
                }
            }

            // Health
            if (childHealth.isNotEmpty()) {
                drawSection("Health (${childHealth.size})")
                childHealth.forEach { h ->
                    val time = timeFmt.format(Date(h.time))
                    val parts = mutableListOf<String>()
                    h.temperature?.let { parts.add(Units.fmtTemp(it, displayTempUnit)) }
                    h.medication?.let { parts.add("$it ${h.dose ?: ""}".trim()) }
                    val note = h.note?.let { "  •  $it" } ?: ""
                    drawRow(time, "${parts.joinToString("  •  ")}$note")
                }
            }

            // Milestones
            if (childMilestones.isNotEmpty()) {
                drawSection("Milestones (${childMilestones.size})")
                childMilestones.forEach { m ->
                    val date = dateFmt.format(Date(m.date))
                    val note = m.note?.let { "  •  $it" } ?: ""
                    drawRow(date, "${m.title}$note")
                }
            }

            y += 8
        }

        // ── Footer ─────────────────────────────────────
        checkSpace(40f)
        drawLine()
        y += 6
        drawText("Exported from Infans — ${children.size} child(ren), " +
                "${feedings.size} feedings, ${sleeps.size} sleeps, ${diapers.size} diapers, " +
                "${weights.size} measurements, ${pumpings.size} pumpings, " +
                "${healthRecords.size} health records, ${milestones.size} milestones.",
            mutedPaint)

        // Finalize
        page?.let { doc!!.finishPage(it) }

        // Write to file
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val fileName = "infans-export-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.pdf"
        val outFile = File(exportsDir, fileName)
        doc!!.writeTo(outFile.outputStream())
        doc!!.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    // ── Layout helpers ────────────────────────────────

    private fun newPage() {
        page?.let { doc!!.finishPage(it) }
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        page = doc!!.startPage(pageInfo)
        canvas = page!!.canvas
        y = margin
    }

    private fun checkSpace(needed: Float) {
        if (y + needed > pageHeight - margin) {
            newPage()
        }
    }

    private fun drawText(text: String, paint: android.graphics.Paint) {
        checkSpace(paint.textSize + 4)
        canvas!!.drawText(text, margin, y + paint.textSize, paint)
        y += paint.textSize + 4
    }

    private fun drawSection(title: String) {
        y += 6
        checkSpace(headingPaint.textSize + 10)
        drawText(title, headingPaint)
    }

    private fun drawRow(time: String, detail: String) {
        checkSpace(bodyPaint.textSize + 3)
        // Time column (left)
        canvas!!.drawText(time, margin, y + bodyPaint.textSize, mutedPaint)
        // Detail column (indented)
        val timeWidth = mutedPaint.measureText(time) + 12
        // Wrap detail if too long
        val maxWidth = contentWidth - timeWidth
        val lines = wrapText(detail, bodyPaint, maxWidth)
        lines.forEachIndexed { i, line ->
            if (i > 0) checkSpace(bodyPaint.textSize + 3)
            canvas!!.drawText(line, margin + timeWidth, y + bodyPaint.textSize, bodyPaint)
            y += bodyPaint.textSize + 3
        }
        if (lines.isEmpty()) y += bodyPaint.textSize + 3
    }

    private fun drawLine() {
        checkSpace(6f)
        canvas!!.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 6f
    }

    private fun wrapText(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidth) {
                current = StringBuilder(test)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                } else {
                    // Single word longer than maxWidth — just add it
                    lines.add(word)
                }
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    // ── Formatting helpers ────────────────────────────

    private fun fmtAmount(amount: Double?, unit: String?): String {
        if (amount == null) return ""
        return when (unit) {
            "ml" -> "${amount.toInt()} ml"
            "fl_oz" -> String.format("%.1f fl oz", amount)
            "min" -> "${amount.toInt()} min"
            "g" -> "${amount.toInt()} g"
            "oz" -> "${amount.toInt()} oz"
            else -> "$amount $unit"
        }
    }

    private fun fmtWeight(value: Double, unit: String): String {
        return when (unit) {
            "kg" -> String.format("%.2f kg", value)
            "lb" -> String.format("%.1f lb", value)
            "oz" -> String.format("%.0f oz", value)
            else -> "$value $unit"
        }
    }

    private fun fmtHeight(value: Double, unit: String?): String {
        return when (unit) {
            "cm" -> String.format("%.1f cm", value)
            "in" -> String.format("%.1f in", value)
            else -> "$value $unit"
        }
    }
}
