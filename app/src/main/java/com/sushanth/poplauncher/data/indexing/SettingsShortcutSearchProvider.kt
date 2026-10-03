package com.sushanth.poplauncher.data.indexing

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.sushanth.poplauncher.data.model.DeepSearchEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingShortcutItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val action: String,
    val keywords: List<String>
)

class SettingsShortcutSearchProvider : SearchProvider {

    override val type: SearchType = SearchType.APP
    override val displayName: String = "System Settings"
    @android.annotation.SuppressLint("InlinedApi")
    private val notificationAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS
    } else {
        "android.settings.NOTIFICATION_SETTINGS"
    }

    private val shortcuts = listOf(
        SettingShortcutItem("setting:wifi", "Wi-Fi & Networks", "Configure wireless connections", Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "network", "internet", "wireless", "wlan")),
        SettingShortcutItem("setting:bluetooth", "Bluetooth", "Pair headphones & devices", Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "bt", "pair", "devices")),
        SettingShortcutItem("setting:display", "Display & Brightness", "Dark mode, font size & wallpaper", Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "brightness", "screen", "dark mode", "font", "theme")),
        SettingShortcutItem("setting:battery", "Battery & Power", "Battery usage & power saver", Settings.ACTION_BATTERY_SAVER_SETTINGS, listOf("battery", "power", "charging", "battery saver", "energy")),
        SettingShortcutItem("setting:sound", "Sound & Volume", "Ringtone, media volume & vibration", Settings.ACTION_SOUND_SETTINGS, listOf("sound", "volume", "ringtone", "vibrate", "audio", "mute")),
        SettingShortcutItem("setting:storage", "Storage & Memory", "Device storage & free space", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("storage", "memory", "sd card", "space", "disk")),
        SettingShortcutItem("setting:apps", "Apps & Permissions", "Manage installed applications", Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "applications", "installed apps", "default apps", "permissions")),
        SettingShortcutItem("setting:notifications", "Notifications", "App notification preferences", notificationAction, listOf("notifications", "alerts", "notification history")),
        SettingShortcutItem("setting:location", "Location & GPS", "Location access & GPS settings", Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("location", "gps", "position", "map")),
        SettingShortcutItem("setting:hotspot", "Hotspot & Tethering", "Share mobile data via Wi-Fi", "android.settings.TETHER_SETTINGS", listOf("hotspot", "tethering", "share internet", "mobile hotspot")),
        SettingShortcutItem("setting:airplane", "Airplane Mode", "Toggle all wireless radios", Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("airplane", "flight mode", "offline")),
        SettingShortcutItem("setting:developer", "Developer Options", "USB debugging & advanced tools", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("developer", "usb debugging", "adb", "dev options")),
        SettingShortcutItem("setting:security", "Security & Lock Screen", "PIN, password & device security", Settings.ACTION_SECURITY_SETTINGS, listOf("security", "lock screen", "password", "pin", "pattern")),
        SettingShortcutItem("setting:privacy", "Privacy & Permissions", "App permissions & camera/mic access", Settings.ACTION_PRIVACY_SETTINGS, listOf("privacy", "permissions", "microphone", "camera")),
        SettingShortcutItem("system:private_space", "Private Space", "Android Private Space • Tap to unlock or open", "android.settings.PRIVATE_SPACE_SETTINGS", listOf("private space", "privatespace")),
        SettingShortcutItem("system:hidden_apps", "Hidden Apps", "Launcher Hidden Apps • Tap to view or configure", "poplauncher.action.OPEN_HIDDEN_APPS", listOf("hidden apps", "hiddenapps")),
        SettingShortcutItem("setting:date", "Date & Time", "Automatic time & timezone", Settings.ACTION_DATE_SETTINGS, listOf("date", "time", "clock", "timezone")),
        SettingShortcutItem("setting:language", "Language & Input", "System language & keyboard", Settings.ACTION_LOCALE_SETTINGS, listOf("language", "locale", "keyboard", "input")),
        SettingShortcutItem("setting:about", "About Phone", "Android version, model & build", Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("about", "about phone", "android version", "model", "build number")),
        SettingShortcutItem("setting:main", "Android System Settings", "All device settings", Settings.ACTION_SETTINGS, listOf("settings", "system settings", "configure"))
    )

    override suspend fun isAvailable(context: Context): Boolean = true

    override suspend fun index(context: Context): List<SearchEntity> =
        withContext(Dispatchers.IO) {
            shortcuts.map { createEntity(it) }
        }

    override suspend fun search(query: String, context: Context): List<SearchEntity> =
        withContext(Dispatchers.IO) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return@withContext emptyList()

            shortcuts.filter { item ->
                // Requirement 1 & 6: Hidden apps and private space must work ONLY when fully searched by their name
                if (item.id == "system:private_space") {
                    q == "private space" || q == "privatespace"
                } else if (item.id == "system:hidden_apps") {
                    q == "hidden apps" || q == "hiddenapps"
                } else {
                    val titleMatch = item.title.lowercase().contains(q)
                    val subtitleMatch = item.subtitle.lowercase().contains(q)
                    // Keyword match: tokenized word boundary matching
                    val keywordMatch = item.keywords.any { kw ->
                        val queryWords = q.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        val kwWords = kw.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        queryWords.all { qw -> kwWords.any { kw -> kw.startsWith(qw) } }
                    }
                    titleMatch || subtitleMatch || keywordMatch
                }
            }.map { createEntity(it) }
        }

    private fun createEntity(item: SettingShortcutItem): SearchEntity {
        val intent = Intent(item.action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return DeepSearchEntity(
            id = item.id,
            title = item.title,
            subtitle = item.subtitle,
            source = "System Settings",
            appPackage = "com.android.settings",
            launchIntent = intent,
            searchableTerms = item.keywords + listOf(item.title, item.subtitle)
        )
    }
}
