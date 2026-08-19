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
 * A child being tracked. One parent creates it; it syncs via Nostr.
 */
@Serializable
@Entity(tableName = "children")
data class Child(
    @PrimaryKey
    val id: String,          // UUID
    val name: String,
    val dob: Long? = null,    // epoch millis, null if not set
    val gender: String? = null, // "boy" or "girl" — needed for WHO charts
    val createdAt: Long = System.currentTimeMillis()
)
