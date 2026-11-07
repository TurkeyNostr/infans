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

package com.turkbot.babytracker

import android.content.Context

/**
 * Simple UI-level preferences that have no reason to be encrypted
 * (screenshot protection toggle, etc.).
 */
object UiPrefs {

    private const val PREF_FILE = "baby_tracker_prefs"
    private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection"

    /**
     * When true, FLAG_SECURE blocks screenshots, screen recording, and the
     * recents-screen preview for this app. Default ON — baby data should not
     * land in screenshots or the system recents thumbnail by accident.
     */
    fun screenshotProtectionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCREENSHOT_PROTECTION, true)

    fun setScreenshotProtection(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCREENSHOT_PROTECTION, enabled)
            .apply()
    }
}
