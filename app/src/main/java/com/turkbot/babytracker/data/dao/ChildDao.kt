package com.turkbot.babytracker.data.dao

import androidx.room.*
import com.turkbot.babytracker.data.entities.Child
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY createdAt")
    fun getAll(): Flow<List<Child>>

    @Query("SELECT * FROM children WHERE id = :id")
    suspend fun getById(id: String): Child?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(child: Child)

    @Update
    suspend fun update(child: Child)

    @Delete
    suspend fun delete(child: Child)
}
