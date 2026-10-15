package com.sushanth.poplauncher.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sushanth.poplauncher.MainActivity
import com.sushanth.poplauncher.MediaAccessState
import com.sushanth.poplauncher.R
import com.sushanth.poplauncher.data.model.AiLaunchMode
import com.sushanth.poplauncher.data.model.AiOverviewHistoryItem
import com.sushanth.poplauncher.data.model.AnimationLevel
import com.sushanth.poplauncher.data.model.AppGroup
import com.sushanth.poplauncher.data.model.BubbleBehavior
import com.sushanth.poplauncher.data.model.BubbleSpeed
import com.sushanth.poplauncher.data.model.GestureActionType
import com.sushanth.poplauncher.data.model.GestureConfig
import com.sushanth.poplauncher.data.model.GestureType
import com.sushanth.poplauncher.data.model.PopDifficulty
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.AppRepository
import com.sushanth.poplauncher.data.repository.InstalledApp
import com.sushanth.poplauncher.data.repository.SettingsRepository
import com.sushanth.poplauncher.ui.theme.LocalIsMonochromeTheme
import com.sushanth.poplauncher.ui.theme.ThemePalette
import com.sushanth.poplauncher.ui.theme.colorToHex
import com.sushanth.poplauncher.ui.theme.parseHexColor
import com.sushanth.poplauncher.util.drawableToBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    appRepository: AppRepository? = null,
    onBackClick: () -> Unit,
    onOpenFrictionApps: () -> Unit,
    onOpenConversationApps: () -> Unit = {},
    onOpenHiddenApps: () -> Unit = {},
    onOpenAppGroups: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsState()
    val apps by (appRepository?.installedApps?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val lifecycleOwner = LocalLifecycleOwner.current

    var isDefaultLauncher by remember {
        mutableStateOf(isDefaultLauncher(context))
    }
    var permissionRefreshTick by remember { mutableIntStateOf(0) }

    var showCustomColor by remember { mutableStateOf(value = false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showContactDisclosure by remember { mutableStateOf(false) }
    var showAiDisclosure by remember { mutableStateOf(false) }
    var showGeminiKeyDialog by remember { mutableStateOf(false) }
    var showAiHistoryDialog by remember { mutableStateOf(false) }
    var showAiAssistantPicker by remember { mutableStateOf(false) }
    var choosingAppForAiAssistant by remember { mutableStateOf(false) }
    var configuringGesture by remember { mutableStateOf<GestureType?>(null) }
    var choosingAppForGesture by remember { mutableStateOf<GestureType?>(null) }
    var choosingGroupForGesture by remember { mutableStateOf<GestureType?>(null) }

    val policyUrl = stringResource(
        R.string.privacy_policy_url
    )

    val hostedPolicyConfigured =
        policyUrl.startsWith("https://") &&
                !policyUrl.contains("YOUR_GITHUB_USERNAME", true) &&
                !policyUrl.contains("YOUR_REPO", true)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultLauncher = isDefaultLauncher(context)
                permissionRefreshTick++
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showCustomColor) {
        ColorPickerDialog(
            initialColor = runCatching {
                parseHexColor(settings.customColorHex)
            }.getOrDefault(
                settings.themePalette.previewDot
            ),
            onDismiss = {
                showCustomColor = false
            },
            onApply = { color ->
                val hex = colorToHex(color)

                settingsRepository.setCustomColor(hex)
                settingsRepository.setThemePalette(
                    ThemePalette.createCustom(color)
                )

                showCustomColor = false
            }
        )
    }

    if (showPrivacy) {
        PrivacyDialog(
            onDismiss = {
                showPrivacy = false
            }
        )
    }

    if (showContactDisclosure) {
        ContactDisclosureDialog(
            onDismiss = {
                showContactDisclosure = false
            },
            onContinue = {
                showContactDisclosure = false
                (context as? MainActivity)?.requestPermissionForProvider(SearchType.PERSON)
            }
        )
    }

    if (showAiDisclosure) {
        AlertDialog(
            onDismissRequest = { showAiDisclosure = false },
            title = { Text("Enable Web & AI Overviews?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "When enabled and local search results are empty, search queries are sent over secure HTTPS to generate web summaries via DuckDuckGo or your configured Gemini API key. No personal data, contacts, or device IDs are ever transmitted.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAiDisclosure = false
                        settingsRepository.toggleProvider(SearchType.GOOGLE_OVERVIEW, true)
                    }
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiDisclosure = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showGeminiKeyDialog) {
        var keyInput by remember { mutableStateOf(settings.geminiApiKey) }
        AlertDialog(
            onDismissRequest = { showGeminiKeyDialog = false },
            title = { Text("Gemini API Key", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Optionally provide your own Google Gemini API key to generate direct AI overviews. If left blank, privacy-preserving DuckDuckGo Instant Answers are used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsRepository.setGeminiApiKey(keyInput)
                        showGeminiKeyDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeminiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    configuringGesture?.let { gestureType ->
        val currentConfig = settings.getGesture(gestureType)
        GestureActionPickerDialog(
            gestureType = gestureType,
            currentConfig = currentConfig,
            onDismiss = { configuringGesture = null },
            onSelectAction = { action ->
                settingsRepository.setGestureConfig(
                    gestureType,
                    GestureConfig(actionType = action)
                )
                configuringGesture = null
            },
            onChooseApp = {
                configuringGesture = null
                choosingAppForGesture = gestureType
            },
            onChooseGroup = {
                configuringGesture = null
                choosingGroupForGesture = gestureType
            }
        )
    }

    choosingAppForGesture?.let { gestureType ->
        AppPickerDialog(
            apps = apps,
            onDismiss = { choosingAppForGesture = null },
            onAppSelected = { app ->
                settingsRepository.setGestureConfig(
                    gestureType,
                    GestureConfig(
                        actionType = GestureActionType.OPEN_APP,
                        targetPackage = app.uniqueId,
                        targetLabel = app.label
                    )
                )
                choosingAppForGesture = null
            }
        )
    }

    choosingGroupForGesture?.let { gestureType ->
        AppGroupPickerDialog(
            groups = settings.appGroups,
            onDismiss = { choosingGroupForGesture = null },
            onGroupSelected = { group ->
                settingsRepository.setGestureConfig(
                    gestureType,
                    GestureConfig(
                        actionType = GestureActionType.OPEN_APP_GROUP,
                        targetPackage = group.id,
                        targetLabel = group.name
                    )
                )
                choosingGroupForGesture = null
            },
            onCreateNewGroup = {
                choosingGroupForGesture = null
                onOpenAppGroups()
            }
        )
    }

    if (showAiHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showAiHistoryDialog = false },
            title = {
                Text(
                    text = "AI Overview History",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                if (settings.aiOverviewHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No search history recorded yet.\nRun searches with AI Overview enabled to see summaries here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Last ${settings.aiOverviewHistory.size} overview searches:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        settings.aiOverviewHistory.forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.query,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = dateFormat.format(Date(item.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Text(
                                        text = item.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAiHistoryDialog = false }
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                if (settings.aiOverviewHistory.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            settingsRepository.clearAiOverviewHistory()
                        }
                    ) {
                        Text("Clear History", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    if (showAiAssistantPicker) {
        val aiOptions = listOf(
            Triple("", "System Default Voice Assistant", "Standard voice interaction / assistant"),
            Triple("com.google.android.apps.bard", "Google Gemini", "Gemini AI assistant & voice overlay"),
            Triple("com.openai.chatgpt", "ChatGPT", "OpenAI ChatGPT Voice & Chat"),
            Triple("com.anthropic.claude", "Claude", "Anthropic Claude"),
            Triple("ai.perplexity.app.android", "Perplexity", "Perplexity AI search & voice"),
            Triple("com.microsoft.copilot", "Microsoft Copilot", "Microsoft Copilot AI assistant")
        )
        AlertDialog(
            onDismissRequest = { showAiAssistantPicker = false },
            title = {
                Text(
                    text = "Long-Press Search Assistant",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Choose which AI assistant opens when you long press the search icon on the home screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Target AI Application",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    aiOptions.forEach { (pkg, name, desc) ->
                        val isSelected = settings.defaultAiAppPackage == pkg
                        val isInstalled = pkg.isEmpty() || apps.any { it.matchesIdentifier(pkg) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    settingsRepository.setDefaultAiAppPackage(pkg)
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isInstalled) desc else "$desc (Not installed)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Text("✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    val isCustom = settings.defaultAiAppPackage.isNotEmpty() && aiOptions.none { it.first == settings.defaultAiAppPackage }
                    val customAppLabel = if (isCustom) {
                        apps.firstOrNull { it.matchesIdentifier(settings.defaultAiAppPackage) }?.label ?: settings.defaultAiAppPackage
                    } else null

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                choosingAppForAiAssistant = true
                            },
                        color = if (isCustom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = customAppLabel ?: "Choose another installed app...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCustom) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Select any installed app to launch on long-press",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCustom) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isCustom) {
                                Text("✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "Preferred Launch Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AiLaunchMode.entries.forEach { mode ->
                        val isSelected = settings.defaultAiMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    settingsRepository.setDefaultAiMode(mode)
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when (mode) {
                                            AiLaunchMode.VOICE_DICTATION -> "Direct voice prompt & dictation input box"
                                            AiLaunchMode.TEXT_CHAT -> "Open app main text chat window"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Text("✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiAssistantPicker = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (choosingAppForAiAssistant) {
        AppPickerDialog(
            apps = apps,
            onDismiss = { choosingAppForAiAssistant = false },
            onAppSelected = { app ->
                settingsRepository.setDefaultAiAppPackage(app.packageName)
                choosingAppForAiAssistant = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsTopBar(
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            /*
             * --------------------------------------------------------------
             * Appearance
             * --------------------------------------------------------------
             */
            SettingsSection(title = "Appearance") {

                if (!settings.themePalette.isMonochrome) {

                    PreferenceItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.ColorLens,
                                contentDescription = null
                            )
                        },
                        title = "Accent color",
                        subtitle = "Customize the color used for actions and selections.",
                        trailing = {
                            AccentPreview(
                                color = settings.themePalette.previewDot,
                                selected = false
                            )
                        },
                        onClick = {
                            showCustomColor = true
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    PaletteChoices(
                        selectedId = settings.themePalette.id,
                        onSelect = {
                            settingsRepository.setThemePalette(it)
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                SwitchPreferenceItem(
                    title = "Monochrome mode",
                    subtitle = "Use a clean monochrome style for app icons and interface.",
                    checked = settings.themePalette.isMonochrome,
                    onCheckedChange = { checked ->
                        settingsRepository.setMonochromeMode(checked)
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                val animationLevels = AnimationLevel.entries
                val selectedAnimIndex = animationLevels.indexOf(settings.animationLevel).let { if (it == -1) 2 else it }
                SegmentedPreference(
                    title = "Animation level",
                    options = animationLevels.map { it.label },
                    selectedIndex = selectedAnimIndex,
                    onSelected = { index ->
                        settingsRepository.setAnimationLevel(animationLevels[index])
                    }
                )
            }

            /*
             * --------------------------------------------------------------
             * Wallpaper & Background
             * --------------------------------------------------------------
             */
            SettingsSection(title = "Wallpaper & Background") {

                SwitchPreferenceItem(
                    title = "Wallpaper background",
                    subtitle = "Show device wallpaper behind the launcher instead of a solid background.",
                    checked = settings.showWallpaper,
                    onCheckedChange = { checked ->
                        settingsRepository.setShowWallpaper(checked)
                    }
                )

                if (settings.showWallpaper) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    val dimOptions = listOf("None", "20%", "40%", "60%")
                    val dimValues = listOf(0.0f, 0.2f, 0.4f, 0.6f)
                    val selectedDimIndex = dimValues.indexOfFirst { abs(it - settings.wallpaperDim) < 0.05f }.let {
                        if (it == -1) 0 else it
                    }

                    SegmentedPreference(
                        title = "Wallpaper dimming",
                        options = dimOptions,
                        selectedIndex = selectedDimIndex,
                        onSelected = { index ->
                            settingsRepository.setWallpaperDim(dimValues[index])
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    PreferenceItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null
                            )
                        },
                        title = "Change wallpaper",
                        subtitle = "Open system wallpaper picker.",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                            runCatching {
                                context.startActivity(Intent.createChooser(intent, "Select Wallpaper"))
                            }
                        }
                    )
                }
            }

            /*
             * --------------------------------------------------------------
             * Home Screen
             * --------------------------------------------------------------
             */
            SettingsSection(title = "Home Screen") {

                SwitchPreferenceItem(
                    title = "Show clock",
                    subtitle = "Display time header on the home screen.",
                    checked = settings.showClock,
                    onCheckedChange = { checked ->
                        settingsRepository.setShowClock(checked)
                    }
                )

                if (settings.showClock) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SwitchPreferenceItem(
                        title = "24-Hour time format",
                        subtitle = "Display time in 24-hour format (e.g. 14:30).",
                        checked = settings.clock24Hour,
                        onCheckedChange = { checked ->
                            settingsRepository.setClock24Hour(checked)
                        }
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SwitchPreferenceItem(
                    title = "Show date",
                    subtitle = "Display current date on the home screen.",
                    checked = settings.showDate,
                    onCheckedChange = { checked ->
                        settingsRepository.setShowDate(checked)
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SwitchPreferenceItem(
                    title = "Hide status bar on home screen",
                    subtitle = "Hide the system status bar while on the home screen for an immersive view.",
                    checked = settings.hideStatusBarOnHomeScreen,
                    onCheckedChange = { checked ->
                        settingsRepository.setHideStatusBarOnHomeScreen(checked)
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SwitchPreferenceItem(
                    title = "Floating search animation",
                    subtitle = "Subtle floating bobbing effect on the search bar.",
                    checked = settings.searchAnimationEnabled,
                    onCheckedChange = { checked ->
                        settingsRepository.setSearchAnimationEnabled(checked)
                    }
                )
            }

            /*
             * --------------------------------------------------------------
             * Gestures
             * --------------------------------------------------------------
             */
            SettingsSection(title = "Gestures") {
                Text(
                    text = "Perform gestures on the home screen to quickly open apps or actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )

                GestureType.entries.forEachIndexed { index, gestureType ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    val config = settings.getGesture(gestureType)
                    val subtitle = when (config.actionType) {
                        GestureActionType.NONE -> "None"
                        GestureActionType.OPEN_APP -> {
                            val label = config.targetLabel?.takeIf { it.isNotBlank() }
                                ?: apps.firstOrNull { it.matchesIdentifier(config.targetPackage ?: "") }?.label
                                ?: "App"
                            "Open $label"
                        }
                        GestureActionType.OPEN_APP_GROUP -> {
                            val group = settings.appGroups.firstOrNull { it.id == config.targetPackage }
                            val label = group?.name ?: config.targetLabel ?: "Select group"
                            "Open Group: $label"
                        }
                        GestureActionType.OPEN_SEARCH -> "Open search"
                        GestureActionType.OPEN_SETTINGS -> "Open settings"
                        GestureActionType.OPEN_NOTIFICATION_SHADE,
                        @Suppress("DEPRECATION")
                        GestureActionType.OPEN_NOTIFICATIONS -> "Open notification shade"
                        GestureActionType.OPEN_PRIVATE_SPACE -> "Open Private Space"
                        GestureActionType.OPEN_HIDDEN_APPS -> "Open Hidden Apps"
                        @Suppress("DEPRECATION")
                        GestureActionType.LOCK_SCREEN -> "None"
                    }

                    PreferenceItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.TouchApp,
                                contentDescription = null
                            )
                        },
                        title = gestureType.displayName,
                        subtitle = subtitle,
                        onClick = {
                            configuringGesture = gestureType
                        }
                    )
                }
            }

            /*
             * --------------------------------------------------------------
             * Launcher
             * --------------------------------------------------------------
             */
            SettingsSection(title = "Launcher") {

                if (!isDefaultLauncher) {
                    PreferenceItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Smartphone,
                                contentDescription = null
                            )
                        },
                        title = "Default launcher",
                        subtitle = "Choose Pop Launcher as your Home app.",
                        onClick = {
                            requestSetDefaultLauncher(context)
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                PreferenceItem(
                    title = "Friction apps",
                    subtitle = "Choose apps that require an intentional press before opening.",
                    onClick = onOpenFrictionApps
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                PreferenceItem(
                    title = "Hidden apps",
                    subtitle = if (settings.hiddenAppPackages.isEmpty()) "Select apps to hide from ordinary search." else "${settings.hiddenAppPackages.size} apps hidden • Revealed on \"hidden apps\" search.",
                    onClick = onOpenHiddenApps
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                PreferenceItem(
                    title = "Custom app groups",
                    subtitle = if (settings.appGroups.isEmpty()) "Create app categories or folders that burst as floating bubbles." else "${settings.appGroups.size} groups configured • Fully searchable by name.",
                    onClick = onOpenAppGroups
                )
            }

            /*
             * --------------------------------------------------------------
             * Interaction
             * --------------------------------------------------------------
             */
            SettingsSection(title = "Interaction") {

                SegmentedPreference(
                    title = "Bubble speed",
                    options = remember { BubbleSpeed.entries.map { it.label } },
                    selectedIndex = settings.bubbleSpeed.ordinal,
                    onSelected = {
                        settingsRepository.setBubbleSpeed(
                            BubbleSpeed.entries[it]
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SegmentedPreference(
                    title = "Bubble behavior",
                    options = remember { BubbleBehavior.entries.map { it.label } },
                    selectedIndex = settings.bubbleBehavior.ordinal,
                    onSelected = {
                        settingsRepository.setBubbleBehavior(
                            BubbleBehavior.entries[it]
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SegmentedPreference(
                    title = "Press depth",
                    options = remember {
                        PopDifficulty.entries.map {
                            it.label.substringBefore(" (")
                        }
                    },
                    selectedIndex = settings.popDifficulty.ordinal,
                    onSelected = {
                        settingsRepository.setPopDifficulty(
                            PopDifficulty.entries[it]
                        )
                    }
                )
            }

            /*
             * --------------------------------------------------------------
             * Search
             * --------------------------------------------------------------
             */
            SettingsSection(title = "Search") {

                val mainActivity = context as? MainActivity

                SwitchPreferenceItem(
                    title = "Installed apps",
                    subtitle = "Search applications installed on this device.",
                    checked = settings.enabledProviders.contains(
                        SearchType.APP
                    ),
                    onCheckedChange = {
                        settingsRepository.toggleProvider(
                            SearchType.APP,
                            it
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val personEnabled = settings.enabledProviders.contains(SearchType.PERSON)
                val personHasPermission = remember(permissionRefreshTick, personEnabled) {
                    mainActivity?.hasPermissionForProvider(SearchType.PERSON) ?: true
                }
                val personSubtitle = when {
                    personEnabled && personHasPermission -> "Search contacts on this device."
                    personEnabled && !personHasPermission -> "Contacts permission required — tap to grant"
                    else -> "Search contacts after granting permission."
                }

                SwitchPreferenceItem(
                    title = "People",
                    subtitle = personSubtitle,
                    checked = personEnabled,
                    onCheckedChange = { enabled ->
                        settingsRepository.toggleProvider(
                            SearchType.PERSON,
                            enabled
                        )
                        if (enabled && !personHasPermission) {
                            showContactDisclosure = true
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val convEnabled = settings.enabledProviders.contains(SearchType.CONVERSATION)

                SwitchPreferenceItem(
                    title = "Conversation shortcuts",
                    subtitle = "Use available launcher conversation shortcuts.",
                    checked = convEnabled,
                    onCheckedChange = { enabled ->
                        settingsRepository.toggleProvider(
                            SearchType.CONVERSATION,
                            enabled
                        )
                    }
                )

                if (convEnabled) {
                    val convPackages = settings.conversationAppPackages
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PreferenceItem(
                        title = "Choose conversation apps",
                        subtitle = if (convPackages == null) "All apps enabled for shortcut search" else "${convPackages.size} apps selected",
                        onClick = onOpenConversationApps
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val fileEnabled = settings.enabledProviders.contains(SearchType.FILE)
                val fileHasAccess = remember(permissionRefreshTick, fileEnabled) {
                    mainActivity?.hasPermissionForProvider(SearchType.FILE) ?: true
                }
                val fileSubtitle = if (fileHasAccess) {
                    "Search documents, PDFs and downloads on this device"
                } else {
                    "Needs file access to find documents and downloads"
                }

                SwitchPreferenceItem(
                    title = "Files",
                    subtitle = fileSubtitle,
                    checked = fileEnabled,
                    onCheckedChange = { enabled ->
                        settingsRepository.toggleProvider(
                            SearchType.FILE,
                            enabled
                        )
                        if (enabled && !fileHasAccess) {
                            mainActivity?.requestFileSearchAccess()
                        }
                    }
                )

                if (fileEnabled && !fileHasAccess) {
                    PreferenceItem(
                        title = "Allow file access",
                        subtitle = "Grant storage permission to index local documents",
                        onClick = { mainActivity?.requestFileSearchAccess() }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val mediaEnabled = settings.enabledProviders.contains(SearchType.MEDIA)
                val mediaAccess = remember(permissionRefreshTick, mediaEnabled) {
                    mainActivity?.getMediaAccessState() ?: MediaAccessState.FULL
                }
                val mediaSubtitle = when (mediaAccess) {
                    MediaAccessState.FULL -> "Search local photos and videos."
                    MediaAccessState.SELECTED_ONLY -> "Limited access (selected photos only) — tap to grant full access."
                    MediaAccessState.DENIED -> "Media permission required — tap to grant."
                }

                SwitchPreferenceItem(
                    title = "Photos & videos",
                    subtitle = mediaSubtitle,
                    checked = mediaEnabled,
                    onCheckedChange = { enabled ->
                        settingsRepository.toggleProvider(
                            SearchType.MEDIA,
                            enabled
                        )
                        if (enabled && (mediaAccess != MediaAccessState.FULL)) {
                            mainActivity?.requestPermissionForProvider(SearchType.MEDIA)
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SwitchPreferenceItem(
                    title = "AI Overview",
                    subtitle = "Use AI to generate summaries when local search is insufficient.",
                    checked = settings.enabledProviders.contains(
                        SearchType.GOOGLE_OVERVIEW
                    ),
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            showAiDisclosure = true
                        } else {
                            settingsRepository.toggleProvider(
                                SearchType.GOOGLE_OVERVIEW,
                                false
                            )
                        }
                    }
                )

                if (settings.enabledProviders.contains(SearchType.GOOGLE_OVERVIEW)) {
                    PreferenceItem(
                        title = "Gemini API Key",
                        subtitle = if (settings.geminiApiKey.isNotBlank()) "Configured (Personal key active)" else "Not set (Using default DuckDuckGo & Wikipedia summaries)",
                        onClick = { showGeminiKeyDialog = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    val historyCount = settings.aiOverviewHistory.size
                    PreferenceItem(
                        title = "AI Overview History",
                        subtitle = if (historyCount == 0) "No search history recorded" else "$historyCount recent overview${if (historyCount == 1) "" else "s"} — tap to view",
                        onClick = { showAiHistoryDialog = true }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                PreferenceItem(
                    title = "Long-press Search AI Assistant",
                    subtitle = when (settings.defaultAiAppPackage) {
                        "com.google.android.apps.bard" -> "Google Gemini (${settings.defaultAiMode.label})"
                        "com.openai.chatgpt" -> "ChatGPT (${settings.defaultAiMode.label})"
                        "com.anthropic.claude" -> "Claude (${settings.defaultAiMode.label})"
                        "ai.perplexity.app.android" -> "Perplexity (${settings.defaultAiMode.label})"
                        "com.microsoft.copilot" -> "Microsoft Copilot (${settings.defaultAiMode.label})"
                        "" -> "Default Voice / Assistant (${settings.defaultAiMode.label})"
                        else -> {
                            val appName = apps.firstOrNull { it.matchesIdentifier(settings.defaultAiAppPackage) }?.label ?: settings.defaultAiAppPackage
                            "$appName (${settings.defaultAiMode.label})"
                        }
                    },
                    onClick = { showAiAssistantPicker = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SwitchPreferenceItem(
                    title = "Case-sensitive app search",
                    subtitle = "Match app names using the exact typed casing.",
                    checked = settings.caseSensitive,
                    onCheckedChange = {
                        settingsRepository.setCaseSensitive(it)
                    }
                )
            }

            /*
             * --------------------------------------------------------------
             * Privacy & Permissions
             * --------------------------------------------------------------
             */
            SettingsSection(title = "Privacy & Permissions") {

                PreferenceItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Smartphone,
                            contentDescription = null
                        )
                    },
                    title = "Check & grant permissions",
                    subtitle = "Request all missing system permissions for local search.",
                    onClick = {
                        (context as? MainActivity)?.requestSearchPermissions()
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                PreferenceItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.PrivacyTip,
                            contentDescription = null
                        )
                    },
                    title = "Privacy & data use",
                    subtitle = "See what is stored locally and what can leave the device.",
                    onClick = {
                        showPrivacy = true
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                PreferenceItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = null
                        )
                    },
                    title = "Privacy policy",
                    subtitle = if (hostedPolicyConfigured) {
                        "Open the current hosted policy."
                    } else {
                        "Configure the hosted policy link before release."
                    },
                    onClick = {
                        if (hostedPolicyConfigured) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        policyUrl.toUri()
                                    )
                                )
                            }
                        } else {
                            showPrivacy = true
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/* ========================================================================= */
/* Top bar                                                                   */
/* ========================================================================= */

@Composable
private fun SettingsTopBar(
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = 8.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back"
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/* ========================================================================= */
/* Section                                                                    */
/* ========================================================================= */

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = 4.dp,
                bottom = 2.dp
            )
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 6.dp
                ),
                content = content
            )
        }
    }
}

/* ========================================================================= */
/* Preference row                                                             */
/* ========================================================================= */

@Composable
private fun PreferenceItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(
                horizontal = 2.dp,
                vertical = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (icon != null) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(14.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

/* ========================================================================= */
/* Switch row                                                                 */
/* ========================================================================= */

@Composable
private fun SwitchPreferenceItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                onCheckedChange(!checked)
            }
            .padding(
                horizontal = 2.dp,
                vertical = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/* ========================================================================= */
/* Segmented choice                                                           */
/* ========================================================================= */

@Composable
private fun SegmentedPreference(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            options.forEachIndexed { index, option ->

                val selected = index == selectedIndex

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSelected(index)
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = option,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 10.dp
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* ========================================================================= */
/* Palette choices                                                            */
/* ========================================================================= */

@Composable
private fun PaletteChoices(
    selectedId: String,
    onSelect: (ThemePalette) -> Unit
) {
    val nonMonochromePalettes = remember {
        ThemePalette.entries.filterNot { it.isMonochrome }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(
                rememberScrollState()
            )
            .padding(
                vertical = 10.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        nonMonochromePalettes.forEach { palette ->

            val selected = selectedId == palette.id

            Column(
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onSelect(palette)
                    }
                    .padding(
                        vertical = 6.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {

                AccentPreview(
                    color = palette.previewDot,
                    selected = selected
                )
            }
        }
    }
}

/* ========================================================================= */
/* Accent preview                                                             */
/* ========================================================================= */

@Composable
private fun AccentPreview(
    color: Color,
    selected: Boolean
) {
    Box(
        modifier = Modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {

        if (selected) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(
                    if (selected) 28.dp else 30.dp
                )
                .background(
                    color,
                    CircleShape
                )
        )
    }
}

/* ========================================================================= */
/* Privacy dialog                                                             */
/* ========================================================================= */

@Composable
private fun PrivacyDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Privacy & data",
                fontWeight = FontWeight.SemiBold
            )
        },

        text = {
            Column(
                modifier = Modifier.verticalScroll(
                    rememberScrollState()
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                PolicySection(
                    title = "On your device",
                    body = "Pop Launcher reads installed-app metadata locally for launcher display and search; it is held in app memory rather than persisted. Persisted local data includes launcher settings, Android widget IDs, installed custom-widget definitions and state, ignored custom-widget IDs, friction-app choices, search affinity data, and the latest valid custom-widget manifest cache. These remain until changed, removed, app data is cleared, or the app is uninstalled."
                )

                PolicySection(
                    title = "Contacts",
                    body = "People search uses Android Contacts permission only after you choose to enable it. Contact names and phone numbers are processed locally and are not uploaded to a Pop Launcher backend. You can revoke the permission from Android settings."
                )

                PolicySection(
                    title = "Custom widgets",
                    body = "The creator's widget catalog is retrieved over HTTPS. Only bounded structured JSON is accepted; the app does not download or execute arbitrary code. Widgets are never installed automatically. You choose Add, Ignore, Update, or Remove. A deleted remote catalog entry does not remotely uninstall an already installed copy."
                )

                PolicySection(
                    title = "AI & Web services",
                    body = "When AI overview is enabled and local results are insufficient, the search query may be sent to an AI search provider to generate a summary. No personal data is included."
                )

                PolicySection(
                    title = "No account or analytics service",
                    body = "This build has no Pop Launcher account, subscription, payment, advertising SDK, Firebase backend, analytics service, crash-reporting service, or remote widget-generation service."
                )

                PolicySection(
                    title = "Your choices",
                    body = "You can disable search providers, revoke Contacts permission, ignore or remove custom widgets, change the accent color, or clear app data/uninstall the app to remove local data."
                )
            }
        },

        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Done")
            }
        }
    )
}

/* ========================================================================= */
/* Policy section                                                             */
/* ========================================================================= */

@Composable
private fun PolicySection(
    title: String,
    body: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ========================================================================= */
/* Contact dialog                                                             */
/* ========================================================================= */

@Composable
private fun ContactDisclosureDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Enable contact search?",
                fontWeight = FontWeight.SemiBold
            )
        },

        text = {
            Text(
                text = "Pop Launcher will search contact names and phone numbers locally on this device. Contact data is not uploaded to a Pop Launcher server."
            )
        },

        confirmButton = {
            Button(
                onClick = onContinue
            ) {
                Text("Continue")
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Not now")
            }
        }
    )
}

/* ========================================================================= */
/* Gesture Action Picker Dialog                                               */
/* ========================================================================= */

@Composable
private fun GestureActionPickerDialog(
    gestureType: GestureType,
    currentConfig: GestureConfig,
    onDismiss: () -> Unit,
    onSelectAction: (GestureActionType) -> Unit,
    onChooseApp: () -> Unit,
    onChooseGroup: () -> Unit
) {
    val availableActions = remember {
        @Suppress("DEPRECATION")
        GestureActionType.entries.filter {
            it != GestureActionType.OPEN_NOTIFICATIONS &&
                    it != GestureActionType.OPEN_SETTINGS &&
                    it != GestureActionType.LOCK_SCREEN
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = gestureType.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = gestureType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableActions.forEach { action ->
                    @Suppress("DEPRECATION")
                    val isSelected = currentConfig.actionType == action ||
                            (action == GestureActionType.OPEN_NOTIFICATION_SHADE && currentConfig.actionType == GestureActionType.OPEN_NOTIFICATIONS)

                    val actionTitle = when (action) {
                        GestureActionType.NONE -> "None"
                        GestureActionType.OPEN_APP -> "Open specific app"
                        GestureActionType.OPEN_APP_GROUP -> "Open app group"
                        GestureActionType.OPEN_SEARCH -> "Universal search"
                        GestureActionType.OPEN_SETTINGS -> "Launcher settings"
                        GestureActionType.OPEN_NOTIFICATION_SHADE -> "Notification shade"
                        @Suppress("DEPRECATION")
                        GestureActionType.OPEN_NOTIFICATIONS -> "Notification shade"
                        GestureActionType.OPEN_PRIVATE_SPACE -> "System Private Space"
                        GestureActionType.OPEN_HIDDEN_APPS -> "Launcher Hidden Apps"
                        @Suppress("DEPRECATION")
                        GestureActionType.LOCK_SCREEN -> "Lock screen"
                    }
                    val actionSubtitle = when (action) {
                        GestureActionType.NONE -> "Do nothing on gesture trigger"
                        GestureActionType.OPEN_APP -> if (isSelected && !currentConfig.targetLabel.isNullOrBlank()) "Launch ${currentConfig.targetLabel}" else "Choose an installed app to launch"
                        GestureActionType.OPEN_APP_GROUP -> if (isSelected && !currentConfig.targetLabel.isNullOrBlank()) "Open ${currentConfig.targetLabel} bubbles" else "Choose a custom app group to emerge as bubbles"
                        GestureActionType.OPEN_SEARCH -> "Quickly pull up universal device search"
                        GestureActionType.OPEN_SETTINGS -> "Customize launcher preferences and layout"
                        GestureActionType.OPEN_NOTIFICATION_SHADE -> "Expand system notifications and quick settings"
                        @Suppress("DEPRECATION")
                        GestureActionType.OPEN_NOTIFICATIONS -> "Expand system notifications"
                        GestureActionType.OPEN_PRIVATE_SPACE -> "Unlock Android 15 Private Space / OEM Safe"
                        GestureActionType.OPEN_HIDDEN_APPS -> "Release hidden apps as interactive floating bubbles"
                        @Suppress("DEPRECATION")
                        GestureActionType.LOCK_SCREEN -> "Instantly turn off and lock the display"
                    }
                    val icon = when (action) {
                        GestureActionType.NONE -> Icons.Outlined.Close
                        GestureActionType.OPEN_APP -> Icons.Outlined.Apps
                        GestureActionType.OPEN_APP_GROUP -> Icons.Outlined.Folder
                        GestureActionType.OPEN_SEARCH -> Icons.Outlined.Search
                        GestureActionType.OPEN_SETTINGS -> Icons.Outlined.Settings
                        GestureActionType.OPEN_NOTIFICATION_SHADE -> Icons.Outlined.Notifications
                        @Suppress("DEPRECATION")
                        GestureActionType.OPEN_NOTIFICATIONS -> Icons.Outlined.Notifications
                        GestureActionType.OPEN_PRIVATE_SPACE -> Icons.Outlined.Security
                        GestureActionType.OPEN_HIDDEN_APPS -> Icons.Outlined.AutoAwesome
                        @Suppress("DEPRECATION")
                        GestureActionType.LOCK_SCREEN -> Icons.Outlined.Lock
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                when (action) {
                                    GestureActionType.OPEN_APP -> onChooseApp()
                                    GestureActionType.OPEN_APP_GROUP -> onChooseGroup()
                                    else -> onSelectAction(action)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = actionTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = actionSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/* ========================================================================= */
/* App Picker Dialog                                                         */
/* ========================================================================= */

@Composable
private fun AppPickerDialog(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val isMonochrome = LocalIsMonochromeTheme.current

    val distinctApps = remember(apps) {
        apps.distinctBy { it.uniqueId }.sortedBy { it.label.lowercase() }
    }

    val filteredApps = remember(distinctApps, searchQuery) {
        if (searchQuery.isBlank()) {
            distinctApps
        } else {
            val q = searchQuery.trim().lowercase()
            distinctApps.filter { it.label.lowercase().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Application",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp)
                )

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No apps found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.uniqueId }
                        ) { app ->
                            val bitmap = remember(app.icon, isMonochrome) {
                                app.icon?.let { drawableToBitmap(it, isMonochrome) }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onAppSelected(app) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (bitmap != null && !bitmap.isRecycled) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (app.isClone) {
                                        Text(
                                            text = "Dual / Cloned App",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/* ========================================================================= */
/* App Group Picker Dialog for Gestures                                      */
/* ========================================================================= */

@Composable
private fun AppGroupPickerDialog(
    groups: List<AppGroup>,
    onDismiss: () -> Unit,
    onGroupSelected: (AppGroup) -> Unit,
    onCreateNewGroup: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select App Group",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No App Groups Created",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a group to assign it to this gesture.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onCreateNewGroup) {
                                Text("Create Group")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = groups, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onGroupSelected(group) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${group.packageNames.size} apps configured",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/* ========================================================================= */
/* Launcher detection                                                         */
/* ========================================================================= */

fun isDefaultLauncher(
    context: Context
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true) {
            return true
        }
    }

    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = context.packageManager.resolveActivity(
        intent,
        PackageManager.MATCH_DEFAULT_ONLY
    )
    val defaultPkg = resolveInfo?.activityInfo?.packageName
    if (defaultPkg == context.packageName) {
        return true
    }

    // Fallback check for older / OEM launchers
    val filter = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
    val filters = listOf(filter)
    val preferredActivities = mutableListOf<ComponentName>()
    try {
        @Suppress("DEPRECATION")
        context.packageManager.getPreferredActivities(filters, preferredActivities, context.packageName)
    } catch (_: Exception) {}
    return preferredActivities.any { it.packageName == context.packageName }
}

fun requestSetDefaultLauncher(context: Context) {
    var activity: Activity? = null
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) {
            activity = ctx
            break
        }
        ctx = ctx.baseContext
    }

    if (activity is MainActivity) {
        activity.requestSetDefaultLauncher()
        return
    }

    // 1. Try RoleManager on Android 10+ (API 29+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            try {
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                return
            } catch (_: Exception) {}
        }
    }

    // 2. Try ACTION_HOME_SETTINGS
    try {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(intent)
        }
        return
    } catch (_: Exception) {}

    // 3. Try ACTION_MANAGE_DEFAULT_APPS_SETTINGS
    try {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(intent)
        }
        return
    } catch (_: Exception) {}

    // 4. Try ACTION_APPLICATION_DETAILS_SETTINGS for Pop Launcher
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri()
        ).apply {
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(intent)
        }
        return
    } catch (_: Exception) {}

    // 5. Fallback: Trigger Home Resolver / Picker dialog
    try {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(homeIntent)
    } catch (_: Exception) {
        // 6. Last resort fallback: System Settings
        try {
            val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
        } catch (_: Exception) {}
    }
}
