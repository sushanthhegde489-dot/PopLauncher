package com.sushanth.poplauncher.data.indexing

import android.Manifest
import android.util.Log
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sushanth.poplauncher.data.model.FileEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FileSearchProvider(
    private val settingsRepository: SettingsRepository? = null
) : SearchProvider {

    override val type: SearchType = SearchType.FILE
    override val displayName: String = "Files & Documents"

    override suspend fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImg = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasVisual = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            return hasImg || hasVid || hasVisual
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun index(context: Context): List<SearchEntity> = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext emptyList()
        val results = mutableListOf<SearchEntity>()
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext() && results.size < 500) {
                    val fileId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "File" else "File"
                    results.add(
                        FileEntity(
                            id = "file_index:$fileId",
                            title = name,
                            source = "Files",
                            mimeType = "*/*",
                            size = 0L,
                            launchIntent = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("PopLauncher", "FileSearchProvider.index failed: ${e.message}", e)
        }
        results
    }

    override suspend fun search(query: String, context: Context): List<SearchEntity> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty() || !isAvailable(context)) return@withContext emptyList()

            val isFileEnabled = settingsRepository?.settings?.value?.enabledProviders?.contains(SearchType.FILE) ?: true
            if (!isFileEnabled) return@withContext emptyList()

            val results = mutableListOf<SearchEntity>()
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.TITLE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.RELATIVE_PATH
                )
            } else {
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.TITLE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.DATA
                )
            }

            val lowerQuery = trimmed.lowercase()
            val (selection, selectionArgs) = buildFileSelection(trimmed, lowerQuery)
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 25"

            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val titleIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.TITLE)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    val mimeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                    val relativePathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext() && results.size < 25) {
                        val fileId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                        val name = (if (nameIdx >= 0) cursor.getString(nameIdx) else null)
                            ?: (if (titleIdx >= 0) cursor.getString(titleIdx) else null)
                            ?: "File"
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "*/*" else "*/*"
                        val relativePath = if (relativePathIdx >= 0) cursor.getString(relativePathIdx) else null

                        val fileUri = ContentUris.withAppendedId(uri, fileId)
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        val formattedSize = formatFileSize(size)
                        val readableType = formatMimeType(mime, name)
                        val folderName = relativePath?.trim('/')?.split('/')?.lastOrNull()?.takeIf { it.isNotBlank() }

                        val subtitleParts = mutableListOf<String>()
                        if (!folderName.isNullOrBlank()) subtitleParts.add(folderName)
                        subtitleParts.add(readableType)
                        if (formattedSize.isNotBlank()) subtitleParts.add(formattedSize)

                        val subtitle = subtitleParts.joinToString(" • ")

                        results.add(
                            FileEntity(
                                id = "file:$fileId",
                                title = name,
                                subtitle = subtitle,
                                source = "Files",
                                mimeType = mime,
                                size = size,
                                launchIntent = viewIntent,
                                searchableTerms = listOf(name, readableType, folderName ?: "")
                            )
                        )
                    }
                    Log.d("PopLauncher", "FileSearchProvider found ${results.size} files for query: $trimmed")
                } ?: Log.e("PopLauncher", "FileSearchProvider cursor is null for query: $trimmed")
            } catch (e: Exception) {
                Log.e("PopLauncher", "FileSearchProvider exception: ${e.message}", e)
            }

            results
        }

    private fun searchDiskStorage(
        query: String,
        context: Context
    ): List<FileEntity> {
        val diskResults = mutableListOf<FileEntity>()
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        val targetDirs = listOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC,
            "Desktop"
        ).map { File(root, it) }.filter { it.exists() && it.isDirectory }

        val tokens = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        for (dir in targetDirs) {
            scanDirectory(dir, tokens, context, diskResults, maxDepth = 3)
            if (diskResults.size >= 15) break
        }
        return diskResults
    }

    private fun scanDirectory(
        dir: File,
        tokens: List<String>,
        context: Context,
        results: MutableList<FileEntity>,
        maxDepth: Int
    ) {
        if (maxDepth <= 0 || !dir.canRead() || results.size >= 15) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (results.size >= 15) break
            val name = file.name
            if (name.startsWith(".")) continue

            if (file.isDirectory) {
                scanDirectory(file, tokens, context, results, maxDepth - 1)
            } else {
                val lowerName = name.lowercase()
                val matchesAllTokens = tokens.all { token -> lowerName.contains(token) }
                if (matchesAllTokens) {
                    val fileUri = try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } catch (_: Exception) {
                        Uri.fromFile(file)
                    }
                    val mime = context.contentResolver.getType(fileUri) ?: "*/*"
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val formattedSize = formatFileSize(file.length())
                    val readableType = formatMimeType(mime, name)
                    val parentName = file.parentFile?.name

                    val subtitleParts = mutableListOf<String>()
                    if (!parentName.isNullOrBlank()) subtitleParts.add(parentName)
                    subtitleParts.add(readableType)
                    if (formattedSize.isNotBlank()) subtitleParts.add(formattedSize)

                    results.add(
                        FileEntity(
                            id = "file_disk:${file.absolutePath.hashCode()}",
                            title = name,
                            subtitle = subtitleParts.joinToString(" • "),
                            source = "Files",
                            mimeType = mime,
                            size = file.length(),
                            launchIntent = viewIntent,
                            searchableTerms = listOf(name, readableType, parentName ?: "")
                        )
                    )
                }
            }
        }
    }

    internal fun buildFileSelection(trimmed: String, lowerQuery: String): Pair<String?, Array<String>?> {
        val isGenericFileKeyword = lowerQuery in setOf(
            "file", "files", "document", "documents", "doc", "docs",
            "download", "downloads", "paper", "media"
        )

        val sizeFilter = "(${MediaStore.Files.FileColumns.SIZE} >= 0 OR ${MediaStore.Files.FileColumns.SIZE} IS NULL)"
        val notHiddenFilter = "(${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE '.%' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} IS NULL)"

        if (isGenericFileKeyword) {
            val selection = "$sizeFilter AND $notHiddenFilter"
            return Pair(selection, null)
        }

        val tokens = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return Pair("$sizeFilter AND $notHiddenFilter", null)
        }

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.FileColumns.RELATIVE_PATH
        } else {
            MediaStore.Files.FileColumns.DATA
        }

        val tokenClauses = mutableListOf<String>()
        val argsList = mutableListOf<String>()

        for (token in tokens) {
            val likeToken = "%$token%"
            val clauseParts = mutableListOf(
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                "${MediaStore.Files.FileColumns.TITLE} LIKE ?",
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?",
                "$pathColumn LIKE ?"
            )
            argsList.add(likeToken)
            argsList.add(likeToken)
            argsList.add(likeToken)
            argsList.add(likeToken)

            when (token.lowercase()) {
                "pdf" -> {
                    clauseParts.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                    argsList.add("%pdf%")
                    clauseParts.add("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?")
                    argsList.add("%.pdf")
                }
                "sheet", "excel", "spreadsheet" -> {
                    clauseParts.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                    argsList.add("%spreadsheet%")
                    clauseParts.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                    argsList.add("%excel%")
                }
                "presentation", "powerpoint", "slides", "ppt" -> {
                    clauseParts.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                    argsList.add("%presentation%")
                    clauseParts.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                    argsList.add("%powerpoint%")
                }
                "zip", "rar", "archive", "compressed" -> {
                    clauseParts.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                    argsList.add("%zip%")
                    clauseParts.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                    argsList.add("%compressed%")
                }
            }

            tokenClauses.add("(${clauseParts.joinToString(" OR ")})")
        }

        val selection = "${tokenClauses.joinToString(" AND ")} AND $sizeFilter AND $notHiddenFilter"
        return Pair(selection, argsList.toTypedArray())
    }

    internal fun formatFileSize(size: Long): String {
        if (size <= 0) return ""
        return when {
            size >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
            size >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
            size >= 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            else -> "$size B"
        }
    }

    internal fun formatMimeType(mime: String, filename: String): String {
        val lowerMime = mime.lowercase()
        val lowerName = filename.lowercase()
        return when {
            lowerMime.contains("pdf") || lowerName.endsWith(".pdf") -> "PDF Document"
            lowerMime.contains("word") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx") -> "Word Document"
            lowerMime.contains("excel") || lowerMime.contains("spreadsheet") || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") -> "Spreadsheet"
            lowerMime.contains("powerpoint") || lowerMime.contains("presentation") || lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx") -> "Presentation"
            lowerMime.contains("zip") || lowerMime.contains("compressed") || lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") -> "ZIP Archive"
            lowerMime.contains("android.package-archive") || lowerName.endsWith(".apk") -> "APK Package"
            lowerMime.contains("text") || lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".json") -> "Text File"
            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".m4a") -> "Audio File"
            else -> mime.substringAfter("/").uppercase().takeIf { it.length in 2..5 } ?: "File"
        }
    }
}
