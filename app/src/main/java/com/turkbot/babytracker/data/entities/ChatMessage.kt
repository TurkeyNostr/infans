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

package com.turkbot.babytracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A parent-to-parent DM received via NIP-17 gift wrap.
 * Stored locally after decryption.
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey
    val id: String,           // Nostr event id
    val senderPubkey: String, // hex pubkey of sender
    val senderNpub: String,   // bech32 npub for display
    val content: String,      // decrypted plaintext
    val createdAt: Long,      // event created_at (epoch seconds → millis)
    val receivedAt: Long = System.currentTimeMillis(),
    val read: Boolean = false
)
