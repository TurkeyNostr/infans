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

package com.turkbot.babytracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.*
import com.turkbot.babytracker.BabyTrackerApp
import com.turkbot.babytracker.data.entities.*
import com.turkbot.babytracker.data.repo.BabyRepository
import com.turkbot.babytracker.nostr.NostrManager
import com.turkbot.babytracker.reminder.ReminderScheduler
import com.turkbot.babytracker.update.ForgejoUpdater
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class BabyViewModel(
    private val app: BabyTrackerApp,
    private val repo: BabyRepository,
    private val nostr: NostrManager
) : ViewModel() {

    // Active child selection
    private val _activeChildId = MutableStateFlow<String?>(null)
    val activeChildId: StateFlow<String?> = _activeChildId

    val children: StateFlow<List<Child>> = repo.children()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Auto-select first child if none selected
    val activeChild: StateFlow<Child?> = combine(children, _activeChildId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Flows for active child
    val feedings: StateFlow<List<Feeding>> = activeChild
        .filterNotNull().flatMapLatest { repo.feedings(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sleeps: StateFlow<List<Sleep>> = activeChild
        .filterNotNull().flatMapLatest { repo.sleeps(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weights: StateFlow<List<Weight>> = activeChild
        .filterNotNull().flatMapLatest { repo.weights(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val milestones: StateFlow<List<Milestone>> = activeChild
        .filterNotNull().flatMapLatest { repo.milestones(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val diapers: StateFlow<List<Diaper>> = activeChild
        .filterNotNull().flatMapLatest { repo.diapers(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pumpings: StateFlow<List<Pumping>> = activeChild
        .filterNotNull().flatMapLatest { repo.pumpings(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val healthRecords: StateFlow<List<HealthRecord>> = activeChild
        .filterNotNull().flatMapLatest { repo.healthRecords(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<ChatMessage>> = repo.messages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = repo.unreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Nostr state
    val signer = nostr.signer
    val relayConnected = nostr.relayConnected
    val partnerNpub = nostr.partnerNpub
    val partnerNip05 = nostr.partnerNip05

    // ── Child management ──────────────────────────────
    fun selectChild(id: String) { _activeChildId.value = id }

    fun addChild(name: String, dob: Long?, gender: String?) {
        viewModelScope.launch {
            val child = Child(
                id = UUID.randomUUID().toString(),
                name = name,
                dob = dob,
                gender = gender
            )
            repo.saveChild(child)
            _activeChildId.value = child.id
            nostr.exportBackup()
        }
    }

    fun deleteChild(child: Child) {
        viewModelScope.launch {
            repo.deleteChild(child)
            nostr.exportBackup()
        }
    }

    // ── Feeding ───────────────────────────────────────
    fun addFeeding(type: String, amount: Double?, unit: String?, breastSide: String?, duration: Int?, note: String?) {
        val child = activeChild.value ?: return
        viewModelScope.launch {
            repo.saveFeeding(Feeding(
                id = UUID.randomUUID().toString(),
                childId = child.id,
                time = System.currentTimeMillis(),
                type = type,
                amount = amount,
                unit = unit,
                breastSide = breastSide,
                duration = duration,
                note = note
            ))
            nostr.exportBackup()
        }
    }

    fun deleteFeeding(id: String) {
        viewModelScope.launch {
            repo.deleteFeeding(id)
            nostr.exportBackup()
        }
    }

    // ── Sleep ─────────────────────────────────────────
    fun addSleep(start: Long, duration: Int, note: String?) {
        val child = activeChild.value ?: return
        viewModelScope.launch {
            repo.saveSleep(Sleep(
                id = UUID.randomUUID().toString(),
                childId = child.id,
                start = start,
                duration = duration,
                note = note
            ))
            nostr.exportBackup()
        }
    }

    fun deleteSleep(id: String) {
        viewModelScope.launch {
            repo.deleteSleep(id)
            nostr.exportBackup()
        }
    }

    // ── Weight ────────────────────────────────────────
    fun addWeight(valueKg: Double, unit: String, heightCm: Double?, heightUnit: String?, headCircCm: Double?, headCircUnit: String?) {
        val child = activeChild.value ?: return
        viewModelScope.launch {
            repo.saveWeight(Weight(
                id = UUID.randomUUID().toString(),
                childId = child.id,
                date = System.currentTimeMillis(),
                value = valueKg,
                unit = unit,
                height = heightCm,
                heightUnit = heightUnit,
                headCirc = headCircCm,
                headCircUnit = headCircUnit
            ))
            nostr.exportBackup()
        }
    }

    fun deleteWeight(id: String) {
        viewModelScope.launch {
            repo.deleteWeight(id)
            nostr.exportBackup()
        }
    }

    // ── Milestones ────────────────────────────────────
    fun addMilestone(title: String, note: String?) {
        val child = activeChild.value ?: return
        viewModelScope.launch {
            repo.saveMilestone(Milestone(
                id = UUID.randomUUID().toString(),
                childId = child.id,
                date = System.currentTimeMillis(),
                title = title,
                note = note
            ))
            nostr.exportBackup()
        }
    }

    fun deleteMilestone(id: String) {
        viewModelScope.launch {
            repo.deleteMilestone(id)
            nostr.exportBackup()
        }
    }

    // ── Diaper ────────────────────────────────────────
    fun addDiaper(contents: String, color: String?, note: String?) {
        val child = activeChild.value ?: return
        viewModelScope.launch {
            repo.saveDiaper(Diaper(
                id = UUID.randomUUID().toString(),
                childId = child.id,
                time = System.currentTimeMillis(),
                contents = contents,
                color = color,
                note = note
            ))
            nostr.exportBackup()
        }
    }

    fun deleteDiaper(id: String) {
        viewModelScope.launch {
            repo.deleteDiaper(id)
            nostr.exportBackup()
        }
    }

    // ── Pumping ───────────────────────────────────────
    fun addPumping(amountMl: Double, unit: String, duration: Int?, side: String?, note: String?) {
        val child = activeChild.value ?: return
        viewModelScope.launch {
            repo.savePumping(Pumping(
                id = UUID.randomUUID().toString(),
                childId = child.id,
                time = System.currentTimeMillis(),
                amount = amountMl,
                unit = unit,
                duration = duration,
                side = side,
                note = note
            ))
            nostr.exportBackup()
        }
    }

    fun deletePumping(id: String) {
        viewModelScope.launch {
            repo.deletePumping(id)
            nostr.exportBackup()
        }
    }

    // ── Health records ────────────────────────────────
    fun addHealthRecord(temperature: Double?, medication: String?, dose: String?, note: String?) {
        val child = activeChild.value ?: return
        viewModelScope.launch {
            repo.saveHealthRecord(HealthRecord(
                id = UUID.randomUUID().toString(),
                childId = child.id,
                time = System.currentTimeMillis(),
                temperature = temperature,
                medication = medication,
                dose = dose,
                note = note
            ))
            nostr.exportBackup()
        }
    }

    fun deleteHealthRecord(id: String) {
        viewModelScope.launch {
            repo.deleteHealthRecord(id)
            nostr.exportBackup()
        }
    }

    // ── Nostr ─────────────────────────────────────────
    fun generateNostrIdentity() {
        viewModelScope.launch { nostr.generateIdentity() }
    }

    fun importNostrIdentity(nsec: String) {
        viewModelScope.launch { nostr.importIdentity(nsec) }
    }

    fun loginWithAmber() {
        viewModelScope.launch { nostr.loginWithAmber() }
    }

    fun clearNostrIdentity() {
        nostr.clearIdentity()
    }

    fun setPartnerNpub(npub: String?) {
        nostr.setPartnerNpub(npub)
    }

    suspend fun setPartnerIdentifier(input: String): Boolean {
        return nostr.setPartnerIdentifier(input)
    }

    // ── Reminders ──────────────────────────────────────
    fun setReminderInterval(minutes: Int) {
        viewModelScope.launch {
            if (minutes > 0) {
                ReminderScheduler.schedule(app, minutes)
            } else {
                ReminderScheduler.cancel(app)
            }
            app.getSharedPreferences("baby_tracker_prefs", Context.MODE_PRIVATE)
                .edit().putInt("reminder_interval", minutes).apply()
        }
    }

    fun isAmberInstalled(): Boolean = nostr.isAmberInstalled()

    // ── App Update ─────────────────────────────────────
    val currentVersionName: String = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    private val updater = ForgejoUpdater(
        app,
        currentVersionName = currentVersionName
    )

    private val _updateInfo = MutableStateFlow<ForgejoUpdater.UpdateInfo?>(null)
    val updateInfo: StateFlow<ForgejoUpdater.UpdateInfo?> = _updateInfo

    private val _updateChecking = MutableStateFlow(false)
    val updateChecking: StateFlow<Boolean> = _updateChecking

    private val _updateDownloading = MutableStateFlow(false)
    val updateDownloading: StateFlow<Boolean> = _updateDownloading

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateChecking.value = true
            _updateMessage.value = null
            when (val result = updater.checkForUpdate()) {
                is ForgejoUpdater.CheckResult.UpdateAvailable -> {
                    _updateInfo.value = result.info
                    _updateMessage.value = null
                }
                is ForgejoUpdater.CheckResult.UpToDate -> {
                    _updateInfo.value = null
                    _updateMessage.value = "You're up to date! (v$currentVersionName)"
                }
                is ForgejoUpdater.CheckResult.Error -> {
                    _updateInfo.value = null
                    _updateMessage.value = result.message
                }
            }
            _updateChecking.value = false
        }
    }

    fun downloadAndInstallUpdate() {
        val info = _updateInfo.value ?: return
        viewModelScope.launch {
            _updateDownloading.value = true
            _updateMessage.value = "Downloading v${info.versionName}..."
            val apkFile = updater.downloadApk(info)
            _updateDownloading.value = false
            if (apkFile != null) {
                _updateMessage.value = "Installing v${info.versionName}..."
                updater.installApk(apkFile)
            } else {
                _updateMessage.value = "Download failed — check your network connection"
            }
        }
    }

    fun isAutoUpdateEnabled(): Boolean = updater.isAutoUpdateEnabled()

    fun setAutoUpdateEnabled(enabled: Boolean) {
        updater.setAutoUpdateEnabled(enabled)
    }

    fun sendDirectMessage(text: String, recipientNpub: String) {
        viewModelScope.launch { nostr.sendMessage(text, recipientNpub) }
    }

    fun exportBackup() {
        viewModelScope.launch { nostr.exportBackup() }
    }
}

class BabyViewModelFactory(private val app: BabyTrackerApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BabyViewModel(app, BabyRepository(app), app.nostrManager) as T
    }
}
