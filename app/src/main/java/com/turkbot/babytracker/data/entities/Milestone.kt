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
 * A developmental milestone: first smile, first word, rolling over, etc.
 */
@Serializable
@Entity(tableName = "milestones")
data class Milestone(
    @PrimaryKey
    val id: String,
    val childId: String,
    val date: Long,           // epoch millis
    val title: String,
    val note: String? = null
)
