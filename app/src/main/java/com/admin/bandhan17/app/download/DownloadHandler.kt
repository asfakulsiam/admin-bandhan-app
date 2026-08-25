package com.admin.bandhan17.app.download

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.admin.bandhan17.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Universal Handler for all WebView file downloads:
 * 1. Supports ANY file format/extension (mp4, mp3, zip, rar, xlsx, docx, pdf, apk, images, etc.)
 * 2. Real-time Status Bar / Notification Panel progress updates
 * 3. Interactive Download Completed notifications with "Open" and "Share" actions
 * 4. In-App Open and Share functionality
 * 5. Modern MediaStore & Scoped Storage compliance (Android 10+) with legacy fallback
 */
object DownloadHandler {

    const val DOWNLOAD_CHANNEL_ID = "bandhan_downloads_channel"
    const val DOWNLOAD_CHANNEL_NAME = "Bandhan Downloads"

    // Map of common extensions to ensure foolproof MIME type resolution
    private val EXTENSION_TO_MIME = mapOf(
        // Video
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "webm" to "video/webm",
        "3gp" to "video/3gpp",
        "flv" to "video/x-flv",
        "wmv" to "video/x-ms-wmv",
        // Audio
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "flac" to "audio/flac",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        // Archives
        "zip" to "application/zip",
        "rar" to "application/x-rar-compressed",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        // Documents & Spreadsheets
        "pdf" to "application/pdf",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xls" to "application/vnd.ms-excel",
        "csv" to "text/csv",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "doc" to "application/msword",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "ppt" to "application/vnd.ms-powerpoint",
        "txt" to "text/plain",
        "rtf" to "application/rtf",
        // Images
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        // Packages & Code
        "apk" to "application/vnd.android.package-archive",
        "json" to "application/json",
        "xml" to "application/xml"
    )

    /**
     * Resolves the filename for any download based on URL, Content-Disposition header, and MIME type.
     */
    fun resolveFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        // 1. Try extracting filename from Content-Disposition header (RFC 6266 & RFC 5987)
        if (!contentDisposition.isNullOrBlank()) {
            val rfc5987Pattern = Regex("""filename\*=UTF-8''([^;\r\n]+)""", RegexOption.IGNORE_CASE)
            rfc5987Pattern.find(contentDisposition)?.let { match ->
                val matched = match.groupValues[1].trim('"', '\'')
                val raw = try {
                    java.net.URLDecoder.decode(matched, "UTF-8")
                } catch (_: Exception) {
                    matched
                }
                if (!raw.isNullOrBlank()) return sanitizeFileName(raw)
            }

            val standardPattern = Regex("""filename=["']?([^"';\r\n]+)["']?""", RegexOption.IGNORE_CASE)
            standardPattern.find(contentDisposition)?.let { match ->
                val raw = match.groupValues[1].trim('"', '\'')
                if (raw.isNotBlank()) return sanitizeFileName(raw)
            }
        }

        // 2. Try extracting from URL path segment (e.g. https://.../files/sample_video.mp4)
        try {
            val cleanUrl = url.substringBefore("?").substringBefore("#")
            val lastSegment = cleanUrl.substringAfterLast("/")
            if (lastSegment.isNotBlank() && lastSegment.contains(".")) {
                val cleanSegment = sanitizeFileName(lastSegment)
                if (cleanSegment.length > 2) {
                    return cleanSegment
                }
            }

            // Check query parameters like ?file=xyz.mp4 or ?filename=xyz.zip
            val query = url.substringAfter("?", "")
            if (query.isNotBlank()) {
                for (part in query.split("&")) {
                    val kv = part.split("=")
                    if (kv.size == 2 && kv[0].lowercase() in listOf("file", "filename", "name", "download", "attachment")) {
                        val decoded = try {
                            java.net.URLDecoder.decode(kv[1], "UTF-8")
                        } catch (_: Exception) {
                            kv[1]
                        }
                        if (decoded.contains(".")) {
                            return sanitizeFileName(decoded)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 3. Fallback to URLUtil guess if Content-Disposition was present
        if (!contentDisposition.isNullOrBlank()) {
            try {
                val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
                if (!guessed.isNullOrBlank() && !guessed.equals("downloadfile.bin", ignoreCase = true)) {
                    return sanitizeFileName(guessed)
                }
            } catch (_: Throwable) {}
        }

        // 4. Default timestamped file name preserving accurate extension
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = getExtensionForMime(mimeType) ?: getExtensionFromUrl(url) ?: "bin"
        return "Bandhan17_Download_$timeStamp.$ext"
    }

    /**
     * Resolves a clean filename for Blob and Data URL downloads.
     */
    fun resolveBlobFileName(suggestedName: String?, mimeType: String?): String {
        val ext = getExtensionForMime(mimeType) ?: "bin"
        if (!suggestedName.isNullOrBlank() && suggestedName != "download" && suggestedName != "unknown") {
            val sanitized = sanitizeFileName(suggestedName)
            return if (sanitized.contains(".")) {
                sanitized
            } else {
                "$sanitized.$ext"
            }
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "Bandhan17_Download_$timeStamp.$ext"
    }

    /**
     * Resolves the MIME type from URL, Content-Disposition header, and provided MIME string.
     */
    fun resolveMimeType(url: String, mimeType: String?, contentDisposition: String?): String {
        if (!mimeType.isNullOrBlank() && mimeType != "application/octet-stream" && mimeType != "*/*") {
            return mimeType
        }

        // Try getting extension from Content-Disposition
        if (!contentDisposition.isNullOrBlank()) {
            val ext = contentDisposition.substringAfterLast(".", "").substringBefore(";").substringBefore("\"").trim().lowercase()
            if (ext.isNotBlank() && EXTENSION_TO_MIME.containsKey(ext)) {
                return EXTENSION_TO_MIME[ext]!!
            }
        }

        // Try getting extension from URL path
        val urlExt = getExtensionFromUrl(url)
        if (!urlExt.isNullOrBlank() && EXTENSION_TO_MIME.containsKey(urlExt)) {
            return EXTENSION_TO_MIME[urlExt]!!
        }

        // MimeTypeMap lookup
        if (!urlExt.isNullOrBlank()) {
            try {
                val mapped = MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(urlExt.lowercase())
                if (!mapped.isNullOrBlank()) return mapped
            } catch (_: Throwable) {}
        }

        return "application/octet-stream"
    }

    fun getExtensionForMime(mimeType: String?): String? {
        if (mimeType.isNullOrBlank()) return null
        val lowerMime = mimeType.lowercase().trim()

        for ((ext, mime) in EXTENSION_TO_MIME) {
            if (mime.equals(lowerMime, ignoreCase = true)) {
                return ext
            }
        }

        return try {
            MimeTypeMap.getSingleton()?.getExtensionFromMimeType(lowerMime)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getExtensionFromUrl(url: String): String? {
        return try {
            val cleanUrl = url.substringBefore("?").substringBefore("#")
            val lastSegment = cleanUrl.substringAfterLast("/")
            val ext = lastSegment.substringAfterLast(".", "").trim().lowercase()
            if (ext.isNotBlank() && ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) ext else null
        } catch (_: Throwable) {
            null
        }
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    fun ensureExtension(fileName: String, defaultMime: String?): String {
        val sanitized = sanitizeFileName(fileName)
        if (sanitized.contains(".")) return sanitized
        val ext = getExtensionForMime(defaultMime) ?: "bin"
        return "$sanitized.$ext"
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Executes standard HTTP/HTTPS file download via Android DownloadManager with notifications enabled.
     */
    fun executeHttpDownload(
        context: Context,
        request: DownloadRequest,
        customFileName: String? = null,
        onQueued: ((Long) -> Unit)? = null
    ) {
        try {
            val finalFileName = ensureExtension(
                customFileName?.ifBlank { null } ?: request.suggestedFileName,
                request.mimeType
            )
            val guessedMime = resolveMimeType(request.url, request.mimeType, request.contentDisposition)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                Toast.makeText(context, "ডাউনলোড ম্যানেজার পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                return
            }

            val dmRequest = DownloadManager.Request(Uri.parse(request.url)).apply {
                setMimeType(guessedMime)

                val cookies = CookieManager.getInstance().getCookie(request.url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }

                if (!request.userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", request.userAgent)
                }
                addRequestHeader("Accept", "*/*")
                addRequestHeader("Referer", "https://bandhan17.website/")

                setTitle(finalFileName)
                setDescription("বন্ধন'১৭ ফাইল ডাউনলোড হচ্ছে...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
            }

            val downloadId = downloadManager.enqueue(dmRequest)
            onQueued?.invoke(downloadId)
            Toast.makeText(context, "ডাউনলোড শুরু হয়েছে: $finalFileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "ডাউনলোড ত্রুটি: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Executes Base64 / Blob download with live notification progress and saved output.
     */
    fun executeBase64Download(
        context: Context,
        request: DownloadRequest,
        customFileName: String? = null,
        onCompleted: (DownloadCompletedInfo) -> Unit
    ) {
        val finalFileName = ensureExtension(
            customFileName?.ifBlank { null } ?: request.suggestedFileName,
            request.mimeType
        )
        val resolvedMime = resolveMimeType(request.url, request.mimeType, request.contentDisposition)
        val notificationId = finalFileName.hashCode()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Post Initial Progress Notification
                showProgressNotification(context, notificationId, finalFileName, 10)

                val base64Data = request.base64Data.orEmpty()
                val cleanBase64 = if (base64Data.contains(",")) {
                    base64Data.substringAfter(",")
                } else {
                    base64Data
                }

                showProgressNotification(context, notificationId, finalFileName, 35)

                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                if (bytes == null || bytes.isEmpty()) {
                    cancelNotification(context, notificationId)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "ডাউনলোড করা ফাইল খালি", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                showProgressNotification(context, notificationId, finalFileName, 70)

                var savedFileUri: Uri? = null
                var savedFile: File? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                        put(MediaStore.Downloads.MIME_TYPE, resolvedMime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { os ->
                            os.write(bytes)
                            os.flush()
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                        savedFileUri = uri
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadsDir.mkdirs()
                    val targetFile = getUniqueFile(downloadsDir, finalFileName)
                    FileOutputStream(targetFile).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                    }
                    savedFile = targetFile
                    savedFileUri = Uri.fromFile(targetFile)

                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    downloadManager?.addCompletedDownload(
                        targetFile.name,
                        "বন্ধন'১৭ ফাইল",
                        true,
                        resolvedMime,
                        targetFile.absolutePath,
                        targetFile.length(),
                        true
                    )
                }

                val finalUri = savedFileUri ?: (savedFile?.let { Uri.fromFile(it) })
                if (finalUri != null) {
                    val completedInfo = DownloadCompletedInfo(
                        fileName = finalFileName,
                        mimeType = resolvedMime,
                        fileUri = finalUri,
                        file = savedFile,
                        fileSizeBytes = bytes.size.toLong()
                    )

                    withContext(Dispatchers.Main) {
                        showCompletedNotification(
                            context = context,
                            notificationId = notificationId,
                            fileName = finalFileName,
                            mimeType = resolvedMime,
                            fileUri = finalUri,
                            file = savedFile,
                            fileSizeBytes = bytes.size.toLong()
                        )
                        onCompleted(completedInfo)
                    }
                }
            } catch (e: Exception) {
                cancelNotification(context, notificationId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "ডাউনলোড সংরক্ষণ ব্যর্থ: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Displays a progress notification in the Android status bar / notification drawer.
     */
    fun showProgressNotification(
        context: Context,
        notificationId: Int,
        fileName: String,
        progressPercent: Int
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            ensureNotificationChannel(notificationManager)

            val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("ডাউনলোড হচ্ছে...")
                .setContentText(fileName)
                .setProgress(100, progressPercent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notificationManager.notify(notificationId, notification)
        } catch (_: Throwable) {}
    }

    /**
     * Displays an interactive download completed notification with Open and Share action buttons.
     */
    fun showCompletedNotification(
        context: Context,
        notificationId: Int,
        fileName: String,
        mimeType: String,
        fileUri: Uri?,
        file: File?,
        fileSizeBytes: Long = 0L
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            ensureNotificationChannel(notificationManager)

            // 1. Open Intent & PendingIntent (Tap notification to open)
            val openIntent = createOpenFileIntent(context, fileUri, mimeType, file)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                notificationId * 2 + 1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 2. Share Intent & PendingIntent (Share Action Button)
            val shareIntent = createShareFileIntent(context, fileUri, mimeType, file, fileName)
            val shareChooser = Intent.createChooser(shareIntent, "Share $fileName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val sharePendingIntent = PendingIntent.getActivity(
                context,
                notificationId * 2 + 2,
                shareChooser,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val sizeDesc = if (fileSizeBytes > 0) " (${formatFileSize(fileSizeBytes)})" else ""

            val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("ডাউনলোড সম্পন্ন হয়েছে")
                .setContentText("$fileName$sizeDesc")
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(android.R.drawable.ic_menu_view, "খুলুন (Open)", openPendingIntent)
                .addAction(android.R.drawable.ic_menu_share, "শেয়ার (Share)", sharePendingIntent)
                .build()

            notificationManager.notify(notificationId, notification)
        } catch (_: Throwable) {}
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notificationId)
        } catch (_: Throwable) {}
    }

    /**
     * Opens the downloaded file using Android's system viewer/player chooser.
     */
    fun openDownloadedFile(
        context: Context,
        fileUri: Uri?,
        mimeType: String,
        file: File? = null
    ): Boolean {
        return try {
            val intent = createOpenFileIntent(context, fileUri, mimeType, file)
            val chooser = Intent.createChooser(intent, "Open file with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "ফাইল খোলার জন্য কোনো অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Shares the downloaded file via Android's system share sheet.
     */
    fun shareDownloadedFile(
        context: Context,
        fileUri: Uri?,
        mimeType: String,
        file: File? = null,
        fileName: String = "file"
    ): Boolean {
        return try {
            val intent = createShareFileIntent(context, fileUri, mimeType, file, fileName)
            val chooser = Intent.createChooser(intent, "Share $fileName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "ফাইল শেয়ার করতে ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun createOpenFileIntent(
        context: Context,
        fileUri: Uri?,
        mimeType: String,
        file: File?
    ): Intent {
        val resolvedUri = when {
            file != null -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            fileUri != null -> fileUri
            else -> Uri.EMPTY
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(resolvedUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createShareFileIntent(
        context: Context,
        fileUri: Uri?,
        mimeType: String,
        file: File?,
        fileName: String
    ): Intent {
        val resolvedUri = when {
            file != null -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            fileUri != null -> fileUri
            else -> Uri.EMPTY
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, resolvedUri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, "Bandhan 17: $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun ensureNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                DOWNLOAD_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for downloads, progress, and file actions"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getUniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = fileName.substringBeforeLast(".")
        val ext = fileName.substringAfterLast(".", "")
        val extSuffix = if (ext.isNotBlank()) ".$ext" else ""

        var counter = 1
        while (file.exists()) {
            file = File(dir, "$nameWithoutExt ($counter)$extSuffix")
            counter++
        }
        return file
    }
}
