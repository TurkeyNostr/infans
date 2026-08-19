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
 * A feeding record: bottle (ml or fl oz), breast (L/R/both + minutes), or solids.
 */
@Serializable
@Entity(tableName = "feedings")
data class Feeding(
    @PrimaryKey
    val id: String,
    val childId: String,
    val time: Long,           // epoch millis
    val type: String,         // "bottle", "breast", "solids"
    val amount: Double? = null, // ml for bottle, minutes for breast, grams for solids
    val unit: String? = null,   // "ml", "fl_oz", "min", "g"
    val breastSide: String? = null, // "left", "right", "both" — breast only
    val duration: Int? = null,     // minutes — breast only
    val note: String? = null
)
