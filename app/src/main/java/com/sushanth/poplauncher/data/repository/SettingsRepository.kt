package com.sushanth.poplauncher.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sushanth.poplauncher.data.model.BubbleBehavior
import com.sushanth.poplauncher.data.model.BubbleConfig
import com.sushanth.poplauncher.data.model.LauncherSettings
import com.sushanth.poplauncher.data.model.PopDifficulty
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.model.BubbleSpeed
import com.sushanth.poplauncher.ui.theme.ThemePalette
import com.sushanth.poplauncher.ui.theme.parseHexColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private fun loadSettings(): LauncherSettings {
        val frictionPackages = prefs.getStringSet(KEY_FRICTION_PACKAGES, null) ?: DEFAULT_DISTRACTING_PACKAGES
        val hiddenPackages = prefs.getStringSet(KEY_HIDDEN_PACKAGES, null) ?: emptySet()
        val hasSetupHidden = prefs.getBoolean(KEY_HAS_SETUP_HIDDEN_APPS, false)
        val appGroups = parseAppGroups(prefs.getString(KEY_APP_GROUPS, null))
        val conversationPackages = prefs.getStringSet(KEY_CONVERSATION_PACKAGES, null)?.toSet()
        val speed = enumOrDefault(prefs.getString(KEY_BUBBLE_SPEED, null), BubbleSpeed.NORMAL)
        val behavior = enumOrDefault(prefs.getString(KEY_BUBBLE_BEHAVIOR, null), BubbleBehavior.DRIFT)
        val difficulty = enumOrDefault(prefs.getString(KEY_POP_DIFFICULTY, null), PopDifficulty.NORMAL)

        val enabledProviders = prefs.getStringSet(KEY_ENABLED_PROVIDERS, null)
            ?.mapNotNull { value -> runCatching { SearchType.valueOf(value) }.getOrNull() }
            ?.toSet()
            ?: setOf(
                SearchType.APP,
                SearchType.PERSON,
                SearchType.CONVERSATION,
                SearchType.FILE,
                SearchType.MEDIA,
                SearchType.GOOGLE_OVERVIEW
            )

        val caseSensitive = prefs.getBoolean(KEY_CASE_SENSITIVE, false)
        val customColorHex = prefs.getString(KEY_CUSTOM_COLOR_HEX, "#8F425F") ?: "#8F425F"
        val paletteId = prefs.getString(KEY_THEME_PALETTE, ThemePalette.DEFAULT_MAROON.id)
        val hasCompletedOnboarding = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        val hasPromptedDefaultLauncher = prefs.getBoolean(KEY_HAS_PROMPTED_DEFAULT_LAUNCHER, false)
        val showWallpaper = prefs.getBoolean(KEY_SHOW_WALLPAPER, false)
        val wallpaperDim = prefs.getFloat(KEY_WALLPAPER_DIM, 0.2f)
        val showClock = prefs.getBoolean(KEY_SHOW_CLOCK, true)
        val clock24Hour = prefs.getBoolean(KEY_CLOCK_24_HOUR, true)
        val showDate = prefs.getBoolean(KEY_SHOW_DATE, true)
        val searchAnimationEnabled = prefs.getBoolean(KEY_SEARCH_ANIMATION, true)

        val gestureDoubleTap = com.sushanth.poplauncher.data.model.GestureConfig.fromSerialized(
            prefs.getString(KEY_GESTURE_DOUBLE_TAP, null)
        )
        val gestureTripleTap = com.sushanth.poplauncher.data.model.GestureConfig.fromSerialized(
            prefs.getString(KEY_GESTURE_TRIPLE_TAP, null)
        )
        val gestureDoubleTapSwipeUp = com.sushanth.poplauncher.data.model.GestureConfig.fromSerialized(
            prefs.getString(KEY_GESTURE_DOUBLE_TAP_SWIPE_UP, null)
        )
        val gestureDoubleTapSwipeDown = com.sushanth.poplauncher.data.model.GestureConfig.fromSerialized(
            prefs.getString(KEY_GESTURE_DOUBLE_TAP_SWIPE_DOWN, null)
        )
        val gestureSwipeDown = prefs.getString(KEY_GESTURE_SWIPE_DOWN, null)?.let {
            com.sushanth.poplauncher.data.model.GestureConfig.fromSerialized(it)
        } ?: com.sushanth.poplauncher.data.model.GestureConfig(
            actionType = com.sushanth.poplauncher.data.model.GestureActionType.OPEN_SEARCH
        )
        val gestureSwipeUp = com.sushanth.poplauncher.data.model.GestureConfig.fromSerialized(
            prefs.getString(KEY_GESTURE_SWIPE_UP, null)
        )

        return LauncherSettings(
            frictionAppPackages = frictionPackages,
            hiddenAppPackages = hiddenPackages,
            appGroups = appGroups,
            hasSetupHiddenApps = hasSetupHidden,
            conversationAppPackages = conversationPackages,
            bubbleSpeed = speed,
            bubbleBehavior = behavior,
            popDifficulty = difficulty,
            enabledProviders = enabledProviders,
            caseSensitive = caseSensitive,
            themePalette = ThemePalette.fromId(paletteId, customColorHex),
            customColorHex = customColorHex,
            hasCompletedOnboarding = hasCompletedOnboarding,
            hasPromptedDefaultLauncher = hasPromptedDefaultLauncher,
            showWallpaper = showWallpaper,
            wallpaperDim = wallpaperDim,
            showClock = showClock,
            clock24Hour = clock24Hour,
            showDate = showDate,
            searchAnimationEnabled = searchAnimationEnabled,
            gestureDoubleTap = gestureDoubleTap,
            gestureTripleTap = gestureTripleTap,
            gestureDoubleTapSwipeUp = gestureDoubleTapSwipeUp,
            gestureDoubleTapSwipeDown = gestureDoubleTapSwipeDown,
            gestureSwipeDown = gestureSwipeDown,
            gestureSwipeUp = gestureSwipeUp
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(fallback)

    fun setFrictionApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_FRICTION_PACKAGES, HashSet(packages)).apply()
        _settings.value = _settings.value.copy(frictionAppPackages = packages)
    }

    fun setConversationApps(packages: Set<String>?) {
        if (packages == null) {
            prefs.edit().remove(KEY_CONVERSATION_PACKAGES).apply()
        } else {
            prefs.edit().putStringSet(KEY_CONVERSATION_PACKAGES, HashSet(packages)).apply()
        }
        _settings.value = _settings.value.copy(conversationAppPackages = packages)
    }

    fun toggleConversationApp(packageName: String, allAvailablePackages: Set<String>) {
        val current = _settings.value.conversationAppPackages?.toMutableSet()
            ?: allAvailablePackages.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setConversationApps(current)
    }

    fun toggleFrictionApp(packageName: String) {
        val current = _settings.value.frictionAppPackages.toMutableSet()
        if (!current.add(packageName)) current.remove(packageName)
        setFrictionApps(current)
    }

    fun setHiddenApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_PACKAGES, HashSet(packages)).apply()
        _settings.value = _settings.value.copy(hiddenAppPackages = packages)
    }

    fun setHasSetupHiddenApps(completed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SETUP_HIDDEN_APPS, completed).apply()
        _settings.value = _settings.value.copy(hasSetupHiddenApps = completed)
    }

    fun toggleHiddenApp(packageName: String) {
        val current = _settings.value.hiddenAppPackages.toMutableSet()
        if (!current.add(packageName)) current.remove(packageName)
        setHiddenApps(current)
    }

    fun cleanupUninstalled(validPackages: Set<String>) {
        if (validPackages.isEmpty()) return
        setFrictionApps(_settings.value.frictionAppPackages.filter(validPackages::contains).toSet())
        setHiddenApps(_settings.value.hiddenAppPackages.filter(validPackages::contains).toSet())
        _settings.value.conversationAppPackages?.let { convPackages ->
            setConversationApps(convPackages.filter(validPackages::contains).toSet())
        }
    }

    fun setBubbleSpeed(speed: BubbleSpeed) {
        prefs.edit().putString(KEY_BUBBLE_SPEED, speed.name).apply()
        _settings.value = _settings.value.copy(bubbleSpeed = speed)
    }

    fun setBubbleBehavior(behavior: BubbleBehavior) {
        prefs.edit().putString(KEY_BUBBLE_BEHAVIOR, behavior.name).apply()
        _settings.value = _settings.value.copy(bubbleBehavior = behavior)
    }

    fun setPopDifficulty(difficulty: PopDifficulty) {
        prefs.edit().putString(KEY_POP_DIFFICULTY, difficulty.name).apply()
        _settings.value = _settings.value.copy(popDifficulty = difficulty)
    }

    fun toggleProvider(provider: SearchType, enabled: Boolean) {
        val current = _settings.value.enabledProviders.toMutableSet()
        if (enabled) current.add(provider) else current.remove(provider)
        prefs.edit().putStringSet(KEY_ENABLED_PROVIDERS, HashSet(current.map { it.name })).apply()
        _settings.value = _settings.value.copy(enabledProviders = current)
    }

    fun setEnabledProviders(providers: Set<SearchType>) {
        prefs.edit().putStringSet(KEY_ENABLED_PROVIDERS, HashSet(providers.map { it.name })).apply()
        _settings.value = _settings.value.copy(enabledProviders = providers)
    }

    fun setCaseSensitive(value: Boolean) {
        prefs.edit().putBoolean(KEY_CASE_SENSITIVE, value).apply()
        _settings.value = _settings.value.copy(caseSensitive = value)
    }

    fun setThemePalette(palette: ThemePalette) {
        prefs.edit().putString(KEY_THEME_PALETTE, palette.id).apply()
        _settings.value = _settings.value.copy(themePalette = palette)
    }

    fun setCustomColor(hex: String) {
        val clean = hex.trim().uppercase()
        val color = parseHexColor(clean)
        prefs.edit().putString(KEY_CUSTOM_COLOR_HEX, clean).apply()
        val current = _settings.value
        _settings.value = current.copy(
            customColorHex = clean,
            themePalette = if (current.themePalette.id == "custom") ThemePalette.createCustom(color) else current.themePalette
        )
    }

    fun setCompletedOnboarding(completed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, completed).apply()
        _settings.value = _settings.value.copy(hasCompletedOnboarding = completed)
    }

    fun setPromptedDefaultLauncher(prompted: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_PROMPTED_DEFAULT_LAUNCHER, prompted).apply()
        _settings.value = _settings.value.copy(hasPromptedDefaultLauncher = prompted)
    }

    fun setShowWallpaper(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WALLPAPER, show).apply()
        _settings.value = _settings.value.copy(showWallpaper = show)
    }

    fun setWallpaperDim(dim: Float) {
        prefs.edit().putFloat(KEY_WALLPAPER_DIM, dim).apply()
        _settings.value = _settings.value.copy(wallpaperDim = dim)
    }

    fun setShowClock(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CLOCK, show).apply()
        _settings.value = _settings.value.copy(showClock = show)
    }

    fun setClock24Hour(is24Hour: Boolean) {
        prefs.edit().putBoolean(KEY_CLOCK_24_HOUR, is24Hour).apply()
        _settings.value = _settings.value.copy(clock24Hour = is24Hour)
    }

    fun setShowDate(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DATE, show).apply()
        _settings.value = _settings.value.copy(showDate = show)
    }

    fun setSearchAnimationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEARCH_ANIMATION, enabled).apply()
        _settings.value = _settings.value.copy(searchAnimationEnabled = enabled)
    }

    fun setGestureConfig(
        gestureType: com.sushanth.poplauncher.data.model.GestureType,
        config: com.sushanth.poplauncher.data.model.GestureConfig
    ) {
        val serialized = config.toSerialized()
        val key = when (gestureType) {
            com.sushanth.poplauncher.data.model.GestureType.DOUBLE_TAP -> KEY_GESTURE_DOUBLE_TAP
            com.sushanth.poplauncher.data.model.GestureType.TRIPLE_TAP -> KEY_GESTURE_TRIPLE_TAP
            com.sushanth.poplauncher.data.model.GestureType.DOUBLE_TAP_SWIPE_UP -> KEY_GESTURE_DOUBLE_TAP_SWIPE_UP
            com.sushanth.poplauncher.data.model.GestureType.DOUBLE_TAP_SWIPE_DOWN -> KEY_GESTURE_DOUBLE_TAP_SWIPE_DOWN
            com.sushanth.poplauncher.data.model.GestureType.SWIPE_DOWN -> KEY_GESTURE_SWIPE_DOWN
            com.sushanth.poplauncher.data.model.GestureType.SWIPE_UP -> KEY_GESTURE_SWIPE_UP
        }
        prefs.edit().putString(key, serialized).apply()
        _settings.value = when (gestureType) {
            com.sushanth.poplauncher.data.model.GestureType.DOUBLE_TAP -> _settings.value.copy(gestureDoubleTap = config)
            com.sushanth.poplauncher.data.model.GestureType.TRIPLE_TAP -> _settings.value.copy(gestureTripleTap = config)
            com.sushanth.poplauncher.data.model.GestureType.DOUBLE_TAP_SWIPE_UP -> _settings.value.copy(gestureDoubleTapSwipeUp = config)
            com.sushanth.poplauncher.data.model.GestureType.DOUBLE_TAP_SWIPE_DOWN -> _settings.value.copy(gestureDoubleTapSwipeDown = config)
            com.sushanth.poplauncher.data.model.GestureType.SWIPE_DOWN -> _settings.value.copy(gestureSwipeDown = config)
            com.sushanth.poplauncher.data.model.GestureType.SWIPE_UP -> _settings.value.copy(gestureSwipeUp = config)
        }
    }


    fun setAppGroups(groups: List<com.sushanth.poplauncher.data.model.AppGroup>) {
        val jsonArray = org.json.JSONArray()
        groups.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_APP_GROUPS, jsonArray.toString()).apply()
        _settings.value = _settings.value.copy(appGroups = groups)
    }

    fun saveAppGroup(group: com.sushanth.poplauncher.data.model.AppGroup) {
        val current = _settings.value.appGroups.toMutableList()
        val index = current.indexOfFirst { it.id == group.id }
        if (index >= 0) {
            current[index] = group
        } else {
            current.add(group)
        }
        setAppGroups(current)
    }

    fun deleteAppGroup(groupId: String) {
        val current = _settings.value.appGroups.filter { it.id != groupId }
        setAppGroups(current)
    }

    private fun parseAppGroups(raw: String?): List<com.sushanth.poplauncher.data.model.AppGroup> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<com.sushanth.poplauncher.data.model.AppGroup>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(com.sushanth.poplauncher.data.model.AppGroup.fromJson(obj))
            }
            list
        }.getOrDefault(emptyList())
    }

    fun getBubbleConfig(): BubbleConfig {
        val s = _settings.value
        return BubbleConfig(speed = s.bubbleSpeed, behavior = s.bubbleBehavior, popDifficulty = s.popDifficulty)
    }

    companion object {
        private const val PREFS_NAME = "pop_launcher_prefs"
        private const val KEY_FRICTION_PACKAGES = "friction_packages"
        private const val KEY_HIDDEN_PACKAGES = "hidden_packages"
        private const val KEY_APP_GROUPS = "app_groups"
        private const val KEY_HAS_SETUP_HIDDEN_APPS = "has_setup_hidden_apps"
        private const val KEY_CONVERSATION_PACKAGES = "conversation_packages"
        private const val KEY_BUBBLE_SPEED = "bubble_speed"
        private const val KEY_BUBBLE_BEHAVIOR = "bubble_behavior"
        private const val KEY_POP_DIFFICULTY = "pop_difficulty"
        private const val KEY_ENABLED_PROVIDERS = "enabled_providers"
        private const val KEY_CASE_SENSITIVE = "case_sensitive"
        private const val KEY_THEME_PALETTE = "theme_palette"
        private const val KEY_CUSTOM_COLOR_HEX = "custom_color_hex"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_HAS_PROMPTED_DEFAULT_LAUNCHER = "has_prompted_default_launcher"
        private const val KEY_SHOW_WALLPAPER = "show_wallpaper"
        private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        private const val KEY_SHOW_CLOCK = "show_clock"
        private const val KEY_CLOCK_24_HOUR = "clock_24_hour"
        private const val KEY_SHOW_DATE = "show_date"
        private const val KEY_SEARCH_ANIMATION = "search_animation"
        private const val KEY_GESTURE_DOUBLE_TAP = "gesture_double_tap"
        private const val KEY_GESTURE_TRIPLE_TAP = "gesture_triple_tap"
        private const val KEY_GESTURE_DOUBLE_TAP_SWIPE_UP = "gesture_double_tap_swipe_up"
        private const val KEY_GESTURE_DOUBLE_TAP_SWIPE_DOWN = "gesture_double_tap_swipe_down"
        private const val KEY_GESTURE_SWIPE_DOWN = "gesture_swipe_down"
        private const val KEY_GESTURE_SWIPE_UP = "gesture_swipe_up"

        val DEFAULT_DISTRACTING_PACKAGES = setOf(
            "com.instagram.android", "com.google.android.youtube", "com.twitter.android",
            "com.zhiliaoapp.musically", "com.reddit.frontpage", "com.facebook.katana",
            "com.snapchat.android", "com.netflix.mediaclient", "tv.twitch.android.app",
            "com.pinterest", "com.whatsapp"
        )
    }
}
