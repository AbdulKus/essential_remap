package com.abdulkus.essentialremap.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.abdulkus.essentialremap.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class GitHubRelease(
    val version: String,
    val title: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
)

data class DownloadedUpdate(
    val release: GitHubRelease,
    val file: File,
)

sealed interface UpdatePromptState {
    data object Checking : UpdatePromptState
    data object None : UpdatePromptState
    data object Dismissed : UpdatePromptState
    data class Available(val release: GitHubRelease) : UpdatePromptState
    data class Downloading(val release: GitHubRelease, val progressPercent: Int?) : UpdatePromptState
    data class Ready(val update: DownloadedUpdate) : UpdatePromptState
    data class Error(val release: GitHubRelease, val message: String) : UpdatePromptState
}

class GitHubUpdateManager(context: Context) {
    private val appContext = context.applicationContext

    suspend fun checkForUpdate(): GitHubRelease? = withContext(Dispatchers.IO) {
        val connection = openConnection(LATEST_RELEASE_API)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return@withContext null

            val tag = json.optString("tag_name").trim()
            if (!UpdatePolicy.isNewerVersion(tag, BuildConfig.VERSION_NAME)) return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            var apkSize = -1L
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true) || name.contains("debug", ignoreCase = true)) continue
                val candidateUrl = asset.optString("browser_download_url")
                if (candidateUrl.isBlank()) continue
                apkUrl = candidateUrl
                apkSize = asset.optLong("size", -1L)
                break
            }
            val downloadUrl = apkUrl ?: return@withContext null
            GitHubRelease(
                version = tag.removePrefix("v").removePrefix("V"),
                title = json.optString("name").ifBlank { "Essential Remap $tag" },
                apkUrl = downloadUrl,
                apkSizeBytes = apkSize,
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(
        release: GitHubRelease,
        onProgress: (Int?) -> Unit,
    ): DownloadedUpdate = withContext(Dispatchers.IO) {
        val updatesDirectory = File(appContext.cacheDir, "updates").apply { mkdirs() }
        updatesDirectory.listFiles()?.forEach { it.delete() }

        val safeVersion = release.version.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val partialFile = File(updatesDirectory, "essential-remap-$safeVersion.apk.part")
        val targetFile = File(updatesDirectory, "essential-remap-$safeVersion.apk")
        partialFile.delete()
        targetFile.delete()

        val connection = openConnection(release.apkUrl)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_APK_BYTES) error("APK is unexpectedly large")

            connection.inputStream.use { input ->
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastReported = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) error("APK is unexpectedly large")
                        val percent = if (contentLength > 0L) {
                            ((downloaded * 100L) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }
                        if (percent == null || percent != lastReported) {
                            lastReported = percent ?: -1
                            onProgress(percent)
                        }
                    }
                    output.fd.sync()
                }
            }

            if (!partialFile.renameTo(targetFile)) {
                partialFile.copyTo(targetFile, overwrite = true)
                partialFile.delete()
            }
            verifyDownloadedApk(targetFile)
            onProgress(100)
            DownloadedUpdate(release, targetFile)
        } catch (throwable: Throwable) {
            partialFile.delete()
            targetFile.delete()
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyDownloadedApk(file: File) {
        val packageManager = appContext.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Downloaded file is not a valid APK")
        if (archive.packageName != appContext.packageName) error("Downloaded APK has a different package name")

        @Suppress("DEPRECATION")
        val current = packageManager.getPackageInfo(appContext.packageName, flags)
        if (versionCode(archive) <= versionCode(current)) error("Downloaded APK is not newer than the installed version")

        val currentSigners = signerDigests(current)
        val archiveSigners = signerDigests(archive)
        if (currentSigners.isEmpty() || currentSigners != archiveSigners) {
            error("Downloaded APK signature does not match the installed app")
        }
    }

    private fun signerDigests(info: PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Essential-Remap/${BuildConfig.VERSION_NAME}")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    private companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/AbdulKus/essential_remap/releases/latest"
        const val MAX_APK_BYTES = 150L * 1024L * 1024L
    }
}
