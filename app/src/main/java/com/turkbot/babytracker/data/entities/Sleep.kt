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

package com.turkbot.babytracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A sleep session: start time + duration in minutes.
 */
@Serializable
@Entity(tableName = "sleeps")
data class Sleep(
    @PrimaryKey
    val id: String,
    val childId: String,
    val start: Long,          // epoch millis
    val duration: Int,        // minutes
    val note: String? = null
)
