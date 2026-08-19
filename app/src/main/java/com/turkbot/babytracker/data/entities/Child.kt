package com.turkbot.babytracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A child being tracked. One parent creates it; it syncs via Nostr.
 */
@Entity(tableName = "children")
data class Child(
    @PrimaryKey
    val id: String,          // UUID
    val name: String,
    val dob: Long? = null,    // epoch millis, null if not set
    val gender: String? = null, // "boy" or "girl" — needed for WHO charts
    val createdAt: Long = System.currentTimeMillis()
)
