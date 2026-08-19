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
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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

    /**
     * OkHttp client that trusts the self-signed certificate on the
     * local Forgejo server.  This is safe because we only connect to
     * a known private-LAN address.
     */
    private val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }

        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
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
     * Check the Forgejo releases API for a newer version.
     */
    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$apiBase?pre-release=false")
                .header("Authorization", "token $AUTH_TOKEN")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext CheckResult.Error("Server returned HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: return@withContext CheckResult.Error("Empty response from server")

            val releasesArray = if (body.trimStart().startsWith("[")) {
                org.json.JSONArray(body)
            } else {
                org.json.JSONArray().put(JSONObject(body))
            }

            if (releasesArray.length() == 0) {
                return@withContext CheckResult.UpToDate
            }

            // Find the latest non-draft, non-prerelease release with an APK
            for (i in 0 until releasesArray.length()) {
                val release = releasesArray.getJSONObject(i)
                if (release.optBoolean("draft", false)) continue
                if (release.optBoolean("prerelease", false)) continue

                val tagName = release.optString("tag_name", "")
                val versionName = tagName.removePrefix("v")

                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val filename = asset.optString("name", "")
                    if (!filename.endsWith(".apk")) continue

                    // Forgejo serves the download via api_download_url (needs auth)
                    // or browser_download_url (public). Prefer api_download_url.
                    val downloadUrl = asset.optString(
                        "api_download_url",
                        asset.optString("browser_download_url", "")
                    )
                    if (downloadUrl.isBlank()) continue

                    return@withContext if (isVersionNewer(versionName, currentVersionName)) {
                        CheckResult.UpdateAvailable(
                            UpdateInfo(
                                versionName = versionName,
                                releaseName = release.optString("name", "Baby Tracker v$versionName"),
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
        } catch (e: javax.net.ssl.SSLException) {
            CheckResult.Error("SSL error: ${e.message}")
        } catch (e: java.net.UnknownHostException) {
            CheckResult.Error("Cannot reach server (unknown host)")
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
     * e.g. "1.3.2" > "1.2.1" → true
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
