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
 * A vaccine record: type, date administered, and optional next-dose date.
 *
 * @param vaccineType e.g. "BCG", "DTaP", "MMR", "Rotavirus"
 * @param dateAdministered epoch millis when the dose was given
 * @param nextDueDate epoch millis for the next scheduled dose, null if none
 * @param doseNumber which dose in the series (1, 2, 3, booster, etc.)
 * @param note optional notes (e.g. "mild fever after")
 */
@Serializable
@Entity(tableName = "vaccines")
data class Vaccine(
    @PrimaryKey
    val id: String,
    val childId: String,
    val vaccineType: String,
    val dateAdministered: Long,
    val nextDueDate: Long? = null,
    val doseNumber: String? = null,
    val note: String? = null,
    val authorPubkey: String? = null
)
