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

    @Query("DELETE FROM sleeps WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM sleeps")
    suspend fun getAll(): List<Sleep>
}
