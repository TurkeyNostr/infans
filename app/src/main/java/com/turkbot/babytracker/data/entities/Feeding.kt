package com.turkbot.babytracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A feeding record: bottle (ml or fl oz), breast (L/R/both + minutes), or solids.
 */
@Entity(tableName = "feedings")
data class Feeding(
    @PrimaryKey
    val id: String,
    val childId: String,
    val time: Long,           // epoch millis
    val type: String,         // "bottle", "breast", "solids"
    val amount: Double? = null, // ml for bottle, minutes for breast, grams for solids
    val unit: String? = null,   // "ml", "fl_oz", "min", "g"
    val breastSide: String? = null, // "left", "right", "both" — breast only
    val duration: Int? = null,     // minutes — breast only
    val note: String? = null
)
