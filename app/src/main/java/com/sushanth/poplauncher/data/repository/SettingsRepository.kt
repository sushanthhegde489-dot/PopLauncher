package com.sushanth.poplauncher.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sushanth.poplauncher.data.model.AppGroup
import com.sushanth.poplauncher.data.model.BubbleBehavior
import com.sushanth.poplauncher.data.model.BubbleConfig
import com.sushanth.poplauncher.data.model.BubbleSpeed
import com.sushanth.poplauncher.data.model.GestureActionType
import com.sushanth.poplauncher.data.model.GestureConfig
import com.sushanth.poplauncher.data.model.GestureType
import com.sushanth.poplauncher.data.model.LauncherSettings
import com.sushanth.poplauncher.data.model.PopDifficulty
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.ui.theme.ThemePalette
import com.sushanth.poplauncher.ui.theme.parseHexColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private fun loadSettings(): LauncherSettings {
        val frictionPackages = prefs.getStringSet(KEY_FRICTION_PACKAGES, null)?.toSet() ?: DEFAULT_DISTRACTING_PACKAGES
        val hiddenPackages = prefs.getStringSet(KEY_HIDDEN_PACKAGES, null)?.toSet() ?: emptySet()
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
                SearchType.MEDIA
            )

        val caseSensitive = prefs.getBoolean(KEY_CASE_SENSITIVE, false)
        val customColorHex = prefs.getString(KEY_CUSTOM_COLOR_HEX, "#8F425F") ?: "#8F425F"
        val paletteId = prefs.getString(KEY_THEME_PALETTE, ThemePalette.DEFAULT_MAROON.id)
        val previousPaletteId = prefs.getString(KEY_PREVIOUS_THEME_PALETTE, ThemePalette.DEFAULT_MAROON.id)
            ?: ThemePalette.DEFAULT_MAROON.id
        val hasCompletedOnboarding = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        val geminiApiKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        val showWallpaper = prefs.getBoolean(KEY_SHOW_WALLPAPER, false)
        val wallpaperDim = prefs.getFloat(KEY_WALLPAPER_DIM, 0.2f)
        val showClock = prefs.getBoolean(KEY_SHOW_CLOCK, true)
        val clock24Hour = prefs.getBoolean(KEY_CLOCK_24_HOUR, true)
        val showDate = prefs.getBoolean(KEY_SHOW_DATE, true)
        val searchAnimationEnabled = prefs.getBoolean(KEY_SEARCH_ANIMATION, true)

        val gestureDoubleTap = GestureConfig.fromSerialized(
            prefs.getString(KEY_GESTURE_DOUBLE_TAP, null)
        )
        val gestureTripleTap = GestureConfig.fromSerialized(
            prefs.getString(KEY_GESTURE_TRIPLE_TAP, null)
        )
        val gestureSwipeDown = prefs.getString(KEY_GESTURE_SWIPE_DOWN, null)?.let {
            GestureConfig.fromSerialized(it)
        } ?: GestureConfig(
            actionType = GestureActionType.OPEN_SEARCH
        )
        val gestureSwipeUp = GestureConfig.fromSerialized(
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
            previousThemePaletteId = previousPaletteId,
            customColorHex = customColorHex,
            hasCompletedOnboarding = hasCompletedOnboarding,
            showWallpaper = showWallpaper,
            wallpaperDim = wallpaperDim,
            showClock = showClock,
            clock24Hour = clock24Hour,
            showDate = showDate,
            searchAnimationEnabled = searchAnimationEnabled,
            geminiApiKey = geminiApiKey,
            gestureDoubleTap = gestureDoubleTap,
            gestureTripleTap = gestureTripleTap,
            gestureSwipeDown = gestureSwipeDown,
            gestureSwipeUp = gestureSwipeUp
        )
    }

    fun setGeminiApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit { putString(KEY_GEMINI_API_KEY, trimmed) }
        _settings.value = _settings.value.copy(geminiApiKey = trimmed)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(fallback)

    fun setFrictionApps(packages: Set<String>) {
        prefs.edit { putStringSet(KEY_FRICTION_PACKAGES, HashSet(packages)) }
        _settings.value = _settings.value.copy(frictionAppPackages = packages)
    }

    fun setConversationApps(packages: Set<String>?) {
        prefs.edit {
            if (packages == null) {
                remove(KEY_CONVERSATION_PACKAGES)
            } else {
                putStringSet(KEY_CONVERSATION_PACKAGES, HashSet(packages))
            }
        }
        _settings.value = _settings.value.copy(conversationAppPackages = packages)
    }

    fun toggleConversationApp(packageName: String, allAvailablePackages: Set<String>) {
        val current = _settings.value.conversationAppPackages?.toMutableSet()
            ?: allAvailablePackages.toMutableSet()
        if (!current.remove(packageName)) {
            current.add(packageName)
        }
        setConversationApps(current)
    }

    fun setHiddenApps(packages: Set<String>) {
        prefs.edit { putStringSet(KEY_HIDDEN_PACKAGES, HashSet(packages)) }
        _settings.value = _settings.value.copy(hiddenAppPackages = packages)
    }

    fun setHasSetupHiddenApps(completed: Boolean) {
        prefs.edit { putBoolean(KEY_HAS_SETUP_HIDDEN_APPS, completed) }
        _settings.value = _settings.value.copy(hasSetupHiddenApps = completed)
    }

    fun cleanupUninstalled(validPackages: Set<String>) {
        if (validPackages.isEmpty()) return
        // Preserve #private# entries even when the Private Space profile is locked in quiet mode
        val isValid: (String) -> Boolean = { id ->
            id.contains("#private#") || validPackages.contains(id.substringBefore("#"))
        }
        val current = _settings.value
        val newFriction = current.frictionAppPackages.filterTo(LinkedHashSet(), isValid)
        if (newFriction.size != current.frictionAppPackages.size) {
            setFrictionApps(newFriction)
        }
        val newHidden = current.hiddenAppPackages.filterTo(LinkedHashSet(), isValid)
        if (newHidden.size != current.hiddenAppPackages.size) {
            setHiddenApps(newHidden)
        }
        current.conversationAppPackages?.let { convPackages ->
            val newConv = convPackages.filterTo(LinkedHashSet(), isValid)
            if (newConv.size != convPackages.size) {
                setConversationApps(newConv)
            }
        }
        val updatedGroups = current.appGroups.map { group ->
            val filteredPkgs = group.packageNames.filterTo(LinkedHashSet(), isValid)
            if (filteredPkgs.size != group.packageNames.size) group.copy(packageNames = filteredPkgs) else group
        }
        if (updatedGroups != current.appGroups) {
            setAppGroups(updatedGroups)
        }
    }

    fun setBubbleSpeed(speed: BubbleSpeed) {
        prefs.edit { putString(KEY_BUBBLE_SPEED, speed.name) }
        _settings.value = _settings.value.copy(bubbleSpeed = speed)
    }

    fun setBubbleBehavior(behavior: BubbleBehavior) {
        prefs.edit { putString(KEY_BUBBLE_BEHAVIOR, behavior.name) }
        _settings.value = _settings.value.copy(bubbleBehavior = behavior)
    }

    fun setPopDifficulty(difficulty: PopDifficulty) {
        prefs.edit { putString(KEY_POP_DIFFICULTY, difficulty.name) }
        _settings.value = _settings.value.copy(popDifficulty = difficulty)
    }

    fun toggleProvider(provider: SearchType, enabled: Boolean) {
        val current = _settings.value.enabledProviders.toMutableSet()
        if (enabled) current.add(provider) else current.remove(provider)
        prefs.edit { putStringSet(KEY_ENABLED_PROVIDERS, HashSet(current.map { it.name })) }
        _settings.value = _settings.value.copy(enabledProviders = current)
    }

    fun setEnabledProviders(providers: Set<SearchType>) {
        prefs.edit { putStringSet(KEY_ENABLED_PROVIDERS, HashSet(providers.map { it.name })) }
        _settings.value = _settings.value.copy(enabledProviders = providers)
    }

    fun setCaseSensitive(value: Boolean) {
        prefs.edit { putBoolean(KEY_CASE_SENSITIVE, value) }
        _settings.value = _settings.value.copy(caseSensitive = value)
    }

    fun setThemePalette(palette: ThemePalette) {
        val current = _settings.value
        val previousNonMonoId = if (!palette.isMonochrome) {
            palette.id
        } else if (!current.themePalette.isMonochrome) {
            current.themePalette.id
        } else {
            current.previousThemePaletteId
        }
        prefs.edit {
            putString(KEY_THEME_PALETTE, palette.id)
            putString(KEY_PREVIOUS_THEME_PALETTE, previousNonMonoId)
        }
        _settings.value = current.copy(
            themePalette = palette,
            previousThemePaletteId = previousNonMonoId
        )
    }

    fun setMonochromeMode(enabled: Boolean) {
        val current = _settings.value
        if (enabled) {
            setThemePalette(ThemePalette.MONOCHROME)
        } else {
            val restored = ThemePalette.fromId(current.previousThemePaletteId, current.customColorHex)
                .takeUnless { it.isMonochrome } ?: ThemePalette.DEFAULT_MAROON
            setThemePalette(restored)
        }
    }

    fun setCustomColor(hex: String) {
        val clean = hex.trim().uppercase()
        val color = parseHexColor(clean)
        prefs.edit { putString(KEY_CUSTOM_COLOR_HEX, clean) }
        val current = _settings.value
        _settings.value = current.copy(
            customColorHex = clean,
            themePalette = if (current.themePalette.id == "custom") ThemePalette.createCustom(color) else current.themePalette
        )
    }

    fun setCompletedOnboarding(completed: Boolean) {
        prefs.edit { putBoolean(KEY_HAS_COMPLETED_ONBOARDING, completed) }
        _settings.value = _settings.value.copy(hasCompletedOnboarding = completed)
    }

    fun setShowWallpaper(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_WALLPAPER, show) }
        _settings.value = _settings.value.copy(showWallpaper = show)
    }

    fun setWallpaperDim(dim: Float) {
        prefs.edit { putFloat(KEY_WALLPAPER_DIM, dim) }
        _settings.value = _settings.value.copy(wallpaperDim = dim)
    }

    fun setShowClock(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_CLOCK, show) }
        _settings.value = _settings.value.copy(showClock = show)
    }

    fun setClock24Hour(is24Hour: Boolean) {
        prefs.edit { putBoolean(KEY_CLOCK_24_HOUR, is24Hour) }
        _settings.value = _settings.value.copy(clock24Hour = is24Hour)
    }

    fun setShowDate(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_DATE, show) }
        _settings.value = _settings.value.copy(showDate = show)
    }

    fun setSearchAnimationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SEARCH_ANIMATION, enabled) }
        _settings.value = _settings.value.copy(searchAnimationEnabled = enabled)
    }

    fun setGestureConfig(
        gestureType: GestureType,
        config: GestureConfig
    ) {
        val serialized = config.toSerialized()
        val key = when (gestureType) {
            GestureType.DOUBLE_TAP -> KEY_GESTURE_DOUBLE_TAP
            GestureType.TRIPLE_TAP -> KEY_GESTURE_TRIPLE_TAP
            GestureType.SWIPE_DOWN -> KEY_GESTURE_SWIPE_DOWN
            GestureType.SWIPE_UP -> KEY_GESTURE_SWIPE_UP
        }
        prefs.edit { putString(key, serialized) }
        _settings.value = when (gestureType) {
            GestureType.DOUBLE_TAP -> _settings.value.copy(gestureDoubleTap = config)
            GestureType.TRIPLE_TAP -> _settings.value.copy(gestureTripleTap = config)
            GestureType.SWIPE_DOWN -> _settings.value.copy(gestureSwipeDown = config)
            GestureType.SWIPE_UP -> _settings.value.copy(gestureSwipeUp = config)
        }
    }

    fun setAppGroups(groups: List<AppGroup>) {
        val jsonArray = JSONArray()
        groups.forEach { jsonArray.put(it.toJson()) }
        prefs.edit { putString(KEY_APP_GROUPS, jsonArray.toString()) }
        _settings.value = _settings.value.copy(appGroups = groups)
    }

    fun saveAppGroup(group: AppGroup) {
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

    private fun parseAppGroups(raw: String?): List<AppGroup> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<AppGroup>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(AppGroup.fromJson(obj))
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
        private const val KEY_PREVIOUS_THEME_PALETTE = "previous_theme_palette"
        private const val KEY_CUSTOM_COLOR_HEX = "custom_color_hex"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_SHOW_WALLPAPER = "show_wallpaper"
        private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        private const val KEY_SHOW_CLOCK = "show_clock"
        private const val KEY_CLOCK_24_HOUR = "clock_24_hour"
        private const val KEY_SHOW_DATE = "show_date"
        private const val KEY_SEARCH_ANIMATION = "search_animation"
        private const val KEY_GESTURE_DOUBLE_TAP = "gesture_double_tap"
        private const val KEY_GESTURE_TRIPLE_TAP = "gesture_triple_tap"
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
