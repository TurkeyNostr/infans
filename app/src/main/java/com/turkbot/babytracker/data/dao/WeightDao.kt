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
import com.turkbot.babytracker.data.entities.Weight
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights WHERE childId = :childId ORDER BY date DESC")
    fun getByChild(childId: String): Flow<List<Weight>>

    @Query("SELECT * FROM weights WHERE childId = :childId ORDER BY date DESC")
    suspend fun getAllByChild(childId: String): List<Weight>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weight: Weight)

    @Query("DELETE FROM weights WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM weights")
    suspend fun getAll(): List<Weight>
}
