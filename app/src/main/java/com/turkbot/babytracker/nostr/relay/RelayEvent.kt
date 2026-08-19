package com.turkbot.babytracker.nostr.relay

import kotlinx.serialization.json.*

/**
 * Lightweight event model parsed from relay WebSocket messages.
 */
data class RelayEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
) {
    companion object {
        fun fromJsonObject(obj: JsonObject): RelayEvent {
            return RelayEvent(
                id = obj["id"]!!.jsonPrimitive.content,
                pubkey = obj["pubkey"]!!.jsonPrimitive.content,
                createdAt = obj["created_at"]!!.jsonPrimitive.long,
                kind = obj["kind"]!!.jsonPrimitive.int,
                tags = obj["tags"]?.jsonArray?.map { row ->
                    row.jsonArray.map { e -> e.jsonPrimitive.content }
                } ?: emptyList(),
                content = obj["content"]?.jsonPrimitive?.content ?: "",
                sig = obj["sig"]!!.jsonPrimitive.content
            )
        }
    }
}
