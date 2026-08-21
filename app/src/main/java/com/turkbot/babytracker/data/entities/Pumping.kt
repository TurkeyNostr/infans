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

package com.turkbot.babytracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A pumping session record.
 * @param amount volume in ml (internal storage)
 * @param unit "ml" or "fl_oz"
 * @param duration minutes spent pumping
 * @param side "left", "right", or "both"
 */
@Serializable
@Entity(tableName = "pumpings")
data class Pumping(
    @PrimaryKey
    val id: String,
    val childId: String,
    val time: Long,              // epoch millis
    val amount: Double,          // ml
    val unit: String,            // "ml" or "fl_oz"
    val duration: Int? = null,   // minutes
    val side: String? = null,    // "left", "right", "both"
    val note: String? = null
)
