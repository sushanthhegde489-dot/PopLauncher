package com.sushanth.poplauncher

import com.sushanth.poplauncher.data.indexing.FileSearchProvider
import com.sushanth.poplauncher.data.indexing.MediaSearchProvider
import com.sushanth.poplauncher.data.model.FileEntity
import com.sushanth.poplauncher.data.model.MediaEntity
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.ProviderDiagnostic
import com.sushanth.poplauncher.data.repository.SearchResultState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAndMediaSearchProviderTest {

    @Test
    fun testFileSearchProviderMetadata() {
        val provider = FileSearchProvider()
        assertEquals(SearchType.FILE, provider.type)
        assertEquals("Files", provider.displayName)
    }

    @Test
    fun testMediaSearchProviderMetadata() {
        val provider = MediaSearchProvider()
        assertEquals(SearchType.MEDIA, provider.type)
        assertEquals("Photos & Videos", provider.displayName)
    }

    @Test
    fun testFileEntityCreation() {
        val fileEntity = FileEntity(
            id = "file:101",
            title = "Report.pdf",
            subtitle = "Documents • PDF Document • 2.4 MB",
            source = "Files",
            mimeType = "application/pdf",
            size = 2500000L,
            launchIntent = null,
            searchableTerms = listOf("Report.pdf", "PDF Document", "Documents")
        )

        assertEquals("Report.pdf", fileEntity.title)
        assertEquals("Files", fileEntity.source)
        assertEquals("application/pdf", fileEntity.mimeType)
        assertEquals(2500000L, fileEntity.size)
        assertTrue(fileEntity.searchableTerms.contains("PDF Document"))
    }

    @Test
    fun testMediaEntityCreationAndSorting() {
        val photo = MediaEntity(
            id = "media:image:1",
            title = "IMG_2026.jpg",
            subtitle = "Camera • Photo • 3.1 MB",
            source = "Photos",
            iconUri = "content://media/external/images/media/1",
            isVideo = false,
            dateAdded = 1000L,
            launchIntent = null
        )

        val video = MediaEntity(
            id = "media:video:2",
            title = "VID_2026.mp4",
            subtitle = "Camera • Video • 0:45 • 15.2 MB",
            source = "Videos",
            iconUri = "content://media/external/video/media/2",
            isVideo = true,
            dateAdded = 2000L,
            launchIntent = null
        )

        val sortedList = listOf(photo, video).sortedByDescending { it.dateAdded }

        assertEquals("VID_2026.mp4", sortedList[0].title)
        assertEquals("IMG_2026.jpg", sortedList[1].title)
        assertTrue(sortedList[0].isVideo)
    }

    @Test
    fun testFileSearchProviderFormatFileSize() {
        val provider = FileSearchProvider()
        assertEquals("", provider.formatFileSize(0))
        assertEquals("", provider.formatFileSize(-10))
        assertEquals("500 B", provider.formatFileSize(500))
        assertEquals("1.0 KB", provider.formatFileSize(1024))
        assertEquals("1.5 KB", provider.formatFileSize(1536))
        assertEquals("1.0 MB", provider.formatFileSize(1024 * 1024))
        assertEquals("2.5 MB", provider.formatFileSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", provider.formatFileSize(1024L * 1024 * 1024))
    }

    @Test
    fun testFileSearchProviderFormatMimeType() {
        val provider = FileSearchProvider()
        assertEquals("PDF Document", provider.formatMimeType("application/pdf", "test.pdf"))
        assertEquals("Word Document", provider.formatMimeType("application/msword", "doc.doc"))
        assertEquals("Word Document", provider.formatMimeType("application/octet-stream", "document.docx"))
        assertEquals("Spreadsheet", provider.formatMimeType("application/vnd.ms-excel", "sheet.xls"))
        assertEquals("Spreadsheet", provider.formatMimeType("application/vnd.openxmlformats", "data.xlsx"))
        assertEquals("Presentation", provider.formatMimeType("application/vnd.ms-powerpoint", "slides.ppt"))
        assertEquals("ZIP Archive", provider.formatMimeType("application/zip", "archive.zip"))
        assertEquals("ZIP Archive", provider.formatMimeType("application/x-rar-compressed", "archive.rar"))
        assertEquals("APK Package", provider.formatMimeType("application/vnd.android.package-archive", "app.apk"))
        assertEquals("Text File", provider.formatMimeType("text/plain", "readme.txt"))
        assertEquals("Audio File", provider.formatMimeType("audio/mpeg", "song.mp3"))
        assertEquals("File", provider.formatMimeType("application/unknown", "unknown"))
    }

    @Test
    fun testFileSearchProviderBuildFileSelection() {
        val provider = FileSearchProvider()

        val (genericSel, genericArgs) = provider.buildFileSelection("test note", "test note")
        assertTrue(genericSel != null && genericSel.contains("_size >= 0"))
        assertTrue(genericArgs!!.contains("%test%"))
        assertTrue(genericArgs.contains("%note%"))

        val (fileSel, fileArgs) = provider.buildFileSelection("file", "file")
        assertTrue(fileSel != null && fileSel.contains("_size >= 0"))
        assertNull(fileArgs)

        val (pdfSel, pdfArgs) = provider.buildFileSelection("pdf", "pdf")
        assertTrue(pdfSel != null && pdfSel.contains("_size >= 0"))
        assertTrue(pdfArgs!!.contains("%.pdf"))
        assertTrue(pdfArgs.contains("%pdf%"))

        val (sheetSel, sheetArgs) = provider.buildFileSelection("sheet", "sheet")
        assertTrue(sheetSel != null && sheetSel.contains("_size >= 0"))
        assertTrue(sheetArgs!!.contains("%spreadsheet%"))
        assertTrue(sheetArgs.contains("%excel%"))
    }

    @Test
    fun testMediaSearchProviderFormatFileSize() {
        val provider = MediaSearchProvider()
        assertEquals("", provider.formatFileSize(0))
        assertEquals("", provider.formatFileSize(-5))
        assertEquals("800 B", provider.formatFileSize(800))
        assertEquals("1.0 KB", provider.formatFileSize(1024))
        assertEquals("3.2 MB", provider.formatFileSize((3.2 * 1024 * 1024).toLong()))
    }

    @Test
    fun testMediaSearchProviderFormatDuration() {
        val provider = MediaSearchProvider()
        assertEquals("", provider.formatDuration(0))
        assertEquals("", provider.formatDuration(-10))
        assertEquals("0:05", provider.formatDuration(5000))
        assertEquals("0:45", provider.formatDuration(45000))
        assertEquals("1:05", provider.formatDuration(65000))
        assertEquals("10:00", provider.formatDuration(600000))
        assertEquals("1:01:05", provider.formatDuration(3665000))
    }

    @Test
    fun testFileAccessStateEnum() {
        val states = FileAccessState.entries
        assertTrue(states.contains(FileAccessState.ALL_FILES))
        assertTrue(states.contains(FileAccessState.MEDIASTORE_ONLY))
        assertTrue(states.contains(FileAccessState.DENIED))
    }

    @Test
    fun testMediaAccessStateEnum() {
        val states = MediaAccessState.entries
        assertTrue(states.contains(MediaAccessState.FULL))
        assertTrue(states.contains(MediaAccessState.SELECTED_ONLY))
        assertTrue(states.contains(MediaAccessState.DENIED))
    }

    @Test
    fun testSearchResultStateDiagnosticsAndErrors() {
        val state = SearchResultState(
            query = "test",
            providerDiagnostics = listOf(
                ProviderDiagnostic(
                    providerType = SearchType.FILE,
                    isAvailable = false,
                    errorMessage = "Storage permission required"
                ),
                ProviderDiagnostic(
                    providerType = SearchType.MEDIA,
                    isAvailable = true,
                    errorMessage = null
                )
            ),
            providerErrors = listOf("Storage permission required")
        )

        assertEquals("test", state.query)
        assertEquals(2, state.providerDiagnostics.size)
        assertEquals(1, state.providerErrors.size)
        assertEquals("Storage permission required", state.providerErrors[0])
        assertFalse(state.providerDiagnostics[0].isAvailable)
        assertTrue(state.providerDiagnostics[1].isAvailable)
    }
}
