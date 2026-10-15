package com.sushanth.poplauncher.search

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserHandle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.sushanth.poplauncher.MainActivity
import com.sushanth.poplauncher.data.model.AnimationLevel
import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.ConversationEntity
import com.sushanth.poplauncher.data.model.DeepSearchEntity
import com.sushanth.poplauncher.data.model.FileEntity
import com.sushanth.poplauncher.data.model.MediaEntity
import com.sushanth.poplauncher.data.model.PersonEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.WebOverviewEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SearchResultCategory(val title: String, val icon: ImageVector) {
    ALL("All", Icons.Outlined.Widgets),
    APPS("Apps", Icons.Outlined.Apps),
    CONTACTS("Contacts", Icons.Outlined.Person),
    CONVERSATIONS("Chats", Icons.Outlined.Forum),
    FILES("Files", Icons.Outlined.Description),
    MEDIA("Media", Icons.Outlined.Image),
    SHORTCUTS("Shortcuts", Icons.Outlined.Bolt),
    AI_OVERVIEW("AI Overview", Icons.Outlined.AutoAwesome)
}

@Composable
fun SearchResultsList(
    results: List<SearchEntity>,
    webOverview: WebOverviewEntity?,
    hasActiveBubble: Boolean,
    query: String,
    onEntityClick: (SearchEntity) -> Unit,
    getAppIcon: (String, UserHandle?) -> Drawable?,
    modifier: Modifier = Modifier,
    selectedCategory: SearchResultCategory = SearchResultCategory.ALL,
    providerErrors: List<String> = emptyList(),
    animationLevel: AnimationLevel = AnimationLevel.STANDARD
) {
    val context = LocalContext.current

    // Synthesize on-demand overview when user selects AI Overview or when no device results exist
    val effectiveOverview = webOverview ?: if ((results.isEmpty() || selectedCategory == SearchResultCategory.AI_OVERVIEW) && query.isNotBlank()) {
        val searchUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
        val browserIntent = Intent(Intent.ACTION_VIEW, searchUrl.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        WebOverviewEntity(
            query = query,
            title = "AI Overview for \"$query\"",
            subtitle = "Tap to search Google or get AI overview",
            browserIntent = browserIntent,
            deepSearchOptions = emptyList(),
            summaryText = null,
            isLoading = false,
            isLiveWeb = false
        )
    } else null

    if (results.isEmpty() && effectiveOverview == null && query.isNotBlank() && !hasActiveBubble) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (providerErrors.isNotEmpty()) "Permission required" else "No direct match",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val description = if (providerErrors.isNotEmpty()) {
                    providerErrors.joinToString("\n")
                } else {
                    "Try searching with a different keyword or application name."
                }
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                if (providerErrors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            (context as? MainActivity)?.requestSearchPermissions()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant access", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        return
    }

    val apps = remember(results) { results.filterIsInstance<AppEntity>() }
    val contacts = remember(results) { results.filterIsInstance<PersonEntity>() }
    val conversations = remember(results) { results.filterIsInstance<ConversationEntity>() }
    val files = remember(results) { results.filterIsInstance<FileEntity>() }
    val media = remember(results) { results.filterIsInstance<MediaEntity>() }
    val shortcuts = remember(results) { results.filterIsInstance<DeepSearchEntity>() }

    val packageOnlyIconLookup: (String) -> Drawable? = { pkg -> getAppIcon(pkg, null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        // Missing Permission Prompt Banner
        if (providerErrors.isNotEmpty()) {
            item(key = "permission_prompt_banner") {
                PermissionPromptCard(
                    errors = providerErrors,
                    onRequestPermissions = {
                        (context as? MainActivity)?.requestSearchPermissions()
                    }
                )
            }
        }

        // Top AI Overview (shown when no local results, or when AI Overview category is selected)
        if (effectiveOverview != null && (results.isEmpty() || selectedCategory == SearchResultCategory.AI_OVERVIEW)) {
            item(key = "ai_overview_main") {
                AnimatedSearchItem(animationLevel = animationLevel, index = 0, modifier = Modifier.animateItem()) {
                    SearchResultItem(
                        entity = effectiveOverview,
                        appIcon = null,
                        getAppIcon = packageOnlyIconLookup,
                        onClick = { onEntityClick(effectiveOverview) },
                        onSubActionClick = { onEntityClick(it) }
                    )
                }
            }
        }

        // Category Sections
        if (selectedCategory == SearchResultCategory.ALL || selectedCategory == SearchResultCategory.APPS) {
            if (apps.isNotEmpty()) {
                if (selectedCategory == SearchResultCategory.ALL) {
                    item(key = "header_apps") {
                        SectionHeader(title = "Applications", count = apps.size, icon = Icons.Outlined.Apps)
                    }
                }
                itemsIndexed(items = apps, key = { _, it -> it.id }) { idx, entity ->
                    AnimatedSearchItem(animationLevel = animationLevel, index = idx, modifier = Modifier.animateItem()) {
                        val icon = getAppIcon(entity.packageName, entity.userHandle)
                        SearchResultItem(
                            entity = entity,
                            appIcon = icon,
                            getAppIcon = packageOnlyIconLookup,
                            onClick = { onEntityClick(entity) },
                            onSubActionClick = { onEntityClick(it) }
                        )
                    }
                }
            }
        }

        if (selectedCategory == SearchResultCategory.ALL || selectedCategory == SearchResultCategory.CONTACTS) {
            if (contacts.isNotEmpty()) {
                if (selectedCategory == SearchResultCategory.ALL) {
                    item(key = "header_contacts") {
                        SectionHeader(title = "Contacts", count = contacts.size, icon = Icons.Outlined.Person)
                    }
                }
                itemsIndexed(items = contacts, key = { _, it -> it.id }) { idx, entity ->
                    AnimatedSearchItem(animationLevel = animationLevel, index = idx, modifier = Modifier.animateItem()) {
                        SearchResultItem(
                            entity = entity,
                            appIcon = null,
                            getAppIcon = packageOnlyIconLookup,
                            onClick = { onEntityClick(entity) },
                            onSubActionClick = { onEntityClick(it) }
                        )
                    }
                }
            }
        }

        if (selectedCategory == SearchResultCategory.ALL || selectedCategory == SearchResultCategory.CONVERSATIONS) {
            if (conversations.isNotEmpty()) {
                if (selectedCategory == SearchResultCategory.ALL) {
                    item(key = "header_conversations") {
                        SectionHeader(title = "Conversations", count = conversations.size, icon = Icons.Outlined.Forum)
                    }
                }
                itemsIndexed(items = conversations, key = { _, it -> it.id }) { idx, entity ->
                    AnimatedSearchItem(animationLevel = animationLevel, index = idx, modifier = Modifier.animateItem()) {
                        val icon = entity.appPackage?.let { getAppIcon(it, null) }
                        SearchResultItem(
                            entity = entity,
                            appIcon = icon,
                            getAppIcon = packageOnlyIconLookup,
                            onClick = { onEntityClick(entity) },
                            onSubActionClick = { onEntityClick(it) }
                        )
                    }
                }
            }
        }

        if (selectedCategory == SearchResultCategory.ALL || selectedCategory == SearchResultCategory.FILES) {
            if (files.isNotEmpty()) {
                if (selectedCategory == SearchResultCategory.ALL) {
                    item(key = "header_files") {
                        SectionHeader(title = "Files & Documents", count = files.size, icon = Icons.Outlined.Description)
                    }
                }
                itemsIndexed(items = files, key = { _, it -> it.id }) { idx, entity ->
                    AnimatedSearchItem(animationLevel = animationLevel, index = idx, modifier = Modifier.animateItem()) {
                        SearchResultItem(
                            entity = entity,
                            appIcon = null,
                            getAppIcon = packageOnlyIconLookup,
                            onClick = { onEntityClick(entity) },
                            onSubActionClick = { onEntityClick(it) }
                        )
                    }
                }
            }
        }

        if (selectedCategory == SearchResultCategory.ALL || selectedCategory == SearchResultCategory.MEDIA) {
            if (media.isNotEmpty()) {
                if (selectedCategory == SearchResultCategory.ALL) {
                    item(key = "header_media") {
                        SectionHeader(title = "Photos & Media", count = media.size, icon = Icons.Outlined.Image)
                    }
                }
                itemsIndexed(items = media, key = { _, it -> it.id }) { idx, entity ->
                    AnimatedSearchItem(animationLevel = animationLevel, index = idx, modifier = Modifier.animateItem()) {
                        SearchResultItem(
                            entity = entity,
                            appIcon = null,
                            getAppIcon = packageOnlyIconLookup,
                            onClick = { onEntityClick(entity) },
                            onSubActionClick = { onEntityClick(it) }
                        )
                    }
                }
            }
        }

        if (selectedCategory == SearchResultCategory.ALL || selectedCategory == SearchResultCategory.SHORTCUTS) {
            if (shortcuts.isNotEmpty()) {
                if (selectedCategory == SearchResultCategory.ALL) {
                    item(key = "header_shortcuts") {
                        SectionHeader(title = "Shortcuts & Actions", count = shortcuts.size, icon = Icons.Outlined.Bolt)
                    }
                }
                itemsIndexed(items = shortcuts, key = { _, it -> it.id }) { idx, entity ->
                    AnimatedSearchItem(animationLevel = animationLevel, index = idx, modifier = Modifier.animateItem()) {
                        SearchResultItem(
                            entity = entity,
                            appIcon = null,
                            getAppIcon = packageOnlyIconLookup,
                            onClick = { onEntityClick(entity) },
                            onSubActionClick = { onEntityClick(it) }
                        )
                    }
                }
            }
        }

        // Bottom AI Overview when local results exist and ALL category is viewed
        if (effectiveOverview != null && results.isNotEmpty() && selectedCategory == SearchResultCategory.ALL) {
            item(key = "header_web_overview_bottom") {
                SectionHeader(title = "AI Overview", count = null, icon = Icons.Outlined.AutoAwesome)
            }
            item(key = "ai_overview_bottom") {
                AnimatedSearchItem(animationLevel = animationLevel, index = 4, modifier = Modifier.animateItem()) {
                    SearchResultItem(
                        entity = effectiveOverview,
                        appIcon = null,
                        getAppIcon = packageOnlyIconLookup,
                        onClick = { onEntityClick(effectiveOverview) },
                        onSubActionClick = { onEntityClick(it) }
                    )
                }
            }
        }
    }
}

/**
 * Category filter chips bar, rendered just above the search field.
 * Allows users to choose AI Overview even when device results match.
 */
@Composable
fun SearchCategoriesBar(
    results: List<SearchEntity>,
    webOverview: WebOverviewEntity?,
    query: String,
    selectedCategory: SearchResultCategory,
    onCategorySelect: (SearchResultCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty() && webOverview == null && query.isBlank()) return

    val apps = remember(results) { results.filterIsInstance<AppEntity>() }
    val contacts = remember(results) { results.filterIsInstance<PersonEntity>() }
    val conversations = remember(results) { results.filterIsInstance<ConversationEntity>() }
    val files = remember(results) { results.filterIsInstance<FileEntity>() }
    val media = remember(results) { results.filterIsInstance<MediaEntity>() }
    val shortcuts = remember(results) { results.filterIsInstance<DeepSearchEntity>() }

    val totalCount = results.size + if (webOverview != null) 1 else 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryChip(
            category = SearchResultCategory.ALL,
            count = totalCount,
            isSelected = selectedCategory == SearchResultCategory.ALL,
            onClick = { onCategorySelect(SearchResultCategory.ALL) }
        )
        if (apps.isNotEmpty()) {
            CategoryChip(
                category = SearchResultCategory.APPS,
                count = apps.size,
                isSelected = selectedCategory == SearchResultCategory.APPS,
                onClick = { onCategorySelect(SearchResultCategory.APPS) }
            )
        }
        if (contacts.isNotEmpty()) {
            CategoryChip(
                category = SearchResultCategory.CONTACTS,
                count = contacts.size,
                isSelected = selectedCategory == SearchResultCategory.CONTACTS,
                onClick = { onCategorySelect(SearchResultCategory.CONTACTS) }
            )
        }
        if (conversations.isNotEmpty()) {
            CategoryChip(
                category = SearchResultCategory.CONVERSATIONS,
                count = conversations.size,
                isSelected = selectedCategory == SearchResultCategory.CONVERSATIONS,
                onClick = { onCategorySelect(SearchResultCategory.CONVERSATIONS) }
            )
        }
        if (files.isNotEmpty()) {
            CategoryChip(
                category = SearchResultCategory.FILES,
                count = files.size,
                isSelected = selectedCategory == SearchResultCategory.FILES,
                onClick = { onCategorySelect(SearchResultCategory.FILES) }
            )
        }
        if (media.isNotEmpty()) {
            CategoryChip(
                category = SearchResultCategory.MEDIA,
                count = media.size,
                isSelected = selectedCategory == SearchResultCategory.MEDIA,
                onClick = { onCategorySelect(SearchResultCategory.MEDIA) }
            )
        }
        if (shortcuts.isNotEmpty()) {
            CategoryChip(
                category = SearchResultCategory.SHORTCUTS,
                count = shortcuts.size,
                isSelected = selectedCategory == SearchResultCategory.SHORTCUTS,
                onClick = { onCategorySelect(SearchResultCategory.SHORTCUTS) }
            )
        }

        // AI Overview option: Always available when query is not blank, even if local device matches exist!
        if (query.isNotBlank()) {
            CategoryChip(
                category = SearchResultCategory.AI_OVERVIEW,
                count = null,
                isSelected = selectedCategory == SearchResultCategory.AI_OVERVIEW,
                onClick = { onCategorySelect(SearchResultCategory.AI_OVERVIEW) }
            )
        }
    }
}

@Composable
fun CategoryChip(
    category: SearchResultCategory,
    count: Int?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        val text = if (count != null) "${category.title} ($count)" else category.title
        Text(
            text = text,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int?,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = count.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AnimatedSearchItem(
    animationLevel: AnimationLevel,
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: @Composable () -> Unit
) {
    if (animationLevel == AnimationLevel.OFF) {
        Box(modifier = modifier) { content() }
        return
    }

    val alphaAnim = remember { Animatable(0f) }
    val translateYAnim = remember { Animatable(28f) }
    val scaleAnim = remember { Animatable(0.95f) }

    LaunchedEffect(Unit) {
        val delayMs = ((index.coerceAtMost(6)) * 25 * animationLevel.scale).toLong()
        if (delayMs > 0L) {
            delay(delayMs)
        }
        val animDuration = (260 * animationLevel.scale).toInt().coerceAtLeast(80)
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(animDuration, easing = FastOutSlowInEasing)
            )
        }
        launch {
            translateYAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = alphaAnim.value
                translationY = translateYAnim.value
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
    ) {
        content()
    }
}

@Composable
private fun PermissionPromptCard(
    errors: List<String>,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Search Access Required",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = errors.joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Grant Search Access", fontWeight = FontWeight.SemiBold)
        }
    }
}
