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
import com.turkbot.babytracker.data.entities.Pumping
import kotlinx.coroutines.flow.Flow

@Dao
interface PumpingDao {
    @Query("SELECT * FROM pumpings WHERE childId = :childId ORDER BY time DESC")
    fun getByChild(childId: String): Flow<List<Pumping>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pumping: Pumping)

    @Query("DELETE FROM pumpings WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM pumpings")
    suspend fun getAll(): List<Pumping>
}
