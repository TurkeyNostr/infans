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

package com.turkbot.babytracker.data.dao

import androidx.room.*
import com.turkbot.babytracker.data.entities.Sleep
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleeps WHERE childId = :childId ORDER BY start DESC")
    fun getByChild(childId: String): Flow<List<Sleep>>

    @Query("SELECT * FROM sleeps WHERE childId = :childId AND start >= :since ORDER BY start DESC")
    suspend fun getSince(childId: String, since: Long): List<Sleep>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sleep: Sleep)

    @Query("UPDATE sleeps SET start = :start WHERE id = :id")
    suspend fun updateStart(id: String, start: Long)

    @Query("DELETE FROM sleeps WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM sleeps")
    suspend fun getAll(): List<Sleep>
}
