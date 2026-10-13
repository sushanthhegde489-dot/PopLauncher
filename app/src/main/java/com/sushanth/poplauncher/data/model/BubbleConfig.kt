package com.sushanth.poplauncher.data.model

import com.sushanth.poplauncher.ui.theme.ThemePalette

enum class BubbleSpeed(val label: String, val speedMultiplier: Float) {
    SLOW("Calm", 0.65f),
    NORMAL("Balanced", 1.0f),
    FAST("Snappy", 1.45f)
}

enum class BubbleBehavior(val label: String) {
    DRIFT("Gentle Float"),
    RANDOMIZED("Dynamic Drift"),
    CONTINUOUS("Smooth Stream")
}

enum class PopDifficulty(val label: String, val holdThresholdMs: Long, val isMazeChallenge: Boolean = false) {
    EASY("Low Friction (1.0s)", 1000L, false),
    NORMAL("Medium Friction (1.8s)", 1800L, false),
    HARD("High Friction (2.5s)", 2500L, false),
    MAZE("Mindful Maze Navigation", 1000L, true)
}

data class BubbleConfig(
    val speed: BubbleSpeed = BubbleSpeed.NORMAL,
    val behavior: BubbleBehavior = BubbleBehavior.DRIFT,
    val popDifficulty: PopDifficulty = PopDifficulty.NORMAL,
    val edgeBounce: Float = 0.92f,
    val idleDelayMs: Long = 800L,
    val interactionRadius: Float = 140f
)

data class LauncherSettings(
    val frictionAppPackages: Set<String> = emptySet(),
    val hiddenAppPackages: Set<String> = emptySet(),
    val appGroups: List<AppGroup> = emptyList(),
    val hasSetupHiddenApps: Boolean = false,
    val conversationAppPackages: Set<String>? = null,
    val bubbleSpeed: BubbleSpeed = BubbleSpeed.NORMAL,
    val bubbleBehavior: BubbleBehavior = BubbleBehavior.DRIFT,
    val popDifficulty: PopDifficulty = PopDifficulty.NORMAL,
    val enabledProviders: Set<SearchType> = setOf(
        SearchType.APP,
        SearchType.PERSON,
        SearchType.CONVERSATION,
        SearchType.FILE,
        SearchType.MEDIA
    ),
    val caseSensitive: Boolean = false,
    val themePalette: ThemePalette = ThemePalette.DEFAULT_MAROON,
    val customColorHex: String = "#8F425F",
    val hasCompletedOnboarding: Boolean = false,
    val hasPromptedDefaultLauncher: Boolean = false,
    val showWallpaper: Boolean = false,
    val wallpaperDim: Float = 0.2f,
    val showClock: Boolean = true,
    val clock24Hour: Boolean = true,
    val showDate: Boolean = true,
    val searchAnimationEnabled: Boolean = true,
    val geminiApiKey: String = "",
    val gestureDoubleTap: GestureConfig = GestureConfig(actionType = GestureActionType.NONE),
    val gestureTripleTap: GestureConfig = GestureConfig(actionType = GestureActionType.NONE),
    val gestureSwipeDown: GestureConfig = GestureConfig(actionType = GestureActionType.OPEN_SEARCH),
    val gestureSwipeUp: GestureConfig = GestureConfig(actionType = GestureActionType.NONE)
) {
    val gestures: Map<GestureType, GestureConfig>
        get() = mapOf(
            GestureType.DOUBLE_TAP to gestureDoubleTap,
            GestureType.TRIPLE_TAP to gestureTripleTap,
            GestureType.SWIPE_DOWN to gestureSwipeDown,
            GestureType.SWIPE_UP to gestureSwipeUp
        )

    fun getGesture(type: GestureType): GestureConfig = when (type) {
        GestureType.DOUBLE_TAP -> gestureDoubleTap
        GestureType.TRIPLE_TAP -> gestureTripleTap
        GestureType.SWIPE_DOWN -> gestureSwipeDown
        GestureType.SWIPE_UP -> gestureSwipeUp
    }
}
