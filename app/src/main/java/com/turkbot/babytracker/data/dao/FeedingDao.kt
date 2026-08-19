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
