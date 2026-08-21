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
    private val noteDao = db.noteDao()
    private val diaperDao = db.diaperDao()
    private val pumpingDao = db.pumpingDao()
    private val healthRecordDao = db.healthRecordDao()

    // ── Children ──────────────────────────────────────
    fun children(): Flow<List<Child>> = childDao.getAll()
    suspend fun childrenList(): List<Child> = childDao.getAll().first()
    suspend fun getChild(id: String): Child? = childDao.getById(id)
    suspend fun saveChild(child: Child) = childDao.insert(child)
    suspend fun deleteChild(child: Child) = childDao.delete(child)

    // ── Feedings ──────────────────────────────────────
    fun feedings(childId: String): Flow<List<Feeding>> = feedingDao.getByChild(childId)
    suspend fun saveFeeding(f: Feeding) = feedingDao.insert(f)
    suspend fun updateFeedingTime(id: String, time: Long) = feedingDao.updateTime(id, time)
    suspend fun updateFeedingFields(id: String, amount: Double?, unit: String?, breastSide: String?, duration: Int?, note: String?) =
        feedingDao.updateFields(id, amount, unit, breastSide, duration, note)
    suspend fun deleteFeeding(id: String) = feedingDao.delete(id)
    suspend fun allFeedings(): List<Feeding> = feedingDao.getAll()

    // ── Sleep ─────────────────────────────────────────
    fun sleeps(childId: String): Flow<List<Sleep>> = sleepDao.getByChild(childId)
    suspend fun saveSleep(s: Sleep) = sleepDao.insert(s)
    suspend fun updateSleepStart(id: String, start: Long) = sleepDao.updateStart(id, start)
    suspend fun deleteSleep(id: String) = sleepDao.delete(id)
    suspend fun allSleeps(): List<Sleep> = sleepDao.getAll()

    // ── Weight ────────────────────────────────────────
    fun weights(childId: String): Flow<List<Weight>> = weightDao.getByChild(childId)
    suspend fun allWeights(childId: String): List<Weight> = weightDao.getAllByChild(childId)
    suspend fun saveWeight(w: Weight) = weightDao.insert(w)
    suspend fun updateWeightDate(id: String, date: Long) = weightDao.updateDate(id, date)
    suspend fun deleteWeight(id: String) = weightDao.delete(id)
    suspend fun allWeights(): List<Weight> = weightDao.getAll()

    // ── Milestones ────────────────────────────────────
    fun milestones(childId: String): Flow<List<Milestone>> = milestoneDao.getByChild(childId)
    suspend fun saveMilestone(m: Milestone) = milestoneDao.insert(m)
    suspend fun updateMilestoneDate(id: String, date: Long) = milestoneDao.updateDate(id, date)
    suspend fun deleteMilestone(id: String) = milestoneDao.delete(id)
    suspend fun allMilestones(): List<Milestone> = milestoneDao.getAll()

    // ── Notes ──────────────────────────────────────────
    fun notes(): Flow<List<Note>> = noteDao.getAll()
    suspend fun saveNote(note: Note) = noteDao.insert(note)
    suspend fun noteExists(id: String): Boolean = noteDao.exists(id) > 0
    suspend fun deleteNote(note: Note) = noteDao.delete(note)
    suspend fun allNotes(): List<Note> = noteDao.getAllList()

    // ── Diapers ──────────────────────────────────────
    fun diapers(childId: String): Flow<List<Diaper>> = diaperDao.getByChild(childId)
    suspend fun saveDiaper(d: Diaper) = diaperDao.insert(d)
    suspend fun updateDiaperTime(id: String, time: Long) = diaperDao.updateTime(id, time)
    suspend fun deleteDiaper(id: String) = diaperDao.delete(id)
    suspend fun allDiapers(): List<Diaper> = diaperDao.getAll()

    // ── Pumping ───────────────────────────────────────
    fun pumpings(childId: String): Flow<List<Pumping>> = pumpingDao.getByChild(childId)
    suspend fun savePumping(p: Pumping) = pumpingDao.insert(p)
    suspend fun updatePumpingTime(id: String, time: Long) = pumpingDao.updateTime(id, time)
    suspend fun deletePumping(id: String) = pumpingDao.delete(id)
    suspend fun allPumpings(): List<Pumping> = pumpingDao.getAll()

    // ── Health records ────────────────────────────────
    fun healthRecords(childId: String): Flow<List<HealthRecord>> = healthRecordDao.getByChild(childId)
    suspend fun saveHealthRecord(r: HealthRecord) = healthRecordDao.insert(r)
    suspend fun updateHealthRecordTime(id: String, time: Long) = healthRecordDao.updateTime(id, time)
    suspend fun deleteHealthRecord(id: String) = healthRecordDao.delete(id)
    suspend fun allHealthRecords(): List<HealthRecord> = healthRecordDao.getAll()

    // ── Backup / restore helpers ──────────────────────
    suspend fun collectAllData(): BackupPayload {
        return BackupPayload(
            version = 3,
            exportedAt = System.currentTimeMillis(),
            children = childDao.getAll().first(),
            feedings = feedingDao.getAll(),
            sleeps = sleepDao.getAll(),
            weights = weightDao.getAll(),
            milestones = milestoneDao.getAll(),
            diapers = diaperDao.getAll(),
            pumpings = pumpingDao.getAll(),
            healthRecords = healthRecordDao.getAll(),
            notes = noteDao.getAllList()
        )
    }
}

/**
 * The serialized payload that gets gzipped + NIP-44 encrypted + published as kind 30078.
 */
@kotlinx.serialization.Serializable
data class BackupPayload(
    val version: Int = 3,
    val exportedAt: Long,
    val children: List<Child>,
    val feedings: List<Feeding>,
    val sleeps: List<Sleep>,
    val weights: List<Weight>,
    val milestones: List<Milestone>,
    val diapers: List<Diaper> = emptyList(),
    val pumpings: List<Pumping> = emptyList(),
    val healthRecords: List<HealthRecord> = emptyList(),
    val notes: List<Note> = emptyList()
)
