package com.admin.bandhan17.app.download

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File
import java.util.UUID

/**
 * Encapsulates a pending download request from WebView (HTTP, Blob, or Data URI).
 */
data class DownloadRequest(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val suggestedFileName: String,
    val mimeType: String,
    val contentDisposition: String? = null,
    val userAgent: String? = null,
    val base64Data: String? = null,
    val isBlobOrBase64: Boolean = false,
    val contentLength: Long = 0L
)

/**
 * Encapsulates information about a successfully downloaded file.
 */
data class DownloadCompletedInfo(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val fileUri: Uri,
    val file: File? = null,
    val fileSizeBytes: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Categorizes files by their extension / MIME type to provide intuitive icons, colors, and descriptions.
 */
enum class FileCategory(
    val displayName: String,
    val icon: ImageVector,
    val accentColor: Color,
    val lightBgColor: Color
) {
    PDF(
        displayName = "PDF Document",
        icon = Icons.Default.PictureAsPdf,
        accentColor = Color(0xFFE53935),
        lightBgColor = Color(0xFFFFEBEE)
    ),
    DOCUMENT(
        displayName = "Office Document",
        icon = Icons.Default.Description,
        accentColor = Color(0xFF1E88E5),
        lightBgColor = Color(0xFFE3F2FD)
    ),
    SPREADSHEET(
        displayName = "Spreadsheet / Data",
        icon = Icons.Default.TableChart,
        accentColor = Color(0xFF2E7D32),
        lightBgColor = Color(0xFFE8F5E9)
    ),
    VIDEO(
        displayName = "Video File",
        icon = Icons.Default.VideoFile,
        accentColor = Color(0xFF8E24AA),
        lightBgColor = Color(0xFFF3E5F5)
    ),
    AUDIO(
        displayName = "Audio / Music",
        icon = Icons.Default.AudioFile,
        accentColor = Color(0xFFF57C00),
        lightBgColor = Color(0xFFFFF3E0)
    ),
    IMAGE(
        displayName = "Image File",
        icon = Icons.Default.Image,
        accentColor = Color(0xFF00897B),
        lightBgColor = Color(0xFFE0F2F1)
    ),
    ARCHIVE(
        displayName = "Compressed Archive",
        icon = Icons.Default.Archive,
        accentColor = Color(0xFF6D4C41),
        lightBgColor = Color(0xFFEFEBE9)
    ),
    CODE(
        displayName = "Code / Config File",
        icon = Icons.Default.Code,
        accentColor = Color(0xFF00ACC1),
        lightBgColor = Color(0xFFE0F7FA)
    ),
    OTHER(
        displayName = "General File",
        icon = Icons.Default.InsertDriveFile,
        accentColor = Color(0xFF546E7A),
        lightBgColor = Color(0xFFECEFF1)
    );

    companion object {
        fun fromFileNameOrMime(fileName: String, mimeType: String?): FileCategory {
            val ext = fileName.substringAfterLast(".", "").lowercase()
            val mime = mimeType?.lowercase().orEmpty()

            return when {
                ext == "pdf" || mime.contains("pdf") -> PDF
                ext in listOf("xlsx", "xls", "csv", "tsv", "ods") || mime.contains("spreadsheet") || mime.contains("excel") || mime.contains("csv") -> SPREADSHEET
                ext in listOf("doc", "docx", "txt", "rtf", "odt", "ppt", "pptx", "odp") || mime.contains("word") || mime.contains("presentation") || mime.contains("text/plain") -> DOCUMENT
                ext in listOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv", "m4v") || mime.startsWith("video/") -> VIDEO
                ext in listOf("mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "amr", "wma") || mime.startsWith("audio/") -> AUDIO
                ext in listOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico", "heic") || mime.startsWith("image/") -> IMAGE
                ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso") || mime.contains("zip") || mime.contains("compressed") || mime.contains("tar") -> ARCHIVE
                ext in listOf("json", "xml", "html", "htm", "css", "js", "ts", "apk", "sql", "py", "kt", "java") || mime.contains("json") || mime.contains("xml") -> CODE
                else -> OTHER
            }
        }
    }
}
