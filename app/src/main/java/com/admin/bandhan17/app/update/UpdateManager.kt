package com.admin.bandhan17.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.admin.bandhan17.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class UpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    companion object {
        const val DEFAULT_REPO_OWNER = "asfakulsiam"
        const val DEFAULT_REPO_NAME = "admin-bandhan-app"
    }

    /**
     * Checks for updates across multiple redundant tiers:
     * Tier 1: GitHub API latest release (/repos/{owner}/{repo}/releases/latest)
     * Tier 2: GitHub API releases list (/repos/{owner}/{repo}/releases?per_page=10)
     * Tier 3: Direct Web redirect (/releases/latest -> /releases/tag/{tag}) [Rate-limit free]
     * Tier 4: GitHub API tags endpoint (/repos/{owner}/{repo}/tags)
     */
    fun checkForUpdates(
        owner: String = BuildConfig.GITHUB_REPO_OWNER,
        repo: String = BuildConfig.GITHUB_REPO_NAME,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): Flow<UpdateState> = flow {
        emit(UpdateState.Checking)

        val resolvedOwner = owner.trim().ifEmpty { DEFAULT_REPO_OWNER }
        val resolvedRepo = repo.trim().ifEmpty { DEFAULT_REPO_NAME }

        var updateInfo: UpdateInfo? = null

        // ---------------------------------------------------------------------
        // Tier 1: Query GitHub API /releases/latest
        // ---------------------------------------------------------------------
        try {
            val latestUrl = "https://api.github.com/repos/$resolvedOwner/$resolvedRepo/releases/latest"
            val requestLatest = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "AdminBandhan17-Android-App")
                .get()
                .build()

            client.newCall(requestLatest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    if (bodyString.isNotBlank()) {
                        val json = JSONObject(bodyString)
                        updateInfo = parseReleaseJsonObject(json, currentVersion, resolvedOwner, resolvedRepo)
                    }
                }
            }
        } catch (_: Exception) {
            // Proceed to Tier 2
        }

        // ---------------------------------------------------------------------
        // Tier 2: Query GitHub API /releases list (for pre-releases / tag drafts)
        // ---------------------------------------------------------------------
        if (updateInfo == null) {
            try {
                val listUrl = "https://api.github.com/repos/$resolvedOwner/$resolvedRepo/releases?per_page=10"
                val requestList = Request.Builder()
                    .url(listUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "AdminBandhan17-Android-App")
                    .get()
                    .build()

                client.newCall(requestList).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string().orEmpty()
                        if (bodyString.isNotBlank()) {
                            val array = JSONArray(bodyString)
                            if (array.length() > 0) {
                                for (i in 0 until array.length()) {
                                    val relObj = array.optJSONObject(i) ?: continue
                                    val parsed = parseReleaseJsonObject(relObj, currentVersion, resolvedOwner, resolvedRepo)
                                    if (parsed != null) {
                                        updateInfo = parsed
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Proceed to Tier 3
            }
        }

        // ---------------------------------------------------------------------
        // Tier 3: Direct Web Redirect Check (Bypasses GitHub API 60 req/hr rate limits)
        // ---------------------------------------------------------------------
        if (updateInfo == null) {
            try {
                val webLatestUrl = "https://github.com/$resolvedOwner/$resolvedRepo/releases/latest"
                val requestWeb = Request.Builder()
                    .url(webLatestUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .get()
                    .build()

                client.newCall(requestWeb).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    if (finalUrl.contains("/releases/tag/")) {
                        val tag = finalUrl.substringAfter("/releases/tag/").substringBefore("/").substringBefore("?").trim()
                        if (tag.isNotEmpty()) {
                            val isNewer = VersionComparator.isNewerVersion(tag, currentVersion)
                            updateInfo = UpdateInfo(
                                latestVersionName = tag,
                                currentVersionName = currentVersion,
                                isUpdateAvailable = isNewer,
                                releaseTitle = "Admin Bandhan 17 $tag",
                                releaseNotes = "Admin Bandhan 17 $tag update is available with security, branding, and performance optimizations.",
                                apkDownloadUrl = "https://github.com/$resolvedOwner/$resolvedRepo/releases/download/$tag/AdminBandhan17-$tag.apk",
                                apkFileName = "AdminBandhan17-$tag.apk",
                                apkSizeBytes = 16 * 1024 * 1024L,
                                publishedAt = "Latest Release",
                                htmlUrl = "https://github.com/$resolvedOwner/$resolvedRepo/releases/tag/$tag"
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Proceed to Tier 4
            }
        }

        // ---------------------------------------------------------------------
        // Tier 4: Query GitHub API /tags
        // ---------------------------------------------------------------------
        if (updateInfo == null) {
            try {
                val tagsUrl = "https://api.github.com/repos/$resolvedOwner/$resolvedRepo/tags?per_page=5"
                val requestTags = Request.Builder()
                    .url(tagsUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "AdminBandhan17-Android-App")
                    .get()
                    .build()

                client.newCall(requestTags).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string().orEmpty()
                        if (bodyString.isNotBlank()) {
                            val array = JSONArray(bodyString)
                            if (array.length() > 0) {
                                val firstTagObj = array.optJSONObject(0)
                                val tagName = firstTagObj?.optString("name", "")?.trim().orEmpty()
                                if (tagName.isNotEmpty()) {
                                    val isNewer = VersionComparator.isNewerVersion(tagName, currentVersion)
                                    updateInfo = UpdateInfo(
                                        latestVersionName = tagName,
                                        currentVersionName = currentVersion,
                                        isUpdateAvailable = isNewer,
                                        releaseTitle = "Admin Bandhan 17 $tagName",
                                        releaseNotes = "New release $tagName available.",
                                        apkDownloadUrl = "https://github.com/$resolvedOwner/$resolvedRepo/releases/download/$tagName/AdminBandhan17-$tagName.apk",
                                        apkFileName = "AdminBandhan17-$tagName.apk",
                                        apkSizeBytes = 16 * 1024 * 1024L,
                                        publishedAt = "Latest Tag",
                                        htmlUrl = "https://github.com/$resolvedOwner/$resolvedRepo/releases/tag/$tagName"
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                emit(
                    UpdateState.Error(
                        message = "Network error while checking updates: ${e.localizedMessage ?: "Connection timed out"}",
                        isNetworkError = true
                    )
                )
                return@flow
            } catch (_: Exception) {}
        }

        // ---------------------------------------------------------------------
        // Final State Evaluation
        // ---------------------------------------------------------------------
        if (updateInfo != null) {
            if (updateInfo!!.isUpdateAvailable) {
                emit(UpdateState.UpdateAvailable(updateInfo!!))
            } else {
                emit(UpdateState.UpToDate(currentVersion, System.currentTimeMillis()))
            }
        } else {
            // Default to UpToDate if repository had zero accessible tags
            emit(UpdateState.UpToDate(currentVersion, System.currentTimeMillis()))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseReleaseJsonObject(
        releaseObj: JSONObject,
        currentVersion: String,
        owner: String,
        repo: String
    ): UpdateInfo? {
        val tagName = releaseObj.optString("tag_name", "").trim()
        if (tagName.isBlank()) return null

        val releaseName = releaseObj.optString("name", tagName)
        val bodyMarkdown = releaseObj.optString("body", "No release notes provided.")
        val publishedAt = releaseObj.optString("published_at", "")
        val htmlUrl = releaseObj.optString("html_url", "https://github.com/$owner/$repo/releases/tag/$tagName")

        // Parse assets for .apk file
        val assetsJson: JSONArray? = releaseObj.optJSONArray("assets")
        var apkAsset: GitHubReleaseAsset? = null

        if (assetsJson != null) {
            for (i in 0 until assetsJson.length()) {
                val assetObj = assetsJson.optJSONObject(i) ?: continue
                val assetName = assetObj.optString("name", "")
                val downloadUrl = assetObj.optString("browser_download_url", "")
                val size = assetObj.optLong("size", 0L)
                val contentType = assetObj.optString("content_type", "")

                if (assetName.endsWith(".apk", ignoreCase = true) ||
                    downloadUrl.endsWith(".apk", ignoreCase = true)
                ) {
                    apkAsset = GitHubReleaseAsset(
                        name = assetName,
                        size = size,
                        browserDownloadUrl = downloadUrl,
                        contentType = contentType
                    )
                    break
                }
            }
        }

        val apkDownloadUrl = apkAsset?.browserDownloadUrl
            ?: "https://github.com/$owner/$repo/releases/download/$tagName/AdminBandhan17-$tagName.apk"
        val apkFileName = apkAsset?.name
            ?: "AdminBandhan17-$tagName.apk"
        val apkSize = if ((apkAsset?.size ?: 0L) > 0L) apkAsset!!.size else 16 * 1024 * 1024L

        val isNewer = VersionComparator.isNewerVersion(tagName, currentVersion)

        return UpdateInfo(
            latestVersionName = tagName,
            currentVersionName = currentVersion,
            isUpdateAvailable = isNewer,
            releaseTitle = if (releaseName.isNotBlank()) releaseName else "Admin Bandhan 17 $tagName",
            releaseNotes = bodyMarkdown,
            apkDownloadUrl = apkDownloadUrl,
            apkFileName = apkFileName,
            apkSizeBytes = apkSize,
            publishedAt = formatDate(publishedAt),
            htmlUrl = htmlUrl
        )
    }

    /**
     * Downloads the APK file with progress reporting and automatic fallback endpoints.
     */
    fun downloadApk(
        context: Context,
        updateInfo: UpdateInfo
    ): Flow<UpdateState> = flow {
        emit(
            UpdateState.Downloading(
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = updateInfo.apkSizeBytes,
                updateInfo = updateInfo
            )
        )

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val cleanVersion = updateInfo.latestVersionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val targetFile = File(updatesDir, "AdminBandhan17_$cleanVersion.apk")

        // Primary and fallback download candidate URLs
        val candidateUrls = listOfNotNull(
            updateInfo.apkDownloadUrl,
            "https://github.com/$DEFAULT_REPO_OWNER/$DEFAULT_REPO_NAME/releases/download/${updateInfo.latestVersionName}/AdminBandhan17-latest.apk",
            "https://github.com/$DEFAULT_REPO_OWNER/$DEFAULT_REPO_NAME/releases/latest/download/AdminBandhan17-latest.apk"
        ).distinct()

        var downloadedSuccessfully = false
        var lastErrorMessage = "Failed to download update APK"

        for (url in candidateUrls) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AdminBandhan17-Android-App")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastErrorMessage = "HTTP ${response.code} downloading from $url"
                        return@use
                    }

                    val body = response.body
                    if (body == null) {
                        lastErrorMessage = "Empty body stream"
                        return@use
                    }

                    val contentLength = body.contentLength().let {
                        if (it > 0) it else updateInfo.apkSizeBytes
                    }

                    body.byteStream().use { inputStream ->
                        FileOutputStream(targetFile).use { outputStream ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            var lastEmittedPercent = -1

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (!currentCoroutineContext().isActive) {
                                    targetFile.delete()
                                    throw CancellationException("Download cancelled")
                                }

                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val percent = if (contentLength > 0) {
                                    ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }

                                if (percent != lastEmittedPercent || totalBytesRead == contentLength) {
                                    lastEmittedPercent = percent
                                    emit(
                                        UpdateState.Downloading(
                                            progressPercent = percent,
                                            downloadedBytes = totalBytesRead,
                                            totalBytes = contentLength,
                                            updateInfo = updateInfo
                                        )
                                    )
                                }
                            }
                            outputStream.flush()
                        }
                    }

                    if (targetFile.exists() && targetFile.length() > 0) {
                        downloadedSuccessfully = true
                    }
                }
            } catch (e: CancellationException) {
                targetFile.delete()
                emit(UpdateState.Idle)
                return@flow
            } catch (e: IOException) {
                lastErrorMessage = e.localizedMessage ?: "Network connection lost"
            } catch (e: Exception) {
                lastErrorMessage = e.localizedMessage ?: "Download error"
            }

            if (downloadedSuccessfully) {
                break
            }
        }

        if (downloadedSuccessfully) {
            emit(UpdateState.Downloaded(targetFile, updateInfo))
        } else {
            targetFile.delete()
            emit(
                UpdateState.Error(
                    message = lastErrorMessage,
                    isNetworkError = true
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Checks if the app has permission to install unknown apps (Android 8.0+).
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens the Unknown App Install permission settings for this package.
     */
    fun openUnknownAppInstallSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    /**
     * Launches the Android Package Installer for the downloaded APK file.
     */
    fun launchPackageInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) return false

        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun formatDate(isoString: String): String {
        if (isoString.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = parser.parse(isoString)
            if (date != null) formatter.format(date) else isoString
        } catch (_: Exception) {
            isoString.substringBefore("T")
        }
    }
}
