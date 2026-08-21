/**
 * Baby Tracker — Native Android (Kotlin)
 *
 * A privacy-first baby tracking app with Nostr-based encrypted storage
 * and parent-to-parent notes.
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
 * A note left by one parent for the other.
 * Synced via the same encrypted kind 30078 payload as all other data —
 * no separate messaging infrastructure needed.
 */
@Serializable
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey
    val id: String,              // UUID
    val authorPubkey: String,    // hex pubkey of the note author
    val content: String,         // the note text
    val createdAt: Long,         // epoch millis when the note was written
)
