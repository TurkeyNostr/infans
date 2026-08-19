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
import com.turkbot.babytracker.data.entities.Feeding
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedingDao {
    @Query("SELECT * FROM feedings WHERE childId = :childId ORDER BY time DESC")
    fun getByChild(childId: String): Flow<List<Feeding>>

    @Query("SELECT * FROM feedings WHERE childId = :childId AND time >= :since ORDER BY time DESC")
    suspend fun getSince(childId: String, since: Long): List<Feeding>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feeding: Feeding)

    @Query("DELETE FROM feedings WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM feedings")
    suspend fun getAll(): List<Feeding>
}
