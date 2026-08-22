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
import com.turkbot.babytracker.data.entities.Bath
import kotlinx.coroutines.flow.Flow

@Dao
interface BathDao {
    @Query("SELECT * FROM baths WHERE childId = :childId ORDER BY time DESC")
    fun getByChild(childId: String): Flow<List<Bath>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bath: Bath)

    @Query("UPDATE baths SET time = :time WHERE id = :id")
    suspend fun updateTime(id: String, time: Long)

    @Query("DELETE FROM baths WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM baths")
    suspend fun getAll(): List<Bath>
}
