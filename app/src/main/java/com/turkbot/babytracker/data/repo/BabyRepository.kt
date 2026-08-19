package com.turkbot.babytracker.data.repo

import android.content.Context
import com.turkbot.babytracker.data.db.AppDatabase
import com.turkbot.babytracker.data.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Single repository for all baby-tracking data.
 * Room is the local source of truth; Nostr sync runs as a background layer.
 */
class BabyRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val childDao = db.childDao()
    private val feedingDao = db.feedingDao()
    private val sleepDao = db.sleepDao()
    private val weightDao = db.weightDao()
    private val milestoneDao = db.milestoneDao()
    private val chatDao = db.chatMessageDao()

    // ── Children ──────────────────────────────────────
    fun children(): Flow<List<Child>> = childDao.getAll()
    suspend fun getChild(id: String): Child? = childDao.getById(id)
    suspend fun saveChild(child: Child) = childDao.insert(child)
    suspend fun deleteChild(child: Child) = childDao.delete(child)

    // ── Feedings ──────────────────────────────────────
    fun feedings(childId: String): Flow<List<Feeding>> = feedingDao.getByChild(childId)
    suspend fun saveFeeding(f: Feeding) = feedingDao.insert(f)
    suspend fun deleteFeeding(id: String) = feedingDao.delete(id)
    suspend fun allFeedings(): List<Feeding> = feedingDao.getAll()

    // ── Sleep ─────────────────────────────────────────
    fun sleeps(childId: String): Flow<List<Sleep>> = sleepDao.getByChild(childId)
    suspend fun saveSleep(s: Sleep) = sleepDao.insert(s)
    suspend fun deleteSleep(id: String) = sleepDao.delete(id)
    suspend fun allSleeps(): List<Sleep> = sleepDao.getAll()

    // ── Weight ────────────────────────────────────────
    fun weights(childId: String): Flow<List<Weight>> = weightDao.getByChild(childId)
    suspend fun allWeights(childId: String): List<Weight> = weightDao.getAllByChild(childId)
    suspend fun saveWeight(w: Weight) = weightDao.insert(w)
    suspend fun deleteWeight(id: String) = weightDao.delete(id)
    suspend fun allWeights(): List<Weight> = weightDao.getAll()

    // ── Milestones ────────────────────────────────────
    fun milestones(childId: String): Flow<List<Milestone>> = milestoneDao.getByChild(childId)
    suspend fun saveMilestone(m: Milestone) = milestoneDao.insert(m)
    suspend fun deleteMilestone(id: String) = milestoneDao.delete(id)
    suspend fun allMilestones(): List<Milestone> = milestoneDao.getAll()

    // ── Chat messages ─────────────────────────────────
    fun messages(): Flow<List<ChatMessage>> = chatDao.getAll()
    suspend fun saveMessage(msg: ChatMessage) = chatDao.insert(msg)
    suspend fun markMessageRead(id: String) = chatDao.markRead(id)
    fun unreadCount(): Flow<Int> = chatDao.unreadCount()

    // ── Backup / restore helpers ──────────────────────
    suspend fun collectAllData(): BackupPayload {
        return BackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            children = childDao.getAll().first(),
            feedings = feedingDao.getAll(),
            sleeps = sleepDao.getAll(),
            weights = weightDao.getAll(),
            milestones = milestoneDao.getAll()
        )
    }
}

/**
 * The serialized payload that gets gzipped + NIP-44 encrypted + published as kind 30078.
 */
@kotlinx.serialization.Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val children: List<Child>,
    val feedings: List<Feeding>,
    val sleeps: List<Sleep>,
    val weights: List<Weight>,
    val milestones: List<Milestone>
)
