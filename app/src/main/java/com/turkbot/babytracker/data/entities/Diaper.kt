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
 * A diaper/nappy change record.
 * @param contents "wet", "dirty", "mixed", or "dry"
 * @param color optional — for dirty diapers: "yellow", "brown", "green", "black"
 */
@Serializable
@Entity(tableName = "diapers")
data class Diaper(
    @PrimaryKey
    val id: String,
    val childId: String,
    val time: Long,              // epoch millis
    val contents: String,        // "wet", "dirty", "mixed", "dry"
    val color: String? = null,   // stool color for dirty/mixed
    val note: String? = null
)
