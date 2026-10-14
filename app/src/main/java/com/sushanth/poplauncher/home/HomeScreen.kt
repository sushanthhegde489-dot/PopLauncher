package com.sushanth.poplauncher.home

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.provider.Settings
import android.util.Log
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sushanth.poplauncher.MainActivity
import com.sushanth.poplauncher.bubble.FloatingAppBubbleContainer
import com.sushanth.poplauncher.bubble.MultiAppBubbleContainer
import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.AppGroup
import com.sushanth.poplauncher.data.model.AppGroupEntity
import com.sushanth.poplauncher.data.model.GestureActionType
import com.sushanth.poplauncher.data.model.GestureConfig
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.AppRepository
import com.sushanth.poplauncher.data.repository.SearchRepository
import com.sushanth.poplauncher.data.repository.SettingsRepository
import com.sushanth.poplauncher.search.SearchOverlay
import com.sushanth.poplauncher.settings.isDefaultLauncher
import com.sushanth.poplauncher.settings.requestSetDefaultLauncher
import com.sushanth.poplauncher.util.PrivateSpaceHelper
import com.sushanth.poplauncher.widgets.DedicatedWidgetPage
import com.sushanth.poplauncher.widgets.LauncherAppWidgetHost
import com.sushanth.poplauncher.widgets.WidgetManager
import com.sushanth.poplauncher.widgets.custom.ActionType
import com.sushanth.poplauncher.widgets.custom.CustomWidgetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    appRepository: AppRepository,
    searchRepository: SearchRepository,
    settingsRepository: SettingsRepository,
    widgetManager: WidgetManager,
    customWidgetRepository: CustomWidgetRepository,
    appWidgetHost: LauncherAppWidgetHost,
    onOpenSettings: () -> Unit,
    onOpenHiddenAppsSetup: () -> Unit,
    onAddWidgetClick: () -> Unit,
    onLaunchEntity: (SearchEntity, String) -> Unit
) {
    val searchState by searchRepository.searchState.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val apps by appRepository.installedApps.collectAsState()
    val pageWidgetIds by widgetManager.pageWidgetIds.collectAsState()
    val widgetLayouts by widgetManager.widgetLayouts.collectAsState()
    val customWidgets by customWidgetRepository.customWidgets.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var isContextMenuOpen by remember { mutableStateOf(false) }
    var isHiddenAppsBubblesOpen by remember { mutableStateOf(false) }
    var activeGroupBubbles by remember { mutableStateOf<AppGroup?>(null) }

    val pagerState = rememberPagerState { 2 }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppResumed by remember { mutableStateOf(true) }
    var isDefault by remember { mutableStateOf(isDefaultLauncher(context)) }
    var fileAccessMissing by remember {
        mutableStateOf(mainActivity?.hasPermissionForProvider(SearchType.FILE) == false)
    }

    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }
    var tapCount by remember { mutableIntStateOf(0) }
    var pendingTapJob by remember { mutableStateOf<Job?>(null) }
    var lastActionExecutionTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAppResumed = true
                isDefault = isDefaultLauncher(context)
                fileAccessMissing = mainActivity?.hasPermissionForProvider(SearchType.FILE) == false
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                isAppResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            pendingTapJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = isSearchOpen || isContextMenuOpen || searchState.activeBubbleApp != null || isHiddenAppsBubblesOpen || activeGroupBubbles != null) {
        if (searchState.activeBubbleApp != null) {
            searchQuery = ""
            searchRepository.clearSearch()
        } else if (activeGroupBubbles != null) {
            activeGroupBubbles = null
        } else if (isHiddenAppsBubblesOpen) {
            isHiddenAppsBubblesOpen = false
        } else if (isContextMenuOpen) {
            isContextMenuOpen = false
        } else if (isSearchOpen) {
            isSearchOpen = false
            searchQuery = ""
            searchRepository.clearSearch()
        }
    }

    val timePattern = if (settings.clock24Hour) "HH:mm" else "hh:mm a"
    val timeFormatter = remember(settings.clock24Hour) {
        DateTimeFormatter.ofPattern(timePattern, Locale.getDefault())
    }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    }

    var currentTime by remember {
        mutableStateOf(LocalDateTime.now(ZoneId.systemDefault()).format(timeFormatter))
    }
    var currentDate by remember {
        mutableStateOf(LocalDateTime.now(ZoneId.systemDefault()).format(dateFormatter))
    }

    // Only tick clock when app is resumed and clock or date is visible on page 0
    val shouldTickClock = isAppResumed && (settings.showClock || settings.showDate)
    LaunchedEffect(shouldTickClock, timeFormatter, dateFormatter) {
        if (!shouldTickClock) return@LaunchedEffect
        while (true) {
            val now = LocalDateTime.now(ZoneId.systemDefault())
            currentTime = now.format(timeFormatter)
            currentDate = now.format(dateFormatter)
            // Align to the next minute boundary instead of ticking every second
            val millisUntilNextMinute = (60_000L - (System.currentTimeMillis() % 60_000L)).coerceAtLeast(1000L)
            delay(millisUntilNextMinute)
        }
    }

    fun executeGestureAction(config: GestureConfig) {
        val now = System.currentTimeMillis()
        if (now - lastActionExecutionTime < 400L) return
        lastActionExecutionTime = now

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (config.actionType) {
            GestureActionType.OPEN_APP -> {
                val target = config.targetPackage
                if (!target.isNullOrBlank()) {
                    val matchingApp = apps.firstOrNull { it.matchesIdentifier(target) }
                    if (matchingApp != null && matchingApp.componentName != null && matchingApp.userHandle != null) {
                        val launcherApps = context.getSystemService(LauncherApps::class.java)
                        try {
                            launcherApps?.startMainActivity(matchingApp.componentName, matchingApp.userHandle, null, null)
                        } catch (_: Exception) {
                            val intent = matchingApp.launchIntent
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                    } else {
                        val pkg = target.substringBefore("#")
                        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                    }
                }
            }
            GestureActionType.OPEN_APP_GROUP -> {
                val groupId = config.targetPackage // Stored group ID
                val group = settings.appGroups.firstOrNull { it.id == groupId }
                if (group != null && group.packageNames.isNotEmpty()) {
                    activeGroupBubbles = group
                } else if (settings.appGroups.isNotEmpty()) {
                    activeGroupBubbles = settings.appGroups.first()
                }
            }
            GestureActionType.OPEN_SEARCH -> {
                isSearchOpen = true
            }
            GestureActionType.OPEN_SETTINGS -> {
                onOpenSettings()
            }
            GestureActionType.OPEN_NOTIFICATION_SHADE,
            @Suppress("DEPRECATION")
            GestureActionType.OPEN_NOTIFICATIONS -> {
                expandNotificationShade(context)
            }
            GestureActionType.OPEN_PRIVATE_SPACE -> {
                val opened = PrivateSpaceHelper.openPrivateSpace(context)
                if (!opened) {
                    Toast.makeText(context, "Private Space is not supported or set up on this device", Toast.LENGTH_SHORT).show()
                }
            }
            GestureActionType.OPEN_HIDDEN_APPS -> {
                if (!settings.hasSetupHiddenApps || settings.hiddenAppPackages.isEmpty()) {
                    onOpenHiddenAppsSetup()
                } else {
                    isHiddenAppsBubblesOpen = true
                }
            }
            @Suppress("DEPRECATION")
            GestureActionType.LOCK_SCREEN -> {
                // Device admin lock screen removed as requested
            }
            GestureActionType.NONE -> {}
        }
    }

    // Fast debounced search trigger (80ms for typing responsiveness, 0ms on clear)
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            delay(80)
        }
        searchRepository.search(searchQuery)
    }

    // Dismiss search window when exact friction bubble pops up
    LaunchedEffect(searchState.activeBubbleApp) {
        if (searchState.activeBubbleApp != null) {
            isSearchOpen = false
        }
    }

    val bubbleConfig = remember(settings.bubbleSpeed, settings.bubbleBehavior, settings.popDifficulty) {
        settingsRepository.getBubbleConfig()
    }

    val showDefaultLauncherBanner = settings.hasCompletedOnboarding && !isDefault
    val showFileAccessBanner = settings.hasCompletedOnboarding &&
        settings.enabledProviders.contains(SearchType.FILE) && fileAccessMissing

    // Wallpaper / Transparent / Solid Background Main Scaffold
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (settings.showWallpaper) {
                    if (settings.wallpaperDim > 0f) Color.Black.copy(alpha = settings.wallpaperDim)
                    else Color.Transparent
                } else {
                    MaterialTheme.colorScheme.background
                }
            )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    // =========================================================
                    // PAGE 0: MAIN HOME SCREEN (Ultra-minimal, calm, zero widgets)
                    // =========================================================
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(settings) {
                                val touchSlop = viewConfiguration.touchSlop
                                val minSwipeDistance = 85.dp.toPx()
                                val maxTapRadius = 36.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    val downTime = System.currentTimeMillis()
                                    val startPos = down.position
                                    var isDragDetected = false
                                    var dragConsumed = false
                                    var swipeTriggered = false

                                    val tapDistance = (startPos - lastTapPos).getDistance()
                                    if (downTime - lastTapTime < 320L && tapDistance <= maxTapRadius) {
                                        tapCount++
                                        pendingTapJob?.cancel()
                                    } else {
                                        tapCount = 1
                                        pendingTapJob?.cancel()
                                    }
                                    lastTapPos = startPos

                                    val longPressJob = scope.launch {
                                        delay(viewConfiguration.longPressTimeoutMillis)
                                        if (!isDragDetected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isContextMenuOpen = true
                                            tapCount = 0
                                            lastTapTime = 0L
                                        }
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val current = event.changes.firstOrNull() ?: break
                                        if (!current.pressed) {
                                            longPressJob.cancel()
                                            if (!isDragDetected && !current.isConsumed) {
                                                lastTapTime = System.currentTimeMillis()
                                                val currentTaps = tapCount
                                                val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong().coerceIn(180L, 240L)
                                                val hasDoubleTap = settings.gestureDoubleTap.actionType != GestureActionType.NONE
                                                val hasTripleTap = settings.gestureTripleTap.actionType != GestureActionType.NONE

                                                if (currentTaps >= 3) {
                                                    tapCount = 0
                                                    lastTapTime = 0L
                                                    executeGestureAction(settings.gestureTripleTap)
                                                } else if (currentTaps == 2) {
                                                    if (hasTripleTap) {
                                                        pendingTapJob = scope.launch {
                                                            delay(doubleTapTimeout)
                                                            tapCount = 0
                                                            lastTapTime = 0L
                                                            executeGestureAction(settings.gestureDoubleTap)
                                                        }
                                                    } else {
                                                        tapCount = 0
                                                        lastTapTime = 0L
                                                        executeGestureAction(settings.gestureDoubleTap)
                                                    }
                                                } else if (currentTaps == 1) {
                                                    if (hasDoubleTap || hasTripleTap) {
                                                        pendingTapJob = scope.launch {
                                                            delay(doubleTapTimeout)
                                                            tapCount = 0
                                                            lastTapTime = 0L
                                                            if (isContextMenuOpen) isContextMenuOpen = false
                                                        }
                                                    } else {
                                                        tapCount = 0
                                                        lastTapTime = 0L
                                                        if (isContextMenuOpen) isContextMenuOpen = false
                                                    }
                                                }
                                            } else {
                                                tapCount = 0
                                                lastTapTime = 0L
                                            }
                                            break
                                        }

                                        val dx = current.position.x - startPos.x
                                        val dy = current.position.y - startPos.y
                                        val absDx = abs(dx)
                                        val absDy = abs(dy)

                                        if (!isDragDetected && (absDy > touchSlop || absDx > touchSlop)) {
                                            isDragDetected = true
                                            longPressJob.cancel()
                                        }

                                        if (!swipeTriggered && absDy >= minSwipeDistance && absDy > absDx * 1.8f) {
                                            swipeTriggered = true
                                            dragConsumed = true
                                            current.consume()
                                            tapCount = 0
                                            lastTapTime = 0L
                                            if (dy > 0) {
                                                executeGestureAction(settings.gestureSwipeDown)
                                            } else {
                                                executeGestureAction(settings.gestureSwipeUp)
                                            }
                                        } else if (dragConsumed) {
                                            current.consume()
                                        }
                                    }
                                }
                            }
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (showDefaultLauncherBanner) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
                                        .clickable {
                                            requestSetDefaultLauncher(context)
                                        }
                                        .padding(16.dp)
                                 ) {
                                    Column {
                                        Text(
                                            text = "Set Pop Launcher as Default",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tap to choose your Home app",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            if (showFileAccessBanner) {
                                if (showDefaultLauncherBanner) Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f))
                                        .clickable { mainActivity?.requestFileSearchAccess() }
                                        .padding(16.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Allow file access",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Needed to find documents, PDFs and downloads in search",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(56.dp))

                            // 1. FLOATING CLOCK & DATE
                            if (settings.showClock) {
                                Text(
                                    text = currentTime,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 68.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-2).sp
                                )
                            }
                            if (settings.showDate) {
                                if (settings.showClock) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Text(
                                    text = currentDate,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0.2.sp
                                )
                            }

                            // Clean, calm empty space fills the home screen (ZERO widgets on Page 0)
                            Spacer(modifier = Modifier.weight(1f))

                            // 2. CONTINUOUS FLOATING MOVING CIRCULAR SEARCH BUTTON
                            val shouldAnimateSearchButton = settings.searchAnimationEnabled &&
                                isAppResumed &&
                                !isSearchOpen &&
                                pagerState.currentPage == 0 &&
                                searchState.activeBubbleApp == null &&
                                !isHiddenAppsBubblesOpen &&
                                activeGroupBubbles == null

                            FloatingSearchButton(
                                animate = shouldAnimateSearchButton,
                                onClick = {
                                    pendingTapJob?.cancel()
                                    tapCount = 0
                                    lastTapTime = 0L
                                    isSearchOpen = true
                                }
                            )

                            Spacer(modifier = Modifier.height(44.dp))
                        }
                    }
                }
                1 -> {
                    // =========================================================
                    // PAGE 1: DEDICATED WIDGET PAGE (Swipe Right)
                    // =========================================================
                    DedicatedWidgetPage(
                        widgetIds = pageWidgetIds,
                        customWidgets = customWidgets,
                        widgetLayouts = widgetLayouts,
                        appWidgetHost = appWidgetHost,
                        widgetManager = widgetManager,
                        onRemoveWidget = { id ->
                            widgetManager.removePageWidget(id, appWidgetHost)
                        },
                        onRemoveCustomWidget = { id ->
                            customWidgetRepository.deleteCustomWidget(id)
                            widgetManager.removeLayout("custom:$id")
                        },
                        onUpdateWidgetLayout = { key, gridX, gridY, spanX, spanY ->
                            widgetManager.updateWidgetLayout(key, gridX, gridY, spanX, spanY)
                        },
                        onActionCustomWidget = { widgetId, action, targetVar, param ->
                            when (action) {
                                ActionType.OPEN_APP -> {
                                    val pkg = param.ifBlank { targetVar }.trim()
                                    if (pkg.isNotEmpty()) {
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                                        if (launchIntent != null) {
                                            runCatching { context.startActivity(launchIntent) }
                                        }
                                    }
                                }
                                ActionType.OPEN_SETTINGS -> {
                                    val settingType = param.ifBlank { targetVar }.trim().lowercase()
                                    val intent = when (settingType) {
                                        "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                                        "bluetooth", "bt" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                        "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                                        "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                        "sound", "volume" -> Intent(Settings.ACTION_SOUND_SETTINGS)
                                        "apps", "applications" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
                                        else -> Intent(Settings.ACTION_SETTINGS)
                                    }
                                    runCatching {
                                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                }
                                ActionType.OPEN_URL -> {
                                    val url = param.ifBlank { targetVar }.trim()
                                    if (url.isNotEmpty()) {
                                        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    formattedUrl.toUri()
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                }
                                ActionType.OPEN_LAUNCHER_SETTINGS -> {
                                    onOpenSettings()
                                }
                                else -> {
                                    customWidgetRepository.dispatchAction(widgetId, action, targetVar, param, context)
                                }
                            }
                        },
                        onLongPressEmpty = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isContextMenuOpen = true
                        }
                    )
                }
            }
        }

        // =============================================================
        // SEARCH OVERLAY (Expands naturally from circular button)
        // =============================================================
        SearchOverlay(
            isVisible = isSearchOpen,
            searchQuery = searchQuery,
            onQueryChange = { searchQuery = it },
            searchState = searchState,
            onSubmitSearch = { query ->
                val isFriction = searchRepository.submitSearch(query)
                if (isFriction) {
                    isSearchOpen = false
                } else {
                    val first = searchState.standardResults.firstOrNull() ?: searchState.webOverview
                    if (first != null) {
                        val queryUsed = searchQuery
                        if (first is AppEntity && first.isFrictionApp) {
                            searchRepository.triggerFrictionBubble(first)
                            isSearchOpen = false
                        } else {
                            onLaunchEntity(first, queryUsed)
                            isSearchOpen = false
                            searchQuery = ""
                            searchRepository.clearSearch()
                        }
                    }
                }
            },
            onDismiss = {
                isSearchOpen = false
                searchQuery = ""
                searchRepository.clearSearch()
            },
            onLaunchEntity = { entity ->
                val queryUsed = searchQuery
                if (entity.id == "system:hidden_apps") {
                    isSearchOpen = false
                    searchQuery = ""
                    searchRepository.clearSearch()
                    if (!settings.hasSetupHiddenApps || settings.hiddenAppPackages.isEmpty()) {
                        onOpenHiddenAppsSetup()
                    } else {
                        isHiddenAppsBubblesOpen = true
                    }
                } else if (entity is AppGroupEntity) {
                    isSearchOpen = false
                    searchQuery = ""
                    searchRepository.clearSearch()
                    val group = settings.appGroups.firstOrNull { it.id == entity.groupId }
                    if (group != null) {
                        activeGroupBubbles = group
                    }
                } else if (entity is AppEntity && entity.isFrictionApp) {
                    searchRepository.triggerFrictionBubble(entity)
                    isSearchOpen = false
                } else {
                    onLaunchEntity(entity, queryUsed)
                    isSearchOpen = false
                    searchQuery = ""
                    searchRepository.clearSearch()
                }
            },
            getAppIcon = { pkg, userHandle -> appRepository.getAppIcon(pkg, settings.themePalette.isMonochrome, userHandle) }
        )

        // =============================================================
        // CONTEXTUAL LONG PRESS MENU
        // =============================================================
        HomeContextMenu(
            isVisible = isContextMenuOpen,
            onDismiss = { isContextMenuOpen = false },
            onAddWidget = onAddWidgetClick,
            onOpenSettings = onOpenSettings
        )

        // =============================================================
        // TWO-STAGE FLOATING APP BUBBLE & GRAVITY BOUNCE
        // =============================================================
        if (searchState.activeBubbleApp != null) {
            val bubbleApp = searchState.activeBubbleApp!!
            val icon = appRepository.getAppIcon(bubbleApp.packageName, settings.themePalette.isMonochrome, bubbleApp.userHandle)

            FloatingAppBubbleContainer(
                app = bubbleApp,
                iconDrawable = icon,
                bubbleConfig = bubbleConfig,
                modifier = Modifier.fillMaxSize(),
                onDismiss = {
                    searchQuery = ""
                    searchRepository.clearSearch()
                },
                onAppLaunch = { launchedApp ->
                    val queryUsed = searchQuery
                    onLaunchEntity(launchedApp, queryUsed)
                    searchQuery = ""
                    searchRepository.clearSearch()
                }
            )
        }

        // =============================================================
        // MULTI-BUBBLE HIDDEN APPS OVERLAY
        // =============================================================
        if (isHiddenAppsBubblesOpen) {
            val hiddenAppsList = remember(apps, settings.hiddenAppPackages) {
                with(appRepository) {
                    apps.filter { it.isContainedIn(settings.hiddenAppPackages) }
                        .map { it.toSearchEntity(isFriction = false) }
                }
            }

            if (hiddenAppsList.isNotEmpty()) {
                MultiAppBubbleContainer(
                    apps = hiddenAppsList,
                    bubbleConfig = bubbleConfig,
                    getAppIcon = { pkg, userHandle -> appRepository.getAppIcon(pkg, settings.themePalette.isMonochrome, userHandle) },
                    modifier = Modifier.fillMaxSize(),
                    onDismiss = { isHiddenAppsBubblesOpen = false },
                    onAppLaunch = { launchedApp ->
                        isHiddenAppsBubblesOpen = false
                        onLaunchEntity(launchedApp, "")
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    isHiddenAppsBubblesOpen = false
                    onOpenHiddenAppsSetup()
                }
            }
        }

        // =============================================================
        // MULTI-BUBBLE CUSTOM APP GROUP OVERLAY
        // =============================================================
        activeGroupBubbles?.let { group ->
            val groupAppsList = remember(apps, group.packageNames) {
                with(appRepository) {
                    apps.filter { it.isContainedIn(group.packageNames) }
                        .map { it.toSearchEntity(isFriction = false) }
                }
            }

            if (groupAppsList.isNotEmpty()) {
                MultiAppBubbleContainer(
                    apps = groupAppsList,
                    bubbleConfig = bubbleConfig,
                    getAppIcon = { pkg, userHandle -> appRepository.getAppIcon(pkg, settings.themePalette.isMonochrome, userHandle) },
                    modifier = Modifier.fillMaxSize(),
                    onDismiss = { activeGroupBubbles = null },
                    onAppLaunch = { launchedApp ->
                        activeGroupBubbles = null
                        onLaunchEntity(launchedApp, "")
                    }
                )
            } else {
                LaunchedEffect(group.id) {
                    activeGroupBubbles = null
                }
            }
        }

        // =============================================================
        // FIRST-RUN ONBOARDING SEARCH PREFERENCES & PERMISSIONS OVERLAY
        // =============================================================
        if (!settings.hasCompletedOnboarding) {
            OnboardingSearchSetupOverlay(
                settingsRepository = settingsRepository,
                onComplete = {
                    searchRepository.triggerReindex()
                }
            )
        }
    }
}

@Composable
private fun FloatingSearchButton(
    animate: Boolean,
    onClick: () -> Unit
) {
    val motionModifier = if (animate) {
        val infiniteTransition = rememberInfiniteTransition(label = "SearchIconMotion")
        val searchFloatX by infiniteTransition.animateFloat(
            initialValue = -45f, targetValue = 45f,
            animationSpec = infiniteRepeatable(tween(4400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "SearchFloatX"
        )
        val searchFloatY by infiniteTransition.animateFloat(
            initialValue = -26f, targetValue = 26f,
            animationSpec = infiniteRepeatable(tween(3300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "SearchFloatY"
        )
        val searchRotate by infiniteTransition.animateFloat(
            initialValue = -5f, targetValue = 5f,
            animationSpec = infiniteRepeatable(tween(5100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "SearchRotate"
        )
        val searchScale by infiniteTransition.animateFloat(
            initialValue = 0.96f, targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(3900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "SearchScale"
        )
        Modifier.graphicsLayer {
            translationX = searchFloatX
            translationY = searchFloatY
            rotationZ = searchRotate
            scaleX = searchScale
            scaleY = searchScale
        }
    } else {
        Modifier
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = motionModifier
            .size(68.dp)
            .shadow(16.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), CircleShape)
            .clickable(
                onClickLabel = "Open search",
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = "Search apps and files",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(30.dp)
        )
    }
}

private fun expandNotificationShade(context: Context) {
    try {
        val statusBarService = context.getSystemService("statusbar") ?: return
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
        expandMethod.invoke(statusBarService)
    } catch (e: Exception) {
        Log.w("HomeScreen", "Failed to expand notification panel via StatusBarManager", e)
    }
}
