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
 * A weight + optional height measurement.
 * Weight stored in kg internally; height in cm.
 * Original unit preserved for display.
 */
@Serializable
@Entity(tableName = "weights")
data class Weight(
    @PrimaryKey
    val id: String,
    val childId: String,
    val date: Long,           // epoch millis
    val value: Double,        // kg
    val unit: String,         // "kg", "lb", "oz"
    val height: Double? = null,  // cm
    val heightUnit: String? = null // "cm" or "in"
)
