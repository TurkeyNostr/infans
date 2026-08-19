package com.turkbot.babytracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A developmental milestone: first smile, first word, rolling over, etc.
 */
@Entity(tableName = "milestones")
data class Milestone(
    @PrimaryKey
    val id: String,
    val childId: String,
    val date: Long,           // epoch millis
    val title: String,
    val note: String? = null
)
