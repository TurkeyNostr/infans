package com.turkbot.babytracker.data.dao

import androidx.room.*
import com.turkbot.babytracker.data.entities.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC")
    fun getAll(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: ChatMessage)

    @Query("UPDATE chat_messages SET read = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE read = 0")
    fun unreadCount(): Flow<Int>
}
