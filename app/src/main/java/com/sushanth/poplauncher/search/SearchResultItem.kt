package com.sushanth.poplauncher.search

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import com.sushanth.poplauncher.util.drawableToBitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.CallEntity
import com.sushanth.poplauncher.data.model.ConversationEntity
import com.sushanth.poplauncher.data.model.DeepSearchEntity
import com.sushanth.poplauncher.data.model.FileEntity
import com.sushanth.poplauncher.data.model.MediaEntity
import com.sushanth.poplauncher.data.model.MessageEntity
import com.sushanth.poplauncher.data.model.PersonEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.WebOverviewEntity
import com.sushanth.poplauncher.ui.theme.LocalIsMonochromeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchResultItem(
    entity: SearchEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    appIcon: Drawable? = null,
    getAppIcon: ((String) -> Drawable?)? = null,
    onSubActionClick: ((SearchEntity) -> Unit)? = null
) {
    when (entity) {
        is WebOverviewEntity -> {
            WebOverviewCard(
                entity = entity,
                getAppIcon = getAppIcon,
                onClick = onClick,
                onSubActionClick = onSubActionClick ?: {},
                modifier = modifier
            )
        }
        is PersonEntity -> {
            PersonResultCard(
                person = entity,
                getAppIcon = getAppIcon,
                onPrimaryClick = onClick,
                onSubActionClick = onSubActionClick ?: {},
                modifier = modifier
            )
        }
        else -> {
            StandardResultRow(
                entity = entity,
                appIcon = appIcon,
                onClick = onClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun WebOverviewCard(
    entity: WebOverviewEntity,
    getAppIcon: ((String) -> Drawable?)?,
    onClick: () -> Unit,
    onSubActionClick: (SearchEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        // Web Overview Main Clickable Header Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = "AI Overview",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "AI Overview",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Open in browser",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = entity.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (entity.isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Generating AI overview...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            } else if (!entity.summaryText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = entity.summaryText,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to open full search results in browser",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to view full web search results in your browser.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Deep App Search Options (Drive, Photos, YouTube, Spotify, Maps, etc.)
        if (entity.deepSearchOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Search in installed apps",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.4.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val isMonochrome = LocalIsMonochromeTheme.current
                entity.deepSearchOptions.forEach { option ->
                    val optionIconDrawable = remember(option.appPackage) {
                        getAppIcon?.invoke(option.appPackage)
                    }
                    val optionBitmap = remember(optionIconDrawable, isMonochrome) {
                        optionIconDrawable?.let { drawableToBitmap(it, isMonochrome) }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { onSubActionClick(option) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (optionBitmap != null && !optionBitmap.isRecycled) {
                                Image(
                                    bitmap = optionBitmap.asImageBitmap(),
                                    contentDescription = option.source,
                                    colorFilter = if (isMonochrome) ColorFilter.colorMatrix(
                                        ColorMatrix().apply { setToSaturation(0f) }) else null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = option.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonResultCard(
    person: PersonEntity,
    getAppIcon: ((String) -> Drawable?)?,
    onPrimaryClick: () -> Unit,
    onSubActionClick: (SearchEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        // Main Contact Header (Tap to Call / View)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPrimaryClick)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (!person.subtitle.isNullOrBlank()) {
                    Text(
                        text = person.subtitle!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            val context = LocalContext.current
            val primaryPhone = person.phoneNumbers.firstOrNull() ?: person.subtitle
            IconButton(
                onClick = {
                    if (!primaryPhone.isNullOrBlank()) {
                        (context as? com.sushanth.poplauncher.MainActivity)?.openDialer(primaryPhone)
                    } else {
                        onPrimaryClick()
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Call,
                    contentDescription = "Call",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Related aggregated conversations (WhatsApp, Telegram, Signal, Messages, Shortcuts)
        if (person.relatedConversations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val isMonochrome = LocalIsMonochromeTheme.current
                person.relatedConversations.forEach { conv ->
                    val convAppDrawable = remember(conv.appPackage) {
                        conv.appPackage?.let { getAppIcon?.invoke(it) }
                    }
                    val convAppBitmap = remember(convAppDrawable, isMonochrome) {
                        convAppDrawable?.let { drawableToBitmap(it, isMonochrome) }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .clickable { onSubActionClick(conv) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        if (convAppBitmap != null && !convAppBitmap.isRecycled) {
                            Image(
                                bitmap = convAppBitmap.asImageBitmap(),
                                contentDescription = conv.source,
                                colorFilter = if (isMonochrome) ColorFilter.colorMatrix(
                                    ColorMatrix().apply { setToSaturation(0f) }
                                ) else null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Message,
                                contentDescription = null,
                                tint = if (conv.source.contains("WhatsApp", ignoreCase = true)) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = conv.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardResultRow(
    entity: SearchEntity,
    appIcon: Drawable?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMonochrome = LocalIsMonochromeTheme.current
    val bitmap = remember(appIcon, isMonochrome) {
        appIcon?.let { drawableToBitmap(it, isMonochrome) }
    }

    val isMediaOrImageFile = entity is MediaEntity ||
            ((entity as? FileEntity)?.mimeType?.startsWith("image/") == true) ||
            ((entity as? FileEntity)?.mimeType?.startsWith("video/") == true) ||
            ((entity as? FileEntity)?.title?.lowercase()?.let { title ->
                listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic", ".heif", ".mp4", ".mkv", ".mov", ".avi", ".3gp").any { title.endsWith(it) }
            } == true)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (isMediaOrImageFile) {
            MediaItemThumbnail(entity = entity)
        } else if ((entity is AppEntity || entity is ConversationEntity) && bitmap != null && !bitmap.isRecycled) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = entity.title,
                    colorFilter = if (isMonochrome) ColorFilter.colorMatrix(
                        ColorMatrix().apply { setToSaturation(0f) }) else null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                val vector: ImageVector = when (entity) {
                    is AppEntity -> Icons.Outlined.Widgets
                    is com.sushanth.poplauncher.data.model.AppGroupEntity -> Icons.Outlined.Folder
                    is ConversationEntity -> Icons.AutoMirrored.Outlined.Message
                    is MessageEntity -> Icons.AutoMirrored.Outlined.Message
                    is CallEntity -> Icons.Outlined.Call
                    is FileEntity -> Icons.Outlined.Description
                    is MediaEntity -> Icons.Outlined.Image
                    is DeepSearchEntity -> Icons.Outlined.Search
                    else -> Icons.Outlined.Widgets
                }
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!entity.subtitle.isNullOrBlank()) {
                Text(
                    text = entity.subtitle!!,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (entity is ConversationEntity || entity is MessageEntity || entity is CallEntity) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                val vector = when (entity) {
                    is CallEntity -> Icons.Outlined.Call
                    is MessageEntity -> Icons.AutoMirrored.Outlined.Message
                    else -> Icons.AutoMirrored.Outlined.ArrowForward
                }
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaItemThumbnail(
    entity: SearchEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isMonochrome = LocalIsMonochromeTheme.current
    var thumbnailBitmap by remember(entity.id) { mutableStateOf<Bitmap?>(null) }

    val isVideo = (entity as? MediaEntity)?.isVideo == true ||
            (entity as? FileEntity)?.mimeType?.startsWith("video/") == true ||
            (entity as? FileEntity)?.title?.lowercase()?.let { title ->
                listOf(".mp4", ".mkv", ".mov", ".avi", ".3gp", ".webm").any { title.endsWith(it) }
            } == true

    val isImage = entity is MediaEntity ||
            (entity as? FileEntity)?.mimeType?.startsWith("image/") == true ||
            (entity as? FileEntity)?.title?.lowercase()?.let { title ->
                listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic", ".heif", ".bmp").any { title.endsWith(it) }
            } == true

    LaunchedEffect(entity.id) {
        withContext(Dispatchers.IO) {
            try {
                val bitmap: Bitmap? = when (entity) {
                    is MediaEntity -> {
                        val uriStr = entity.iconUri
                        if (!uriStr.isNullOrBlank()) {
                            val uri = Uri.parse(uriStr)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    context.contentResolver.loadThumbnail(uri, Size(160, 160), null)
                                } catch (_: Exception) { null }
                            } else {
                                val id = ContentUris.parseId(uri)
                                if (entity.isVideo) {
                                    @Suppress("DEPRECATION")
                                    MediaStore.Video.Thumbnails.getThumbnail(
                                        context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaStore.Images.Thumbnails.getThumbnail(
                                        context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                                    )
                                }
                            }
                        } else null
                    }
                    is FileEntity -> {
                        val fileUri = entity.launchIntent?.data
                        if (fileUri != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    context.contentResolver.loadThumbnail(fileUri, Size(160, 160), null)
                                } catch (_: Exception) { null }
                            } else if (fileUri.scheme == "file" && fileUri.path != null) {
                                val filePath = fileUri.path!!
                                if (isVideo) {
                                    @Suppress("DEPRECATION")
                                    ThumbnailUtils.createVideoThumbnail(
                                        filePath, MediaStore.Images.Thumbnails.MINI_KIND
                                    )
                                } else if (isImage) {
                                    BitmapFactory.decodeFile(filePath)
                                } else null
                            } else null
                        } else null
                    }
                    else -> null
                }
                thumbnailBitmap = bitmap
            } catch (_: Exception) {
                thumbnailBitmap = null
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        if (thumbnailBitmap != null && !thumbnailBitmap!!.isRecycled) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = entity.title,
                contentScale = ContentScale.Crop,
                colorFilter = if (isMonochrome) ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(0f) }
                ) else null,
                modifier = Modifier.fillMaxSize()
            )
            if (isVideo) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        } else {
            // Coil AsyncImage Fallback for Content Uris
            val mediaUri = (entity as? MediaEntity)?.iconUri ?: (entity as? FileEntity)?.launchIntent?.data?.toString()
            if (!mediaUri.isNullOrBlank()) {
                val placeholderPainter = rememberVectorPainter(if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.Image)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(mediaUri)
                        .crossfade(true)
                        .size(160, 160)
                        .build(),
                    contentDescription = entity.title,
                    contentScale = ContentScale.Crop,
                    placeholder = placeholderPainter,
                    error = placeholderPainter,
                    fallback = placeholderPainter,
                    modifier = Modifier.fillMaxSize()
                )
                if (isVideo) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            } else {
                val vector = if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.Image
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
