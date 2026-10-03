package com.sushanth.poplauncher.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import com.sushanth.poplauncher.data.model.GestureActionType
import com.sushanth.poplauncher.data.model.GestureConfig
import com.sushanth.poplauncher.LauncherScreen
import com.sushanth.poplauncher.bubble.FloatingAppBubbleContainer
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.repository.AppRepository
import com.sushanth.poplauncher.data.repository.SearchRepository
import com.sushanth.poplauncher.data.repository.SettingsRepository
import com.sushanth.poplauncher.search.SearchOverlay
import com.sushanth.poplauncher.settings.isDefaultLauncher
import com.sushanth.poplauncher.widgets.DedicatedWidgetPage
import com.sushanth.poplauncher.widgets.LauncherAppWidgetHost
import com.sushanth.poplauncher.widgets.WidgetManager
import com.sushanth.poplauncher.widgets.custom.ActionType
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    appRepository: AppRepository,
    searchRepository: SearchRepository,
    settingsRepository: SettingsRepository,
    widgetManager: WidgetManager,
    customWidgetRepository: com.sushanth.poplauncher.widgets.custom.CustomWidgetRepository,
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
    var activeGroupBubbles by remember { mutableStateOf<com.sushanth.poplauncher.data.model.AppGroup?>(null) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val density = LocalDensity.current

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

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now(ZoneId.systemDefault())
            currentTime = now.format(timeFormatter)
            currentDate = now.format(dateFormatter)
            // Align to the next minute boundary instead of ticking every second
            val millisUntilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(millisUntilNextMinute)
        }
    }

    LaunchedEffect(settings.clock24Hour) {
        currentTime = LocalDateTime.now(ZoneId.systemDefault()).format(timeFormatter)
    }

    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }
    var pendingTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun executeGestureAction(config: GestureConfig) {
        if (config.actionType == GestureActionType.NONE) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (config.actionType) {
            GestureActionType.NONE -> {}
            GestureActionType.OPEN_APP -> {
                val target = config.targetPackage
                if (!target.isNullOrBlank()) {
                    val matchingApp = apps.firstOrNull { it.matchesIdentifier(target) }
                    if (matchingApp != null && matchingApp.componentName != null && matchingApp.userHandle != null) {
                        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
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
                val opened = com.sushanth.poplauncher.util.PrivateSpaceHelper.openPrivateSpace(context)
                if (!opened) {
                    Toast.makeText(context, "Private Space is not available on this device", Toast.LENGTH_SHORT).show()
                }
            }
            GestureActionType.OPEN_HIDDEN_APPS -> {
                if (!settings.hasSetupHiddenApps || settings.hiddenAppPackages.isEmpty()) {
                    onOpenHiddenAppsSetup()
                } else {
                    isHiddenAppsBubblesOpen = true
                }
            }
            GestureActionType.LOCK_SCREEN -> {
                try {
                    val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
                    dpm?.lockNow()
                } catch (_: Exception) {}
            }
        }
    }

    // Keep the original organic floating motion for the main search control.
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

    val bubbleConfig = remember(settings) {
        settingsRepository.getBubbleConfig()
    }

    val isDefault = remember(settings) {
        isDefaultLauncher(context)
    }
    val showOnboarding = !settings.hasCompletedOnboarding
    val showDefaultLauncherBanner = settings.hasCompletedOnboarding && !isDefault && !settings.hasPromptedDefaultLauncher

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
                                val swipeThreshold = touchSlop * 2.8f
                                val pinchThreshold = touchSlop * 2.5f

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downTime = System.currentTimeMillis()
                                    val startPos = down.position
                                    var lastPointerPos = startPos

                                    val isDoubleTapCandidate = (downTime - lastTapTime < 340L)
                                    if (isDoubleTapCandidate) {
                                        tapCount++
                                        pendingTapJob?.cancel()
                                    } else {
                                        tapCount = 1
                                        pendingTapJob?.cancel()
                                    }
                                    val currentTapCount = tapCount

                                    var isHorizontalDrag = false
                                    var isVerticalDrag = false
                                    var gestureFired = false
                                    var multiTouchDetected = false

                                    var initialPinchDistance = 0f
                                    var initialMidpoint = Offset.Zero
                                    var twoFingerDownTime = 0L

                                    val longPressJob = scope.launch {
                                        delay(viewConfiguration.longPressTimeoutMillis)
                                        if (!isVerticalDrag && !isHorizontalDrag && !multiTouchDetected && !gestureFired) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isContextMenuOpen = true
                                            tapCount = 0
                                            lastTapTime = 0L
                                        }
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressedPointers = event.changes.filter { it.pressed }

                                        if (pressedPointers.isEmpty()) {
                                            longPressJob.cancel()
                                            break
                                        }

                                        // Multi-touch tracking (2 or more fingers)
                                        if (pressedPointers.size >= 2) {
                                            longPressJob.cancel()
                                            pendingTapJob?.cancel()
                                            tapCount = 0
                                            lastTapTime = 0L

                                            val p1 = pressedPointers[0]
                                            val p2 = pressedPointers[1]
                                            lastPointerPos = p1.position

                                            if (!multiTouchDetected) {
                                                multiTouchDetected = true
                                                twoFingerDownTime = System.currentTimeMillis()
                                                val dx = p2.position.x - p1.position.x
                                                val dy = p2.position.y - p1.position.y
                                                initialPinchDistance = kotlin.math.hypot(dx, dy)
                                                initialMidpoint = Offset(
                                                    (p1.position.x + p2.position.x) / 2f,
                                                    (p1.position.y + p2.position.y) / 2f
                                                )
                                            } else if (!gestureFired) {
                                                val dx = p2.position.x - p1.position.x
                                                val dy = p2.position.y - p1.position.y
                                                val currentDistance = kotlin.math.hypot(dx, dy)
                                                val currentMid = Offset(
                                                    (p1.position.x + p2.position.x) / 2f,
                                                    (p1.position.y + p2.position.y) / 2f
                                                )

                                                val distDelta = currentDistance - initialPinchDistance
                                                val midDeltaY = currentMid.y - initialMidpoint.y
                                                val midDeltaX = currentMid.x - initialMidpoint.x

                                                if (distDelta < -pinchThreshold && kotlin.math.abs(distDelta) > kotlin.math.abs(midDeltaY) * 1.15f) {
                                                    gestureFired = true
                                                    event.changes.forEach { it.consume() }
                                                    executeGestureAction(settings.gesturePinchIn)
                                                } else if (distDelta > pinchThreshold && distDelta > kotlin.math.abs(midDeltaY) * 1.15f) {
                                                    gestureFired = true
                                                    event.changes.forEach { it.consume() }
                                                    executeGestureAction(settings.gesturePinchOut)
                                                } else if (kotlin.math.abs(midDeltaY) > swipeThreshold &&
                                                    kotlin.math.abs(midDeltaY) > kotlin.math.abs(midDeltaX) * 1.05f
                                                ) {
                                                    gestureFired = true
                                                    event.changes.forEach { it.consume() }
                                                    if (midDeltaY > 0) {
                                                        executeGestureAction(settings.gestureTwoFingerSwipeDown)
                                                    } else {
                                                        executeGestureAction(settings.gestureTwoFingerSwipeUp)
                                                    }
                                                }
                                            }

                                            if (gestureFired) {
                                                event.changes.forEach { it.consume() }
                                            }
                                            continue
                                        }

                                        // Single finger tracking
                                        val current = pressedPointers.first()
                                        lastPointerPos = current.position
                                        val totalDx = current.position.x - startPos.x
                                        val totalDy = current.position.y - startPos.y

                                        if (multiTouchDetected) {
                                            continue
                                        }

                                        if (!isHorizontalDrag && !isVerticalDrag) {
                                            val absX = kotlin.math.abs(totalDx)
                                            val absY = kotlin.math.abs(totalDy)

                                            if (absX > touchSlop && absX > absY * 1.25f) {
                                                // Horizontal movement: allow HorizontalPager to handle page scroll
                                                isHorizontalDrag = true
                                                longPressJob.cancel()
                                                pendingTapJob?.cancel()
                                                tapCount = 0
                                                lastTapTime = 0L
                                            } else if (absY > touchSlop && absY > absX * 1.05f) {
                                                // Vertical movement: lock as launcher gesture and consume
                                                isVerticalDrag = true
                                                longPressJob.cancel()
                                                current.consume()
                                            }
                                        } else if (isVerticalDrag) {
                                            current.consume()

                                            if (!gestureFired && kotlin.math.abs(totalDy) > swipeThreshold) {
                                                gestureFired = true
                                                tapCount = 0
                                                lastTapTime = 0L
                                                if (currentTapCount >= 2) {
                                                    if (totalDy > 0) {
                                                        executeGestureAction(settings.gestureDoubleTapSwipeDown)
                                                    } else {
                                                        executeGestureAction(settings.gestureDoubleTapSwipeUp)
                                                    }
                                                } else {
                                                    if (totalDy > 0) {
                                                        executeGestureAction(settings.gestureSwipeDown)
                                                    } else {
                                                        executeGestureAction(settings.gestureSwipeUp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    longPressJob.cancel()

                                    // 1. Multi-touch completion (Two-Finger Tap)
                                    if (multiTouchDetected) {
                                        if (!gestureFired) {
                                            val duration = System.currentTimeMillis() - twoFingerDownTime
                                            if (duration < 420L) {
                                                executeGestureAction(settings.gestureTwoFingerTap)
                                            }
                                        }
                                        tapCount = 0
                                        lastTapTime = 0L
                                        return@awaitEachGesture
                                    }

                                    // 2. Gesture already fired during continuous drag
                                    if (gestureFired) {
                                        tapCount = 0
                                        lastTapTime = 0L
                                        return@awaitEachGesture
                                    }

                                    // 3. User swiped horizontally
                                    if (isHorizontalDrag) {
                                        tapCount = 0
                                        lastTapTime = 0L
                                        return@awaitEachGesture
                                    }

                                    // 4. Quick vertical flick that ended before exceeding continuous threshold
                                    if (isVerticalDrag) {
                                        val finalDy = lastPointerPos.y - startPos.y
                                        if (kotlin.math.abs(finalDy) > touchSlop * 1.7f) {
                                            if (currentTapCount >= 2) {
                                                if (finalDy > 0) {
                                                    executeGestureAction(settings.gestureDoubleTapSwipeDown)
                                                } else {
                                                    executeGestureAction(settings.gestureDoubleTapSwipeUp)
                                                }
                                            } else {
                                                if (finalDy > 0) {
                                                    executeGestureAction(settings.gestureSwipeDown)
                                                } else {
                                                    executeGestureAction(settings.gestureSwipeUp)
                                                }
                                            }
                                        }
                                        tapCount = 0
                                        lastTapTime = 0L
                                        return@awaitEachGesture
                                    }

                                    // 5. Clean tap release without drag
                                    lastTapTime = System.currentTimeMillis()
                                    if (tapCount >= 3) {
                                        tapCount = 0
                                        lastTapTime = 0L
                                        pendingTapJob?.cancel()
                                        executeGestureAction(settings.gestureTripleTap)
                                    } else if (tapCount == 2) {
                                        val hasTripleTapAction = settings.gestureTripleTap.actionType != GestureActionType.NONE
                                        pendingTapJob = scope.launch {
                                            delay(if (hasTripleTapAction) 240L else 140L)
                                            tapCount = 0
                                            lastTapTime = 0L
                                            executeGestureAction(settings.gestureDoubleTap)
                                        }
                                    } else if (tapCount == 1) {
                                        val hasDoubleTapAction = settings.gestureDoubleTap.actionType != GestureActionType.NONE ||
                                                settings.gestureDoubleTapSwipeUp.actionType != GestureActionType.NONE ||
                                                settings.gestureDoubleTapSwipeDown.actionType != GestureActionType.NONE ||
                                                settings.gestureTripleTap.actionType != GestureActionType.NONE
                                        pendingTapJob = scope.launch {
                                            if (hasDoubleTapAction) {
                                                delay(250L)
                                            }
                                            tapCount = 0
                                            lastTapTime = 0L
                                            if (isContextMenuOpen) {
                                                isContextMenuOpen = false
                                            }
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
                                        .padding(16.dp)
                                        .clickable {
                                            runCatching {
                                                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                            settingsRepository.setPromptedDefaultLauncher(true)
                                        }
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
                            val searchAnimation = settings.searchAnimationEnabled
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .then(
                                        if (searchAnimation) {
                                            Modifier.graphicsLayer {
                                                translationX = searchFloatX
                                                translationY = searchFloatY
                                                rotationZ = searchRotate
                                                scaleX = searchScale
                                                scaleY = searchScale
                                            }
                                        } else Modifier
                                    )
                                    .size(68.dp)
                                    .shadow(16.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), CircleShape)
                                    .clickable {
                                        pendingTapJob?.cancel()
                                        tapCount = 0
                                        lastTapTime = 0L
                                        isSearchOpen = true
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

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
                        onLaunchEntity(first, queryUsed)
                        isSearchOpen = false
                        searchQuery = ""
                        searchRepository.clearSearch()
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
                } else if (entity is com.sushanth.poplauncher.data.model.AppGroupEntity) {
                    isSearchOpen = false
                    searchQuery = ""
                    searchRepository.clearSearch()
                    val group = settings.appGroups.firstOrNull { it.id == entity.groupId }
                    if (group != null) {
                        activeGroupBubbles = group
                    }
                } else {
                    onLaunchEntity(entity, queryUsed)
                    isSearchOpen = false
                    searchQuery = ""
                    searchRepository.clearSearch()
                }
            },
            getAppIcon = { pkg -> appRepository.getAppIcon(pkg, settings.themePalette.isMonochrome) }
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
            val icon = appRepository.getAppIcon(bubbleApp.packageName, settings.themePalette.isMonochrome)

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
                com.sushanth.poplauncher.bubble.MultiAppBubbleContainer(
                    apps = hiddenAppsList,
                    bubbleConfig = bubbleConfig,
                    getAppIcon = { pkg -> appRepository.getAppIcon(pkg, settings.themePalette.isMonochrome) },
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
                com.sushanth.poplauncher.bubble.MultiAppBubbleContainer(
                    apps = groupAppsList,
                    bubbleConfig = bubbleConfig,
                    getAppIcon = { pkg -> appRepository.getAppIcon(pkg, settings.themePalette.isMonochrome) },
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

private fun expandNotificationShade(context: android.content.Context) {
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
        expandMethod.invoke(statusBarService)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
