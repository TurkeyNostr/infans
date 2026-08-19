package com.turkbot.babytracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.turkbot.babytracker.BabyTrackerApp
import com.turkbot.babytracker.data.entities.*
import com.turkbot.babytracker.data.repo.BabyRepository
import com.turkbot.babytracker.nostr.NostrManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class BabyViewModel(
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

    val messages: StateFlow<List<ChatMessage>> = repo.messages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = repo.unreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Nostr state
    val nostrKeys = nostr.keys
    val relayConnected = nostr.relayConnected

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
    fun addWeight(valueKg: Double, unit: String, heightCm: Double?, heightUnit: String?) {
        val child = activeChild.value ?: return
        viewModelScope.launch {
            repo.saveWeight(Weight(
                id = UUID.randomUUID().toString(),
                childId = child.id,
                date = System.currentTimeMillis(),
                value = valueKg,
                unit = unit,
                height = heightCm,
                heightUnit = heightUnit
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

    // ── Nostr ─────────────────────────────────────────
    fun generateNostrIdentity() {
        viewModelScope.launch { nostr.generateIdentity() }
    }

    fun importNostrIdentity(nsec: String) {
        viewModelScope.launch { nostr.importIdentity(nsec) }
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
        return BabyViewModel(BabyRepository(app), app.nostrManager) as T
    }
}
