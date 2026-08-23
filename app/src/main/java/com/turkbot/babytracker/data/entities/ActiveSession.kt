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

package com.turkbot.babytracker.data.entities

import kotlinx.serialization.Serializable

/**
 * A live timer session that is synced between co-parents via Nostr.
 *
 * When one parent starts a timer (e.g. "Nap"), an [ActiveSession] is
 * NIP-44 encrypted to the partner and published as kind 30078 with
 * d-tag "baby-tracker-session-{label}". The partner's app receives it,
 * decrypts it, and shows the timer as running — derived from [startTime]
 * so it stays accurate regardless of clock drift or latency.
 *
 * Either parent can stop the timer. The stopper creates the Sleep/Feeding
 * record and publishes an empty-content event with the same d-tag to signal
 * "session ended". The other parent's app clears its local/remote timer.
 *
 * @param sessionId  unique UUID for this session (dedupe key)
 * @param label      timer label — "Sleep" or "Breast" (determines d-tag + UI screen)
 * @param childId    the child this session tracks
 * @param startTime  epoch millis when the timer was started
 * @param startedBy  pubkey hex of the parent who started it
 * @param alarmMinutes  alarm preset selected by the starter (0 = no alarm)
 */
@Serializable
data class ActiveSession(
    val sessionId: String,
    val label: String,
    val childId: String,
    val startTime: Long,
    val startedBy: String,
    val alarmMinutes: Int = 0
)
