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

package com.turkbot.babytracker.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks Forgejo releases for a newer APK and downloads + installs it.
 *
 * The Forgejo API endpoint is:
 *   GET /api/v1/repos/{owner}/{repo}/releases?pre-release=false
 *
 * Each release has an "assets" array; we pick the first .apk asset.
 * Version comparison is by versionName (semantic versioning).
 */
class ForgejoUpdater(
    private val context: Context,
    private val apiBase: String = FORGEJO_API_BASE,
    private val currentVersionName: String
) {

    companion object {
        // Forgejo API base for releases
        private const val FORGEJO_API_BASE =
            "https://192.168.1.172:54198/api/v1/repos/Turkey/baby-tracker-android/releases"

        // Auth token for private repo access
        private const val AUTH_TOKEN = "97c4d9d49e7c48f9d55b0e73a29baf9154359bde"

        private const val PREFS_NAME = "baby_tracker_prefs"
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val versionName: String,
        val releaseName: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val fileSize: Long
    )

    /**
     * Check the Forgejo releases API for a newer version.
     * Returns UpdateInfo if a newer version exists, null otherwise.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$apiBase?pre-release=false")
                .header("Authorization", "token $AUTH_TOKEN")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val releases = JSONObject(body)
            // If it's a single object, wrap it
            val releasesArray = if (body.trimStart().startsWith("[")) {
                org.json.JSONArray(body)
            } else {
                org.json.JSONArray().put(releases)
            }

            // Find the latest non-draft, non-prerelease release
            for (i in 0 until releasesArray.length()) {
                val release = releasesArray.getJSONObject(i)
                if (release.optBoolean("draft", false)) continue
                if (release.optBoolean("prerelease", false)) continue

                val tagName = release.optString("tag_name", "") // e.g. "v1.2.1"
                val versionName = tagName.removePrefix("v")

                // Find the first .apk asset
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val filename = asset.optString("name", "")
                    if (!filename.endsWith(".apk")) continue

                    val downloadUrl = asset.optString(
                        "api_download_url",
                        asset.optString("browser_download_url", "")
                    )
                    if (downloadUrl.isBlank()) continue

                    // Compare versions
                    if (isVersionNewer(versionName, currentVersionName)) {
                        return@withContext UpdateInfo(
                            versionName = versionName,
                            releaseName = release.optString("name", "Baby Tracker v$versionName"),
                            downloadUrl = downloadUrl,
                            releaseNotes = release.optString("body", ""),
                            fileSize = asset.optLong("size", 0)
                        )
                    }
                    // First non-draft release with an APK that isn't newer = up to date
                    return@withContext null
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Download the APK to the app's internal files directory.
     * Returns the downloaded File, or null on failure.
     */
    suspend fun downloadApk(updateInfo: UpdateInfo): File? = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "baby-tracker-update-${updateInfo.versionName}.apk")

            // If already downloaded, reuse
            if (apkFile.exists() && apkFile.length() == updateInfo.fileSize) {
                return@withContext apkFile
            }

            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("Authorization", "token $AUTH_TOKEN")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            response.body?.byteStream()?.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            apkFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Launch the system APK installer for the downloaded file.
     * Uses FileProvider to share the file with the installer.
     */
    fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    /**
     * Compare two semantic version strings.
     * Returns true if `remote` is newer than `current`.
     * e.g. "1.2.1" > "1.2.0" → true
     */
    private fun isVersionNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false // equal
    }

    // ── Auto-update preference ──────────────────────────

    fun isAutoUpdateEnabled(): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE, false)
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }
}
