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
import com.turkbot.babytracker.data.entities.HealthRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthRecordDao {
    @Query("SELECT * FROM health_records WHERE childId = :childId ORDER BY time DESC")
    fun getByChild(childId: String): Flow<List<HealthRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: HealthRecord)

    @Query("UPDATE health_records SET time = :time WHERE id = :id")
    suspend fun updateTime(id: String, time: Long)

    @Query("DELETE FROM health_records WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM health_records")
    suspend fun getAll(): List<HealthRecord>
}
