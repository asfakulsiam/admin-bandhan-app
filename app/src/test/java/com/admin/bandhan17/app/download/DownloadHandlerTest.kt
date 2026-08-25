package com.admin.bandhan17.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadHandlerTest {

    @Test
    fun testResolveFileNameFromContentDisposition() {
        val cd1 = "attachment; filename=\"report_2026.xlsx\""
        assertEquals("report_2026.xlsx", DownloadHandler.resolveFileName("https://example.com/export", cd1, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))

        val cdUtf8 = "attachment; filename*=UTF-8''Bandhan_Statement_January.pdf"
        assertEquals("Bandhan_Statement_January.pdf", DownloadHandler.resolveFileName("https://example.com/api", cdUtf8, "application/pdf"))

        val cdVideo = "attachment; filename=\"promo_clip.mp4\""
        assertEquals("promo_clip.mp4", DownloadHandler.resolveFileName("https://example.com/video/download", cdVideo, "video/mp4"))

        val cdArchive = "attachment; filename=\"backup_files.zip\""
        assertEquals("backup_files.zip", DownloadHandler.resolveFileName("https://example.com/backup", cdArchive, "application/zip"))
    }

    @Test
    fun testResolveFileNameFromUrlPath() {
        val urlMp4 = "https://bandhan17.website/media/videos/event_2026.mp4?auth=xyz"
        assertEquals("event_2026.mp4", DownloadHandler.resolveFileName(urlMp4, null, "video/mp4"))

        val urlMp3 = "https://bandhan17.website/audio/song_track.mp3"
        assertEquals("song_track.mp3", DownloadHandler.resolveFileName(urlMp3, null, "audio/mpeg"))

        val urlRar = "https://bandhan17.website/downloads/archive_data.rar"
        assertEquals("archive_data.rar", DownloadHandler.resolveFileName(urlRar, null, "application/x-rar-compressed"))
    }

    @Test
    fun testResolveMimeTypeForAllExtensions() {
        assertEquals("video/mp4", DownloadHandler.resolveMimeType("https://example.com/v.mp4", null, null))
        assertEquals("audio/mpeg", DownloadHandler.resolveMimeType("https://example.com/a.mp3", null, null))
        assertEquals("application/zip", DownloadHandler.resolveMimeType("https://example.com/f.zip", null, null))
        assertEquals("application/x-rar-compressed", DownloadHandler.resolveMimeType("https://example.com/f.rar", null, null))
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", DownloadHandler.resolveMimeType("https://example.com/f.xlsx", null, null))
        assertEquals("application/pdf", DownloadHandler.resolveMimeType("https://example.com/f.pdf", null, null))
    }

    @Test
    fun testFileCategoryClassification() {
        assertEquals(FileCategory.VIDEO, FileCategory.fromFileNameOrMime("movie.mp4", "video/mp4"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromFileNameOrMime("audio.mp3", "audio/mpeg"))
        assertEquals(FileCategory.SPREADSHEET, FileCategory.fromFileNameOrMime("accounts.xlsx", "application/vnd.ms-excel"))
        assertEquals(FileCategory.ARCHIVE, FileCategory.fromFileNameOrMime("backup.rar", null))
        assertEquals(FileCategory.PDF, FileCategory.fromFileNameOrMime("statement.pdf", "application/pdf"))
        assertEquals(FileCategory.DOCUMENT, FileCategory.fromFileNameOrMime("notes.docx", null))
    }

    @Test
    fun testEnsureExtension() {
        assertEquals("custom_name.mp4", DownloadHandler.ensureExtension("custom_name", "video/mp4"))
        assertEquals("already_named.xlsx", DownloadHandler.ensureExtension("already_named.xlsx", "application/vnd.ms-excel"))
        assertEquals("archive.zip", DownloadHandler.ensureExtension("archive", "application/zip"))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("500 B", DownloadHandler.formatFileSize(500L))
        assertEquals("15 KB", DownloadHandler.formatFileSize(15 * 1024L))
        assertEquals("4.5 MB", DownloadHandler.formatFileSize((4.5 * 1024 * 1024).toLong()))
        assertEquals("1.2 GB", DownloadHandler.formatFileSize((1.2 * 1024 * 1024 * 1024).toLong()))
    }
}
