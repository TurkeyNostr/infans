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
 * A health record: temperature and/or medication dose.
 * Either field can be set independently — you might log a temp without meds, or vice versa.
 *
 * @param temperature body temperature in °C (internal storage)
 * @param medication name of medication given, null if none
 * @param dose dose amount (free text, e.g. "2.5 ml", "1 drop")
 */
@Serializable
@Entity(tableName = "health_records")
data class HealthRecord(
    @PrimaryKey
    val id: String,
    val childId: String,
    val time: Long,                    // epoch millis
    val temperature: Double? = null,   // °C
    val medication: String? = null,    // medication name
    val dose: String? = null,          // dose description
    val note: String? = null
)
