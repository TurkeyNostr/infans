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

package com.turkbot.babytracker.data.dao

import androidx.room.*
import com.turkbot.babytracker.data.entities.Milestone
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestones WHERE childId = :childId ORDER BY date DESC")
    fun getByChild(childId: String): Flow<List<Milestone>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(milestone: Milestone)

    @Query("UPDATE milestones SET date = :date WHERE id = :id")
    suspend fun updateDate(id: String, date: Long)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM milestones")
    suspend fun getAll(): List<Milestone>
}
