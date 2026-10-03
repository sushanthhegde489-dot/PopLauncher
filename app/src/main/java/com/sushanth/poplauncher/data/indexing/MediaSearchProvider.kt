package com.sushanth.poplauncher.data.indexing

import android.Manifest
import android.util.Log
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.sushanth.poplauncher.MediaAccessState
import com.sushanth.poplauncher.data.model.MediaEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale

class MediaSearchProvider(
    private val settingsRepository: SettingsRepository? = null
) : SearchProvider {

    override val type: SearchType = SearchType.MEDIA
    override val displayName: String = "Photos & Videos"

    fun getAccessState(context: Context): MediaAccessState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return MediaAccessState.FULL
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasVisualSelected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            return when {
                hasImages || hasVideo -> MediaAccessState.FULL
                hasVisualSelected -> MediaAccessState.SELECTED_ONLY
                else -> MediaAccessState.DENIED
            }
        }
        val hasStorage = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return if (hasStorage) MediaAccessState.FULL else MediaAccessState.DENIED
    }

    override suspend fun isAvailable(context: Context): Boolean =
        getAccessState(context) != MediaAccessState.DENIED

    override suspend fun index(context: Context): List<SearchEntity> = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext emptyList()
        val results = mutableListOf<SearchEntity>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext() && results.size < 500) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Photo" else "Photo"
                    results.add(MediaEntity(id = "media_index:img:$id", title = name, source = "Photos", launchIntent = null))
                }
            }
        } catch (e: Exception) {
            Log.e("PopLauncher", "MediaSearchProvider.index photos failed: ${e.message}", e)
        }

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                while (cursor.moveToNext() && results.size < 1000) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Video" else "Video"
                    results.add(MediaEntity(id = "media_index:vid:$id", title = name, source = "Videos", isVideo = true, launchIntent = null))
                }
            }
        } catch (e: Exception) {
            Log.e("PopLauncher", "MediaSearchProvider.index videos failed: ${e.message}", e)
        }

        results
    }

    override suspend fun search(query: String, context: Context): List<SearchEntity> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty() || !isAvailable(context)) return@withContext emptyList()

            val isMediaEnabled = settingsRepository?.settings?.value?.enabledProviders?.contains(SearchType.MEDIA) ?: true
            if (!isMediaEnabled) return@withContext emptyList()

            val photoResults = Collections.synchronizedList(mutableListOf<MediaEntity>())
            val videoResults = Collections.synchronizedList(mutableListOf<MediaEntity>())

            coroutineScope {
                val photoJob = async { queryPhotos(context, trimmed, photoResults) }
                val videoJob = async { queryVideos(context, trimmed, videoResults) }
                photoJob.await()
                videoJob.await()
            }

            // Combine and sort results by dateAdded descending
            (photoResults + videoResults)
                .sortedByDescending { it.dateAdded }
                .take(20)
        }

    private fun queryPhotos(context: Context, query: String, photoResults: MutableList<MediaEntity>) {
        val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.RELATIVE_PATH
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATA
            )
        }

        val lowerQuery = query.lowercase()
        val isGenericPhotoKeyword = lowerQuery in setOf(
            "photo", "photos", "image", "images", "pic", "pics", "picture", "pictures",
            "camera", "dcim", "gallery", "screenshot", "screenshots", "media",
            "img", "jpg", "jpeg", "png", "webp", "gif", "dng", "raw",
            "wallpaper", "wallpapers", "snap", "snaps", "ss", "shot"
        )

        val tokens = query.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val (selection, selectionArgs) = if (isGenericPhotoKeyword || tokens.isEmpty()) {
            Pair(null, null)
        } else {
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.RELATIVE_PATH
            } else {
                MediaStore.Images.Media.DATA
            }

            val tokenClauses = mutableListOf<String>()
            val argsList = mutableListOf<String>()

            for (token in tokens) {
                val likeToken = "%$token%"
                tokenClauses.add("(${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR $pathColumn LIKE ?)")
                argsList.add(likeToken)
                argsList.add(likeToken)
                argsList.add(likeToken)
            }

            Pair(tokenClauses.joinToString(" AND "), argsList.toTypedArray())
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 15"

        try {
            context.contentResolver.query(
                imageUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val bucketIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateAddedIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext() && photoResults.size < 15) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Photo" else "Photo"
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "image/*" else "image/*"
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val bucket = if (bucketIdx >= 0) cursor.getString(bucketIdx) else null
                    val dateAddedSec = if (dateAddedIdx >= 0) cursor.getLong(dateAddedIdx) else 0L

                    val contentUri = ContentUris.withAppendedId(imageUri, id)
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    val albumLabel = bucket ?: "Photos"
                    val formattedSize = formatFileSize(size)
                    val subtitle = listOfNotNull(albumLabel, "Photo", formattedSize.takeIf { it.isNotBlank() })
                        .joinToString(" • ")

                    photoResults.add(
                        MediaEntity(
                            id = "media:image:$id",
                            title = name,
                            subtitle = subtitle,
                            source = "Photos",
                            iconUri = contentUri.toString(),
                            isVideo = false,
                            dateAdded = dateAddedSec * 1000L,
                            launchIntent = viewIntent,
                            searchableTerms = listOf(name, albumLabel)
                        )
                    )
                }
                Log.d("PopLauncher", "MediaSearchProvider found ${photoResults.size} photos for query: $query")
            } ?: Log.e("PopLauncher", "MediaSearchProvider cursor is null for query: $query")
        } catch (e: Exception) {
            Log.e("PopLauncher", "MediaSearchProvider exception: ${e.message}", e)
        }
    }

    private fun queryVideos(context: Context, query: String, videoResults: MutableList<MediaEntity>) {
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.RELATIVE_PATH
            )
        } else {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATA
            )
        }

        val lowerQuery = query.lowercase()
        val isGenericVideoKeyword = lowerQuery in setOf(
            "video", "videos", "movie", "movies", "clip", "clips", "media",
            "mp4", "mkv", "vid", "vids", "mov", "avi", "3gp", "webm",
            "recording", "recordings"
        )

        val tokens = query.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val (selection, selectionArgs) = if (isGenericVideoKeyword || tokens.isEmpty()) {
            Pair(null, null)
        } else {
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.RELATIVE_PATH
            } else {
                MediaStore.Video.Media.DATA
            }

            val tokenClauses = mutableListOf<String>()
            val argsList = mutableListOf<String>()

            for (token in tokens) {
                val likeToken = "%$token%"
                tokenClauses.add("(${MediaStore.Video.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} LIKE ? OR $pathColumn LIKE ?)")
                argsList.add(likeToken)
                argsList.add(likeToken)
                argsList.add(likeToken)
            }

            Pair(tokenClauses.joinToString(" AND "), argsList.toTypedArray())
        }

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC LIMIT 15"

        try {
            context.contentResolver.query(
                videoUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durationIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val bucketIdx = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateAddedIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext() && videoResults.size < 15) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Video" else "Video"
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "video/*" else "video/*"
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val durationMs = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0L
                    val bucket = if (bucketIdx >= 0) cursor.getString(bucketIdx) else null
                    val dateAddedSec = if (dateAddedIdx >= 0) cursor.getLong(dateAddedIdx) else 0L

                    val contentUri = ContentUris.withAppendedId(videoUri, id)
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    val albumLabel = bucket ?: "Videos"
                    val formattedDuration = formatDuration(durationMs)
                    val formattedSize = formatFileSize(size)
                    val subtitle = listOfNotNull(albumLabel, "Video", formattedDuration.takeIf { it.isNotBlank() }, formattedSize.takeIf { it.isNotBlank() })
                        .joinToString(" • ")

                    videoResults.add(
                        MediaEntity(
                            id = "media:video:$id",
                            title = name,
                            subtitle = subtitle,
                            source = "Videos",
                            iconUri = contentUri.toString(),
                            isVideo = true,
                            dateAdded = dateAddedSec * 1000L,
                            launchIntent = viewIntent,
                            searchableTerms = listOf(name, albumLabel)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("PopLauncher", "MediaSearchProvider video query exception: ${e.message}", e)
        }
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

    internal fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return ""
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = durationMs / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
