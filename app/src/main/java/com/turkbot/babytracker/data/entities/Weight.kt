package com.turkbot.babytracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A weight + optional height measurement.
 * Weight stored in kg internally; height in cm.
 * Original unit preserved for display.
 */
@Entity(tableName = "weights")
data class Weight(
    @PrimaryKey
    val id: String,
    val childId: String,
    val date: Long,           // epoch millis
    val value: Double,        // kg
    val unit: String,         // "kg", "lb", "oz"
    val height: Double? = null,  // cm
    val heightUnit: String? = null // "cm" or "in"
)
