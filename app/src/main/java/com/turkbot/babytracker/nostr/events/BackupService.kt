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

package com.turkbot.babytracker.nostr.events

import android.content.Context
import android.util.Log
import com.turkbot.babytracker.data.repo.BabyRepository
import com.turkbot.babytracker.data.repo.BackupPayload
import com.turkbot.babytracker.nostr.crypto.NostrKeys
import com.turkbot.babytracker.nostr.crypto.NostrSigner
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
 * Only the user's nsec (or Amber) can decrypt the backup.
 * Cross-device: log in with your nsec/Amber on a new phone → fetch → decrypt → restore.
 *
 * Signing + encryption now go through [NostrSigner] (LocalSigner or AmberSigner).
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
        const val PARTNER_SYNC_D_TAG = "baby-tracker-sync"
        private const val TAG = "BackupService"
    }

    /**
     * Export all data to Nostr relays as an encrypted kind 30078 event.
     */
    suspend fun export(signer: NostrSigner): Boolean {
        try {
            val payload = collectPayload()

            // 2. Serialize to JSON
            val jsonStr = json.encodeToString(BackupPayload.serializer(), payload)

            // 3. Gzip compress
            val compressed = gzipCompress(jsonStr.toByteArray(Charsets.UTF_8))

            // 4. NIP-44 self-encrypt (encrypt to our own pubkey via signer)
            val plaintext = android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP)
            val encrypted = signer.nip44Encrypt(plaintext, signer.pubkeyHex)

            // 5. Build and sign the kind 30078 event via signer
            val event = NostrEvent.createSigned(
                kind = BACKUP_KIND,
                content = encrypted,
                signer = signer,
                tags = listOf(
                    listOf("d", BACKUP_D_TAG),
                    listOf("client", "Infans", "1.0.0"),
                    listOf("encrypted", "nip44"),
                    listOf("compression", "gzip"),
                    listOf("backup_version", "1")
                )
            )

            // 6. Publish to all relays
            relayPool.publish(event.toJsonObject())
            Log.d(TAG, "Backup published: ${payload.feedings.size} feedings, ${payload.sleeps.size} sleeps, ${payload.weights.size} weights")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Backup export failed", e)
            return false
        }
    }

    /**
     * Collect all local data into a BackupPayload.
     */
    private suspend fun collectPayload(): BackupPayload {
        val children = repo.children().first()
        val feedings = repo.allFeedings()
        val sleeps = repo.allSleeps()
        val weights = repo.allWeights()
        val milestones = repo.allMilestones()

        return BackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            children = children,
            feedings = feedings,
            sleeps = sleeps,
            weights = weights,
            milestones = milestones
        )
    }

    /**
     * Fetch and decrypt the latest backup from relays.
     * Returns the decrypted BackupPayload, or null if no backup found.
     */
    suspend fun import(signer: NostrSigner): BackupPayload? {
        // This is handled by NostrManager which subscribes to kind 30078
        // and processes incoming events. See NostrManager.handleBackupEvent()
        return null
    }

    /**
     * Decrypt a kind 30078 event content into a BackupPayload.
     */
    suspend fun decryptBackup(content: String, signer: NostrSigner): BackupPayload? {
        try {
            // 1. NIP-44 self-decrypt → base64 gzip data (via signer)
            val base64Gzip = signer.nip44Decrypt(content, signer.pubkeyHex)

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

    /**
     * Export an encrypted backup addressed to a partner (co-parent).
     *
     * The payload is the same as a self-backup, but NIP-44 encrypted to the partner's
     * pubkey instead of our own. Published as kind 30078 with:
     *   - d-tag "baby-tracker-sync" (separate replaceable address from self-backup)
     *   - p-tag = partner's pubkey (so relays index it for their subscription)
     *
     * The partner's app subscribes to kind 30078 events where they're p-tagged,
     * decrypts with their own key, and merges the data locally.
     */
    suspend fun exportToPartner(
        signer: NostrSigner,
        partnerPubkeyHex: String
    ): Boolean {
        try {
            val payload = collectPayload()

            val jsonStr = json.encodeToString(BackupPayload.serializer(), payload)
            val compressed = gzipCompress(jsonStr.toByteArray(Charsets.UTF_8))
            val plaintext = android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP)

            // Encrypt to the partner's pubkey (not our own)
            val encrypted = signer.nip44Encrypt(plaintext, partnerPubkeyHex)

            val event = NostrEvent.createSigned(
                kind = BACKUP_KIND,
                content = encrypted,
                signer = signer,
                tags = listOf(
                    listOf("d", PARTNER_SYNC_D_TAG),
                    listOf("p", partnerPubkeyHex),
                    listOf("client", "Infans", "1.0.0"),
                    listOf("encrypted", "nip44"),
                    listOf("compression", "gzip"),
                    listOf("backup_version", "1")
                )
            )

            relayPool.publish(event.toJsonObject())
            Log.d(TAG, "Partner sync published to ${partnerPubkeyHex.take(16)}...")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Partner sync export failed", e)
            return false
        }
    }

    /**
     * Decrypt a partner sync event (kind 30078 with p-tag = our pubkey).
     * Uses our own key to decrypt since the partner encrypted it to us.
     */
    suspend fun decryptPartnerBackup(
        content: String,
        signer: NostrSigner,
        partnerPubkeyHex: String
    ): BackupPayload? {
        try {
            // Partner encrypted this to our pubkey, so we decrypt with our key.
            // NIP-44 decryption uses our private key + sender's pubkey (ECDH).
            val base64Gzip = signer.nip44Decrypt(content, partnerPubkeyHex)

            val gzipBytes = android.util.Base64.decode(base64Gzip, android.util.Base64.NO_WRAP)
            val jsonBytes = gzipDecompress(gzipBytes)
            val jsonStr = String(jsonBytes, Charsets.UTF_8)

            return json.decodeFromString(BackupPayload.serializer(), jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Partner backup decrypt failed", e)
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
            val maxDecompressed = 16 * 1024 * 1024 // 16 MB cap — prevents gzip bombs
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxDecompressed) {
                    throw IllegalArgumentException("Decompressed data exceeds 16MB limit")
                }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }
}
