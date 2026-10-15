package com.sushanth.poplauncher.data.model

import com.sushanth.poplauncher.ui.theme.ThemePalette

enum class BubbleSpeed(val label: String, val speedMultiplier: Float) {
    SLOW("Calm", 0.53f),
    NORMAL("Balanced", 1.0f),
    FAST("Snappy", 1.65f)
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
    val idleDelayMs: Long = 800L
)

enum class AnimationLevel(val label: String, val scale: Float) {
    OFF("Off (Instant)", 0.0f),
    REDUCED("Reduced (0.5x)", 0.5f),
    STANDARD("Standard (1.0x)", 1.0f),
    EXPRESSIVE("Expressive (1.5x)", 1.5f)
}

enum class AiLaunchMode(val label: String) {
    VOICE_DICTATION("Voice & Dictation"),
    TEXT_CHAT("Text Chat")
}

data class AiOverviewHistoryItem(
    val query: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
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
        SearchType.CONVERSATION,
        SearchType.FILE,
        SearchType.MEDIA
    ),
    val caseSensitive: Boolean = false,
    val themePalette: ThemePalette = ThemePalette.DEFAULT_MAROON,
    val previousThemePaletteId: String = ThemePalette.DEFAULT_MAROON.id,
    val customColorHex: String = "#8F425F",
    val hasCompletedOnboarding: Boolean = false,
    val showWallpaper: Boolean = false,
    val wallpaperDim: Float = 0.2f,
    val showClock: Boolean = true,
    val clock24Hour: Boolean = true,
    val showDate: Boolean = true,
    val searchAnimationEnabled: Boolean = true,
    val animationLevel: AnimationLevel = AnimationLevel.STANDARD,
    val hideStatusBarOnHomeScreen: Boolean = false,
    val defaultAiAppPackage: String = "",
    val defaultAiMode: AiLaunchMode = AiLaunchMode.VOICE_DICTATION,
    val aiOverviewHistory: List<AiOverviewHistoryItem> = emptyList(),
    val geminiApiKey: String = "",
    val gestureDoubleTap: GestureConfig = GestureConfig(actionType = GestureActionType.NONE),
    val gestureTripleTap: GestureConfig = GestureConfig(actionType = GestureActionType.NONE),
    val gestureSwipeDown: GestureConfig = GestureConfig(actionType = GestureActionType.OPEN_SEARCH),
    val gestureSwipeUp: GestureConfig = GestureConfig(actionType = GestureActionType.NONE)
) {
    fun getGesture(type: GestureType): GestureConfig = when (type) {
        GestureType.DOUBLE_TAP -> gestureDoubleTap
        GestureType.TRIPLE_TAP -> gestureTripleTap
        GestureType.SWIPE_DOWN -> gestureSwipeDown
        GestureType.SWIPE_UP -> gestureSwipeUp
    }
}
