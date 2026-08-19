package com.turkbot.babytracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A sleep session: start time + duration in minutes.
 */
@Entity(tableName = "sleeps")
data class Sleep(
    @PrimaryKey
    val id: String,
    val childId: String,
    val start: Long,          // epoch millis
    val duration: Int,        // minutes
    val note: String? = null
)
