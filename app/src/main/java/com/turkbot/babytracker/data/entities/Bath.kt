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
 * A bath record tracking when the baby was bathed.
 * @param type "full", "sponge", or "tub" — the kind of bath
 * @param note optional free-text note
 */
@Serializable
@Entity(tableName = "baths")
data class Bath(
    @PrimaryKey
    val id: String,
    val childId: String,
    val time: Long,              // epoch millis
    val type: String,            // "full", "sponge", "tub"
    val note: String? = null
)
