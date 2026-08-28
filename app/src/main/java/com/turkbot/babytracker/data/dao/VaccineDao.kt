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
import com.turkbot.babytracker.data.entities.Vaccine
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    @Query("SELECT * FROM vaccines WHERE childId = :childId ORDER BY dateAdministered DESC")
    fun getByChild(childId: String): Flow<List<Vaccine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vaccine: Vaccine)

    @Query("UPDATE vaccines SET dateAdministered = :date WHERE id = :id")
    suspend fun updateDate(id: String, date: Long)

    @Query("DELETE FROM vaccines WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM vaccines")
    suspend fun getAll(): List<Vaccine>
}
