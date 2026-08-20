// SPDX-License-Identifier: MIT
// Copyright (c) 2025 TurkeyNostr
// Infans — Baby Tracker
//
// PII-free in-app diagnostic logger. Captures structured log entries for the
// Debug Log screen in Settings. By design, callers must NEVER pass npubs,
// pubkey hex, NIP-05 addresses, child names, decrypted payloads, or care-event
// timestamps. Identifiers are reduced to opaque 8-char hashes or category labels
// ("partner", "self") before they reach this logger.

package com.turkbot.babytracker.debug

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {

    enum class Level { INFO, WARN, ERROR }
    enum class Category { RELAY, SYNC, AMBER, DM, NIP05, GENERAL }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val category: Category,
        val message: String
    )

    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(level: Level, category: Category, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, category, message)
        val current = _entries.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_ENTRIES) {
            _entries.value = current.takeLast(MAX_ENTRIES)
        } else {
            _entries.value = current
        }
    }

    fun info(category: Category, message: String) = log(Level.INFO, category, message)
    fun warn(category: Category, message: String) = log(Level.WARN, category, message)
    fun error(category: Category, message: String) = log(Level.ERROR, category, message)

    /** Convenience for exception logging — records class name only, not the
     *  message text (which may contain PII from a relay or signer). */
    fun exception(category: Category, context: String, e: Throwable) {
        log(Level.ERROR, category, "$context — ${e.javaClass.simpleName}")
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** Export the log to a text file and return a content Uri for sharing. */
    fun export(context: Context): Uri? {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "infans_debug_log_${System.currentTimeMillis()}.txt")
        // Include the app version in the export header so pasted logs
        // always identify which build produced them.
        val versionHeader = try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pkgInfo.versionName ?: "?"
            val vCode = pkgInfo.longVersionCode
            "Infans v$vName ($vCode)"
        } catch (e: Exception) {
            "Infans (version unavailable)"
        }
        file.printWriter().use { writer ->
            writer.println("Infans Debug Log — exported ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            writer.println(versionHeader)
            writer.println("Total entries: ${_entries.value.size}")
            writer.println("PII-free by construction: no npubs, pubkeys, NIP-05s, child names, or decrypted data")
            writer.println()
            _entries.value.forEach { entry ->
                val time = timeFmt.format(Date(entry.timestamp))
                val levelStr = entry.level.name.padEnd(5)
                val catStr = entry.category.name.padEnd(8)
                writer.println("$time  $levelStr $catStr  ${entry.message}")
            }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
