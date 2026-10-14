package com.sushanth.poplauncher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushanth.poplauncher.ui.theme.LocalIsMonochromeTheme
import com.sushanth.poplauncher.util.drawableToBitmap
import com.sushanth.poplauncher.widgets.custom.CatalogStatus
import com.sushanth.poplauncher.widgets.custom.CustomWidgetCatalogRepository
import com.sushanth.poplauncher.widgets.custom.CustomWidgetRepository
import com.sushanth.poplauncher.widgets.custom.PublishedCustomWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

private enum class PickerMode { ANDROID, CUSTOM }

enum class WidgetCategory(val label: String) {
    ALL("All"), PRODUCTIVITY("Productivity"), COMMUNICATION("Communication"), MEDIA("Media"), WEATHER("Weather"), TOOLS("Tools"), OTHER("Other")
}

data class VisualWidgetInfo(
    val providerInfo: AppWidgetProviderInfo,
    val appLabel: String,
    val widgetLabel: String,
    val appIconBitmap: Bitmap?,
    val previewBitmap: Bitmap?,
    val estimatedSpan: String,
    val category: WidgetCategory
)

@Composable
fun VisualWidgetPickerScreen(
    customWidgetRepository: CustomWidgetRepository,
    customWidgetCatalogRepository: CustomWidgetCatalogRepository,
    onWidgetSelected: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isMonochrome = LocalIsMonochromeTheme.current
    var mode by remember { mutableStateOf(PickerMode.ANDROID) }
    var widgetList by remember { mutableStateOf<List<VisualWidgetInfo>>(emptyList()) }
    var filterQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(WidgetCategory.ALL) }
    val catalog by customWidgetCatalogRepository.catalog.collectAsState()
    val catalogStatus by customWidgetCatalogRepository.status.collectAsState()
    val ignoredIds by customWidgetCatalogRepository.ignoredIds.collectAsState()
    val installed by customWidgetRepository.customWidgets.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(isMonochrome) {
        widgetList = withContext(Dispatchers.IO) { loadAllVisualWidgets(context, isMonochrome = isMonochrome) }
    }

    LaunchedEffect(mode) {
        if (mode == PickerMode.CUSTOM) customWidgetCatalogRepository.refresh()
    }

    val filteredAndroidWidgets = remember(widgetList, filterQuery, selectedCategory) {
        val query = filterQuery.trim().lowercase()
        widgetList.filter { item ->
            val categoryMatch = (selectedCategory == WidgetCategory.ALL) || (item.category == selectedCategory)
            val queryMatch = query.isBlank() || item.appLabel.lowercase().contains(query) || item.widgetLabel.lowercase().contains(query) || item.providerInfo.provider.packageName.lowercase().contains(query)
            categoryMatch && queryMatch
        }
    }
    val visibleCustomWidgets = remember(catalog, ignoredIds, filterQuery) {
        val q = filterQuery.trim().lowercase()
        catalog.asSequence().filterNot { it.id in ignoredIds }.filter {
            q.isBlank() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }.toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Add Widget", style = MaterialTheme.typography.headlineSmall)
                        Text("Choose what you want on the widget page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (mode == PickerMode.CUSTOM) {
                        IconButton(onClick = { scope.launch { customWidgetCatalogRepository.refresh() } }) { Icon(Icons.Outlined.Refresh, "Refresh custom widgets") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PickerModeButton("Android widgets", mode == PickerMode.ANDROID, Modifier.weight(1f)) { mode = PickerMode.ANDROID }
                    PickerModeButton("Custom Widgets", mode == PickerMode.CUSTOM, Modifier.weight(1f)) { mode = PickerMode.CUSTOM }
                }
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text(if (mode == PickerMode.ANDROID) "Search installed widgets" else "Search custom widgets") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (filterQuery.isNotEmpty()) IconButton(onClick = { filterQuery = "" }) { Icon(Icons.Outlined.Clear, "Clear") } },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (mode == PickerMode.ANDROID) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetCategory.entries.forEach { category ->
                            PickerModeButton(category.label, selectedCategory == category) { selectedCategory = category }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (mode == PickerMode.ANDROID) {
            if (filteredAndroidWidgets.isEmpty()) {
                EmptyPickerState(
                    modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
                    title = "No matching Android widgets",
                    body = "Try another category or search term."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(168.dp),
                    modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredAndroidWidgets, key = { "${it.providerInfo.provider}:${it.widgetLabel}" }) { item ->
                        WidgetPreviewCard(item) { onWidgetSelected(item.providerInfo) }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
                if (catalogStatus == CatalogStatus.READY_CACHE) {
                    Text(
                        "Using the saved catalog. Refresh when a connection is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (visibleCustomWidgets.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        EmptyPickerState(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                            title = if (ignoredIds.isNotEmpty()) "No custom widgets to show" else "No custom widgets available",
                            body = when (catalogStatus) {
                                CatalogStatus.LOADING -> "Checking the published catalog…"
                                CatalogStatus.ERROR -> "The catalog is unavailable. Installed widgets still work normally."
                                else -> "Published widgets will appear here. Nothing is added until you choose Add."
                            }
                        )
                        if (ignoredIds.isNotEmpty()) {
                            OutlinedButton(onClick = { ignoredIds.forEach(customWidgetCatalogRepository::restore) }) { Text("Restore ignored widgets") }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(280.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleCustomWidgets, key = { it.id }) { published ->
                            CustomWidgetCatalogRow(
                                published = published,
                                installedVersion = installed.firstOrNull { it.id == published.id }?.sourceVersion,
                                onAdd = { customWidgetRepository.installPublishedWidget(published) },
                                onIgnore = { customWidgetCatalogRepository.ignore(published.id) }
                            )
                        }
                        if (ignoredIds.isNotEmpty()) {
                            item(key = "restore_ignored") {
                                OutlinedButton(onClick = { ignoredIds.forEach(customWidgetCatalogRepository::restore) }) { Text("Restore ignored widgets") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishedWidgetIcon(published: PublishedCustomWidget) {
    var bitmap by remember(published.thumbnailUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(published.thumbnailUrl) {
        bitmap = published.thumbnailUrl?.let { url -> withContext(Dispatchers.IO) { loadThumbnail(url) } }
    }
    Box(
        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(loadedBitmap, contentDescription = published.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            Text(published.icon ?: "□", fontSize = 22.sp)
        }
    }
    Spacer(Modifier.size(12.dp))
}

private fun loadThumbnail(url: String): ImageBitmap? {
    return runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank()) return null
        val connection = uri.toURL().openConnection() as? HttpURLConnection ?: return null
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 256 * 1024) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

@Composable
private fun PickerModeButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) { Text(text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun CustomWidgetCatalogRow(
    published: PublishedCustomWidget,
    installedVersion: Int?,
    onAdd: () -> Unit,
    onIgnore: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PublishedWidgetIcon(published)
            Column(modifier = Modifier.weight(1f)) {
                Text(published.name, style = MaterialTheme.typography.titleMedium)
                Text("Version ${published.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (published.deprecated) Text("Deprecated", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        Text(published.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        published.deprecationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val updateAvailable = installedVersion != null && published.version > installedVersion
            Button(
                onClick = onAdd,
                modifier = Modifier.weight(1f),
                enabled = installedVersion == null || updateAvailable,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Icon(if (updateAvailable) Icons.Outlined.Refresh else if (installedVersion == null) Icons.Outlined.Add else Icons.Outlined.Widgets, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (updateAvailable) "Update" else if (installedVersion == null) "Add" else "Added")
            }
            TextButton(onClick = onIgnore) {
                Icon(Icons.Outlined.RemoveCircleOutline, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Ignore")
            }
        }
    }
}

@Composable
private fun EmptyPickerState(modifier: Modifier = Modifier, title: String, body: String) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Widgets, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WidgetPreviewCard(widget: VisualWidgetInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "Add ${widget.widgetLabel} from ${widget.appLabel} to home screen",
                onClick = onClick
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            if (widget.previewBitmap != null && !widget.previewBitmap.isRecycled) {
                Image(widget.previewBitmap.asImageBitmap(), contentDescription = widget.widgetLabel, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            } else Icon(Icons.Outlined.Widgets, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        }
        Text(widget.widgetLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (widget.appIconBitmap != null && !widget.appIconBitmap.isRecycled) Image(widget.appIconBitmap.asImageBitmap(), widget.appLabel, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(widget.appLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(widget.estimatedSpan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun loadAllVisualWidgets(context: Context, isMonochrome: Boolean = false): List<VisualWidgetInfo> {
    val manager = AppWidgetManager.getInstance(context)
    val pm = context.packageManager
    val userManager = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
    val profiles = userManager?.userProfiles ?: listOf(android.os.Process.myUserHandle())
    val providers = profiles.flatMap { profile -> runCatching { manager.getInstalledProvidersForProfile(profile) }.getOrDefault(emptyList()) }
        .distinctBy { "${it.provider.packageName}/${it.provider.className}" }

    return providers.mapNotNull { info ->
        runCatching {
            val appInfo = pm.getApplicationInfo(info.provider.packageName, 0)
            val appLabel = pm.getApplicationLabel(appInfo).toString()
            val widgetLabel = info.loadLabel(pm)
            val appIcon = runCatching { drawableToBitmap(pm.getApplicationIcon(appInfo), isMonochrome) }.getOrNull()
            val preview = runCatching { info.loadPreviewImage(context, 0)?.let { drawableToBitmap(it, isMonochrome) } ?: drawableToBitmap(info.loadIcon(context, 0), isMonochrome) }.getOrNull()
            val spanX = ((info.minWidth + 30) / 70).coerceIn(1, 4)
            val spanY = ((info.minHeight + 30) / 70).coerceIn(1, 4)
            VisualWidgetInfo(info, appLabel, widgetLabel, appIcon, preview, "${spanX}×$spanY", categorizeWidget(info, appLabel, widgetLabel))
        }.getOrNull()
    }.sortedWith(compareBy({ it.category.ordinal }, { it.appLabel.lowercase() }, { it.widgetLabel.lowercase() }))
}

private fun categorizeWidget(info: AppWidgetProviderInfo, appLabel: String, widgetLabel: String): WidgetCategory {
    val text = "${info.provider.packageName} ${info.provider.className} $appLabel $widgetLabel".lowercase()
    return when {
        text.containsAny("weather", "forecast", "climate", "radar", "temperature") -> WidgetCategory.WEATHER
        text.containsAny("calendar", "schedule", "task", "todo", "note", "keep", "memo", "reminder", "clock", "alarm", "timer", "stopwatch", "doc", "sheet", "slide", "office", "workspace") -> WidgetCategory.PRODUCTIVITY
        text.containsAny("contact", "people", "person", "phone", "dialer", "message", "sms", "mms", "chat", "mail", "gmail", "whatsapp", "telegram", "signal", "slack", "messenger", "call") -> WidgetCategory.COMMUNICATION
        text.containsAny("music", "spotify", "podcast", "sound", "audio", "song", "radio", "media", "player", "photo", "picture", "gallery", "image", "camera", "youtube", "video", "netflix", "vlc") -> WidgetCategory.MEDIA
        text.containsAny("battery", "tool", "setting", "toggle", "drive", "cloud", "file", "manager", "system", "wifi", "bluetooth", "torch", "flashlight", "calculator", "device", "search", "google") -> WidgetCategory.TOOLS
        else -> WidgetCategory.OTHER
    }
}

private fun String.containsAny(vararg keywords: String): Boolean = keywords.any(::contains)
