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
import com.turkbot.babytracker.data.entities.Diaper
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaperDao {
    @Query("SELECT * FROM diapers WHERE childId = :childId ORDER BY time DESC")
    fun getByChild(childId: String): Flow<List<Diaper>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(diaper: Diaper)

    @Query("UPDATE diapers SET time = :time WHERE id = :id")
    suspend fun updateTime(id: String, time: Long)

    @Query("DELETE FROM diapers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM diapers")
    suspend fun getAll(): List<Diaper>
}
