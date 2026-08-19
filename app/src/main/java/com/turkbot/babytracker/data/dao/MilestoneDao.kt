package com.turkbot.babytracker.data.dao

import androidx.room.*
import com.turkbot.babytracker.data.entities.Milestone
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestones WHERE childId = :childId ORDER BY date DESC")
    fun getByChild(childId: String): Flow<List<Milestone>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(milestone: Milestone)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM milestones")
    suspend fun getAll(): List<Milestone>
}
