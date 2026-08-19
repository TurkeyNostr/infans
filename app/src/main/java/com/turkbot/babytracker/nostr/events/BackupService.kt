package com.turkbot.babytracker.nostr.events

import android.content.Context
import android.util.Log
import com.turkbot.babytracker.data.repo.BabyRepository
import com.turkbot.babytracker.data.repo.BackupPayload
import com.turkbot.babytracker.nostr.crypto.Nip44
import com.turkbot.babytracker.nostr.crypto.NostrKeyPair
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import com.turkbot.babytracker.nostr.relay.RelayPool
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Encrypted backup via Nostr kind 30078 events.
 *
 * Following Runstr's model:
 *   1. Collect all local data → JSON
 *   2. Gzip compress (NIP-44 has a 64KB payload limit)
 *   3. NIP-44 self-encrypt (encrypt to your own pubkey)
 *   4. Publish as kind 30078 with d-tag "baby-tracker-backup"
 *
 * Kind 30078 is a replaceable parameterized event — newer backups overwrite older ones on relays.
 * Only the user's nsec can decrypt the backup.
 * Cross-device: log in with your nsec on a new phone → fetch → decrypt → restore.
 */
class BackupService(
    private val context: Context,
    private val relayPool: RelayPool
) {
    private val repo = BabyRepository(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        const val BACKUP_KIND = 30078
        const val BACKUP_D_TAG = "baby-tracker-backup"
        private const val TAG = "BackupService"
    }

    /**
     * Export all data to Nostr relays as an encrypted kind 30078 event.
     */
    suspend fun export(keys: NostrKeyPair): Boolean {
        try {
            // 1. Collect all data
            val children = repo.children().first()
            val feedings = repo.allFeedings()
            val sleeps = repo.allSleeps()
            val weights = repo.allWeights()
            val milestones = repo.allMilestones()

            val payload = BackupPayload(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                children = children,
                feedings = feedings,
                sleeps = sleeps,
                weights = weights,
                milestones = milestones
            )

            // 2. Serialize to JSON
            val jsonStr = json.encodeToString(BackupPayload.serializer(), payload)

            // 3. Gzip compress
            val compressed = gzipCompress(jsonStr.toByteArray(Charsets.UTF_8))

            // 4. NIP-44 self-encrypt
            val encrypted = Nip44.selfEncrypt(
                android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP),
                keys.privateKey,
                keys.publicKey
            )

            // 5. Build and sign the kind 30078 event
            val event = NostrEvent.create(
                kind = BACKUP_KIND,
                content = encrypted,
                privKey = keys.privateKey,
                pubKey = keys.publicKey,
                tags = listOf(
                    listOf("d", BACKUP_D_TAG),
                    listOf("client", "Baby Tracker", "1.0.0"),
                    listOf("encrypted", "nip44"),
                    listOf("compression", "gzip"),
                    listOf("backup_version", "1"),
                    listOf("child_count", children.size.toString()),
                    listOf("feeding_count", feedings.size.toString()),
                    listOf("sleep_count", sleeps.size.toString()),
                    listOf("weight_count", weights.size.toString()),
                    listOf("milestone_count", milestones.size.toString())
                )
            )

            // 6. Publish to all relays
            relayPool.publish(event.toJsonObject())
            Log.d(TAG, "Backup published: ${feedings.size} feedings, ${sleeps.size} sleeps, ${weights.size} weights")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Backup export failed", e)
            return false
        }
    }

    /**
     * Fetch and decrypt the latest backup from relays.
     * Returns the decrypted BackupPayload, or null if no backup found.
     */
    suspend fun import(keys: NostrKeyPair): BackupPayload? {
        // This is handled by NostrManager which subscribes to kind 30078
        // and processes incoming events. See NostrManager.handleBackupEvent()
        return null
    }

    /**
     * Decrypt a kind 30078 event content into a BackupPayload.
     */
    fun decryptBackup(content: String, keys: NostrKeyPair): BackupPayload? {
        try {
            // 1. NIP-44 self-decrypt → base64 gzip data
            val base64Gzip = Nip44.selfDecrypt(content, keys.privateKey, keys.publicKey)

            // 2. Base64 decode → gzip bytes
            val gzipBytes = android.util.Base64.decode(base64Gzip, android.util.Base64.NO_WRAP)

            // 3. Gunzip → JSON
            val jsonBytes = gzipDecompress(gzipBytes)
            val jsonStr = String(jsonBytes, Charsets.UTF_8)

            // 4. Parse
            return json.decodeFromString(BackupPayload.serializer(), jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Backup decrypt failed", e)
            return null
        }
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        GZIPInputStream(data.inputStream()).use { input ->
            return input.readBytes()
        }
    }
}
