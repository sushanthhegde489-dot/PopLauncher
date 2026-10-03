
package com.sushanth.poplauncher.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sushanth.poplauncher.MainActivity
import com.sushanth.poplauncher.R
import com.sushanth.poplauncher.data.model.BubbleBehavior
import com.sushanth.poplauncher.data.model.BubbleSpeed
import com.sushanth.poplauncher.data.model.AppGroup
import com.sushanth.poplauncher.data.model.GestureActionType
import com.sushanth.poplauncher.data.model.GestureConfig
import com.sushanth.poplauncher.data.model.GestureType
import com.sushanth.poplauncher.data.model.PopDifficulty
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.AppRepository
import com.sushanth.poplauncher.data.repository.InstalledApp
import com.sushanth.poplauncher.data.repository.SearchRepository
import com.sushanth.poplauncher.data.repository.SettingsRepository
import com.sushanth.poplauncher.ui.theme.LocalIsMonochromeTheme
import com.sushanth.poplauncher.ui.theme.ThemePalette
import com.sushanth.poplauncher.ui.theme.parseHexColor
import kotlin.math.min

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    searchRepository: SearchRepository? = null,
    appRepository: AppRepository? = null,
    onBackClick: () -> Unit,
    onOpenFrictionApps: () -> Unit,
    onOpenConversationApps: () -> Unit = {},
    onOpenHiddenApps: () -> Unit = {},
    onOpenAppGroups: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsState()
    val apps by (appRepository?.installedApps?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val lifecycleOwner = LocalLifecycleOwner.current

    var isDefaultLauncher by remember {
        mutableStateOf(isDefaultLauncher(context))
    }
    var permissionRefreshTick by remember { mutableStateOf(0) }

    var showCustomColor by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showContactDisclosure by remember { mutableStateOf(false) }
    var configuringGesture by remember { mutableStateOf<GestureType?>(null) }
    var choosingAppForGesture by remember { mutableStateOf<GestureType?>(null) }
    var choosingGroupForGesture by remember { mutableStateOf<GestureType?>(null) }

    val policyUrl = androidx.compose.ui.res.stringResource(
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

    /*
     * Actual visual color picker.
     */
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
                val hex = String.format(
                    "#%02X%02X%02X",
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt()
                )

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

    configuringGesture?.let { gestureType ->
        val currentConfig = settings.gestures[gestureType] ?: GestureConfig()
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
                val g = gestureType
                configuringGesture = null
                choosingAppForGesture = g
            },
            onChooseGroup = {
                val g = gestureType
                configuringGesture = null
                choosingGroupForGesture = g
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            /*
             * --------------------------------------------------------------
             * Appearance
             * --------------------------------------------------------------
             *
             * When monochrome is enabled, all accent-color controls disappear.
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

                    /*
                     * Fixed palette spacing:
                     *
                     * - no large leading gap
                     * - equal spacing between every palette
                     * - content starts exactly at the section padding
                     */
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
                            settingsRepository.setThemePalette(
                                if (checked) {
                                    ThemePalette.MONOCHROME
                                } else {
                                    ThemePalette.DEFAULT_MAROON
                                }
                            )
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
                    val selectedDimIndex = dimValues.indexOfFirst { kotlin.math.abs(it - settings.wallpaperDim) < 0.05f }.let {
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

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SwitchPreferenceItem(
                        title = "Show date",
                        subtitle = "Display current date below the clock.",
                        checked = settings.showDate,
                        onCheckedChange = { checked ->
                            settingsRepository.setShowDate(checked)
                        }
                    )
                }

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
                    val config = settings.gestures[gestureType] ?: GestureConfig()
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
             *
             * Custom Widgets has intentionally been removed.
             *
             * Default launcher only appears when Pop Launcher isn't already
             * the default Home app.
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
                    options = BubbleSpeed.entries.map { it.label },
                    selectedIndex = settings.bubbleSpeed.ordinal,
                    onSelected = {
                        // Debounce by doing nothing here if logic is complex, 
                        // but simple state sets are fast enough.
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
                    options = BubbleBehavior.entries.map { it.label },
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
                    options = PopDifficulty.entries.map {
                        it.label.substringBefore(" (")
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
                val personHasPermission = mainActivity?.hasPermissionForProvider(SearchType.PERSON) ?: true
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
                        subtitle = "Opens system settings — turn on \"Allow access to manage all files\"",
                        onClick = { mainActivity?.requestFileSearchAccess() }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val mediaEnabled = settings.enabledProviders.contains(SearchType.MEDIA)
                val mediaAccess = mainActivity?.getMediaAccessState() ?: com.sushanth.poplauncher.MediaAccessState.FULL
                val mediaSubtitle = when (mediaAccess) {
                    com.sushanth.poplauncher.MediaAccessState.FULL -> "Search local photos and videos."
                    com.sushanth.poplauncher.MediaAccessState.SELECTED_ONLY -> "Limited access (selected photos only) — tap to grant full access."
                    com.sushanth.poplauncher.MediaAccessState.DENIED -> "Media permission required — tap to grant."
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
                        if (enabled && mediaAccess != com.sushanth.poplauncher.MediaAccessState.FULL) {
                            // On Android 14+, selected-photo access can be upgraded by requesting
                            // the media permissions again.
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
                    onCheckedChange = {
                        settingsRepository.toggleProvider(
                            SearchType.GOOGLE_OVERVIEW,
                            it
                        )
                    }
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

        ThemePalette.entries
            .filterNot { it.isMonochrome }
            .forEach { palette ->

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
/* Divider                                                                    */
/* ========================================================================= */

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/* ========================================================================= */
/* Actual visual color picker                                                 */
/* ========================================================================= */

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit
) {
    val hsv = remember(initialColor) {
        val values = FloatArray(3)

        AndroidColor.colorToHSV(
            initialColor.toArgb(),
            values
        )

        values
    }

    var hue by remember {
        mutableFloatStateOf(hsv[0])
    }

    var saturation by remember {
        mutableFloatStateOf(hsv[1])
    }

    var value by remember {
        mutableFloatStateOf(hsv[2])
    }

    val selectedColor = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(
                hue,
                saturation,
                value
            )
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Choose accent color",
                fontWeight = FontWeight.SemiBold
            )
        },

        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                /*
                 * Saturation / brightness picker.
                 */
                SaturationValuePicker(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChanged = { newSaturation, newValue ->
                        saturation = newSaturation
                        value = newValue
                    }
                )

                /*
                 * Hue picker.
                 */
                HuePicker(
                    hue = hue,
                    onHueChanged = {
                        hue = it
                    }
                )

                /*
                 * Current color preview.
                 */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(selectedColor)
                    )

                    Spacer(
                        modifier = Modifier.width(14.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Selected color",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = colorToHex(selectedColor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },

        confirmButton = {
            Button(
                onClick = {
                    onApply(selectedColor)
                }
            ) {
                Text("Apply")
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

/* ========================================================================= */
/* Saturation + value picker                                                  */
/* ========================================================================= */

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onChanged: (Float, Float) -> Unit
) {
    val hueColor = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(
                hue,
                1f,
                1f
            )
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(hueColor)
            .pointerInput(hue) {

                fun update(position: Offset) {
                    val x = position.x
                        .coerceIn(0f, size.width.toFloat())

                    val y = position.y
                        .coerceIn(0f, size.height.toFloat())

                    val newSaturation =
                        (x / size.width)
                            .coerceIn(0f, 1f)

                    val newValue =
                        1f - (
                                y / size.height
                                ).coerceIn(0f, 1f)

                    onChanged(
                        newSaturation,
                        newValue
                    )
                }

                detectTapGestures { position ->
                    update(position)
                }
            }
            .pointerInput(hue) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()

                        val x = change.position.x
                            .coerceIn(
                                0f,
                                size.width.toFloat()
                            )

                        val y = change.position.y
                            .coerceIn(
                                0f,
                                size.height.toFloat()
                            )

                        onChanged(
                            x / size.width,
                            1f - (
                                    y / size.height
                                    )
                        )
                    }
                )
            }
    ) {

        /*
         * White → transparent.
         */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White,
                            Color.Transparent
                        )
                    )
                )
        )

        /*
         * Transparent → black.
         */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black
                        )
                    )
                )
        )

        /*
         * Selection indicator.
         */
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    Color.Transparent,
                    CircleShape
                )
                .borderCircle(
                    color = Color.White,
                    width = 2.dp
                )
                .align(
                    Alignment(
                        saturation,
                        1f - value
                    )
                )
        )
    }
}

/* ========================================================================= */
/* Hue picker                                                                 */
/* ========================================================================= */

@Composable
private fun HuePicker(
    hue: Float,
    onHueChanged: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {

                fun update(x: Float) {
                    onHueChanged(
                        (
                                x / size.width
                                ).coerceIn(0f, 1f) * 360f
                    )
                }

                detectTapGestures { position ->
                    update(position.x)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        onHueChanged(
                            (
                                    change.position.x / size.width
                                    ).coerceIn(0f, 1f) * 360f
                        )
                    }
                )
            }
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    )
                )
            )
        }

        /*
         * Hue selection indicator.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 1.dp
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .borderCircle(
                        color = Color.Black.copy(alpha = 0.25f),
                        width = 1.dp
                    )
                    .align(
                        Alignment(
                            hue / 360f,
                            0.5f
                        )
                    )
            )
        }
    }
}

/* ========================================================================= */
/* Small helper for placing the picker indicator                             */
/* ========================================================================= */

private fun Modifier.borderCircle(
    color: Color,
    width: androidx.compose.ui.unit.Dp
): Modifier {
    return this.then(
        Modifier
            .background(
                color = color,
                shape = CircleShape
            )
            .padding(width)
    )
}

/*
 * Alignment helper for normalized coordinates.
 */
private fun Alignment(
    x: Float,
    y: Float
): Alignment {
    return object : Alignment {
        override fun align(
            size: androidx.compose.ui.unit.IntSize,
            space: androidx.compose.ui.unit.IntSize,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection
        ): androidx.compose.ui.unit.IntOffset {
            return androidx.compose.ui.unit.IntOffset(
                (
                        (space.width - size.width) * x
                        ).toInt(),
                (
                        (space.height - size.height) * y
                        ).toInt()
            )
        }
    }
}

/* ========================================================================= */
/* Color helpers                                                              */
/* ========================================================================= */

private fun colorToHex(
    color: Color
): String {
    return String.format(
        "#%02X%02X%02X",
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
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
                @Suppress("DEPRECATION")
                val availableActions = GestureActionType.entries.filter {
                    it != GestureActionType.OPEN_NOTIFICATIONS &&
                    it != GestureActionType.OPEN_SETTINGS &&
                    it != GestureActionType.LOCK_SCREEN
                }
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

    val filteredApps = remember(apps, searchQuery) {
        val distinct = apps.distinctBy { it.uniqueId }.sortedBy { it.label.lowercase() }
        if (searchQuery.isBlank()) {
            distinct
        } else {
            val q = searchQuery.trim().lowercase()
            distinct.filter { it.label.lowercase().contains(q) }
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
                                app.icon?.let { com.sushanth.poplauncher.util.drawableToBitmap(it, isMonochrome) }
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
        val roleManager =
            context.getSystemService(RoleManager::class.java)

        if (
            roleManager?.isRoleHeld(
                RoleManager.ROLE_HOME
            ) == true
        ) {
            return true
        }
    }

    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)

    val resolveInfo =
        context.packageManager.resolveActivity(
            intent,
            0
        )

    return resolveInfo
        ?.activityInfo
        ?.packageName == context.packageName
}

fun requestSetDefaultLauncher(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    try {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}


