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

package com.turkbot.babytracker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub releases for a newer APK and downloads + installs it.
 *
 * Uses the public GitHub REST API (no authentication required for public repos):
 *   GET https://api.github.com/repos/{owner}/{repo}/releases/latest
 *
 * Each release has an "assets" array; we pick the first .apk asset.
 * The asset's "browser_download_url" is a direct public download link.
 * Version comparison is by versionName (semantic versioning).
 */
class AppUpdater(
    private val context: Context,
    private val apiBase: String = GITHUB_API_BASE,
    private val currentVersionName: String
) {

    companion object {
        // GitHub public API — no auth token needed for public repos
        private const val GITHUB_API_BASE =
            "https://api.github.com/repos/TurkeyNostr/infans/releases"

        private const val PREFS_NAME = "baby_tracker_prefs"
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
    }

    /**
     * Standard OkHttp client — GitHub uses valid public TLS certificates.
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    data class UpdateInfo(
        val versionName: String,
        val releaseName: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val fileSize: Long
    )

    /**
     * Result of an update check — distinguishes "up to date" from
     * "network error" so the UI can give the user proper feedback.
     */
    sealed class CheckResult {
        object UpToDate : CheckResult()
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    /**
     * Check GitHub releases for a newer version.
     * Tries /releases/latest first, then falls back to listing all releases
     * (including prereleases — this is a beta app) and picks the first non-draft.
     */
    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        try {
            // Try the "latest" endpoint first (excludes prereleases)
            var request = Request.Builder()
                .url("$apiBase/latest")
                .header("Accept", "application/vnd.github+json")
                .build()

            var response = client.newCall(request).execute()
            var body: String? = null

            if (response.isSuccessful) {
                body = response.body?.string()
            } else if (response.code == 404) {
                // No "latest" release (all are prereleases) — list all releases
                response.close()
                request = Request.Builder()
                    .url("$apiBase?per_page=10")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    body = response.body?.string()
                }
            }

            if (body.isNullOrBlank()) {
                return@withContext CheckResult.Error("Server returned HTTP ${response.code}")
            }
            response.close()

            // Parse — either a single release object or an array
            val releasesList = mutableListOf<JSONObject>()
            if (body.trimStart().startsWith("[")) {
                val arr = org.json.JSONArray(body)
                for (i in 0 until arr.length()) {
                    releasesList.add(arr.getJSONObject(i))
                }
            } else {
                releasesList.add(JSONObject(body))
            }

            if (releasesList.isEmpty()) {
                return@withContext CheckResult.UpToDate
            }

            // Find the latest non-draft release with an APK
            // (includes prereleases — this is a beta app)
            for (release in releasesList) {
                if (release.optBoolean("draft", false)) continue

                val tagName = release.optString("tag_name", "")
                val versionName = tagName.removePrefix("v")
                if (versionName.isBlank()) continue

                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val filename = asset.optString("name", "")
                    if (!filename.endsWith(".apk")) continue

                    // GitHub's browser_download_url is a direct public CDN link — no auth needed
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (downloadUrl.isBlank()) continue

                    return@withContext if (isVersionNewer(versionName, currentVersionName)) {
                        CheckResult.UpdateAvailable(
                            UpdateInfo(
                                versionName = versionName,
                                releaseName = release.optString("name", "Infans v$versionName"),
                                downloadUrl = downloadUrl,
                                releaseNotes = release.optString("body", ""),
                                fileSize = asset.optLong("size", 0)
                            )
                        )
                    } else {
                        CheckResult.UpToDate
                    }
                }
            }
            CheckResult.UpToDate
        } catch (e: java.net.UnknownHostException) {
            CheckResult.Error("Cannot reach GitHub (no network)")
        } catch (e: java.net.SocketTimeoutException) {
            CheckResult.Error("Connection timed out — check your network")
        } catch (e: java.net.ConnectException) {
            CheckResult.Error("Cannot connect to update server")
        } catch (e: Exception) {
            CheckResult.Error("Update check failed: ${e.message}")
        }
    }

    /**
     * Download the APK to the app's internal files directory.
     * Returns the downloaded File, or null on failure.
     */
    suspend fun downloadApk(updateInfo: UpdateInfo): File? = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "infans-update-${updateInfo.versionName}.apk")

            // If already downloaded and size matches, reuse
            if (apkFile.exists() && apkFile.length() == updateInfo.fileSize) {
                return@withContext apkFile
            }

            // GitHub download URLs are public — no auth header needed
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("Accept", "application/octet-stream")
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
     * Requires REQUEST_INSTALL_PACKAGES permission (Android 8+).
     */
    fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }

        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }

    /**
     * Compare two semantic version strings.
     * Returns true if `remote` is newer than `current`.
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
