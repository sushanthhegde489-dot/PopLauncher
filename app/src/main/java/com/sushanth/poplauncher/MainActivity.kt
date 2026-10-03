package com.sushanth.poplauncher

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.sushanth.poplauncher.data.model.ConversationEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.AppRepository
import com.sushanth.poplauncher.data.repository.SearchRepository
import com.sushanth.poplauncher.data.repository.SettingsRepository
import com.sushanth.poplauncher.home.HomeScreen
import com.sushanth.poplauncher.settings.FrictionAppsScreen
import com.sushanth.poplauncher.settings.SettingsScreen
import com.sushanth.poplauncher.ui.theme.PopLauncherTheme
import com.sushanth.poplauncher.widgets.LauncherAppWidgetHost
import com.sushanth.poplauncher.widgets.VisualWidgetPickerScreen
import com.sushanth.poplauncher.widgets.WidgetManager
import kotlinx.coroutines.launch
import com.sushanth.poplauncher.settings.ConversationAppsScreen
import com.sushanth.poplauncher.widgets.custom.CustomWidgetCatalogRepository
import com.sushanth.poplauncher.widgets.custom.CustomWidgetRepository

enum class LauncherScreen {
    HOME,
    SETTINGS,
    FRICTION_APPS,
    HIDDEN_APPS_SETUP,
    CONVERSATION_APPS,
    APP_GROUPS,
    WIDGET_PICKER,
}

enum class FileAccessState {
    ALL_FILES,
    MEDIASTORE_ONLY,
    DENIED
}

enum class MediaAccessState {
    FULL,
    SELECTED_ONLY,
    DENIED
}

class MainActivity : ComponentActivity() {

    private lateinit var appRepository: AppRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var searchRepository: SearchRepository
    private lateinit var widgetManager: WidgetManager
    private lateinit var launcherAppWidgetHost: LauncherAppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var customWidgetRepository: CustomWidgetRepository
    private lateinit var customWidgetCatalogRepository: CustomWidgetCatalogRepository

    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null
    private var pendingPinItemRequest: LauncherApps.PinItemRequest? = null
    private val currentScreen = mutableStateOf(LauncherScreen.HOME)

    // Widget Bind Launcher (For permission dialog when bindAppWidgetIdIfAllowed returns false)
    private val widgetBindLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val widgetId = pendingWidgetId
            val info = pendingWidgetInfo
            val pinRequest = pendingPinItemRequest
            if (pinRequest != null) {
                try {
                    val accepted = pinRequest.accept(Bundle().apply {
                        putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    })
                    if (!accepted) {
                        launcherAppWidgetHost.deleteAppWidgetId(widgetId)
                        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                        pendingWidgetInfo = null
                        pendingPinItemRequest = null
                        Toast.makeText(this, "Widget pin request was rejected", Toast.LENGTH_SHORT).show()
                        return@registerForActivityResult
                    }
                } catch (e: Exception) {
                    launcherAppWidgetHost.deleteAppWidgetId(widgetId)
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                    pendingWidgetInfo = null
                    pendingPinItemRequest = null
                    Toast.makeText(this, "Could not accept widget pin request: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                pendingPinItemRequest = null
            }
            if (info != null) {
                proceedAfterWidgetBound(widgetId, info)
            } else {
                widgetManager.addPageWidget(widgetId)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                pendingWidgetInfo = null
            }
        } else if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            launcherAppWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            pendingWidgetInfo = null
            pendingPinItemRequest = null
        }
    }

    // Widget Configure Launcher
    private val widgetConfigureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetManager.addPageWidget(pendingWidgetId)
        } else if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            launcherAppWidgetHost.deleteAppWidgetId(pendingWidgetId)
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingWidgetInfo = null
    }

    // Permission launcher for local search (Contacts, Call Log, SMS, Files, Photos/Media, Notifications)
    private var pendingAllFilesAccessAfterRuntimePermissions = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        searchRepository.triggerReindex()
        if (pendingAllFilesAccessAfterRuntimePermissions &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            pendingAllFilesAccessAfterRuntimePermissions = false
            launchAllFilesSettings()
        } else {
            pendingAllFilesAccessAfterRuntimePermissions = false
        }
    }

    fun getFileAccessState(): FileAccessState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return FileAccessState.ALL_FILES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImg = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVid = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasVisual = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            return if (hasImg || hasVid || hasVisual) FileAccessState.MEDIASTORE_ONLY else FileAccessState.DENIED
        }
        val hasStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return if (hasStorage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) FileAccessState.MEDIASTORE_ONLY else FileAccessState.ALL_FILES
        } else {
            FileAccessState.DENIED
        }
    }

    fun getMediaAccessState(): MediaAccessState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return MediaAccessState.FULL
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImg = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVid = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasVisual = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            return when {
                hasImg || hasVid -> MediaAccessState.FULL
                hasVisual -> MediaAccessState.SELECTED_ONLY
                else -> MediaAccessState.DENIED
            }
        }
        val hasStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return if (hasStorage) MediaAccessState.FULL else MediaAccessState.DENIED
    }

    /**
     * Returns list of runtime permissions required for full Pop Launcher functionality that are currently missing.
     */
    fun getMissingRuntimePermissions(): List<String> {
        val neededPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.READ_CONTACTS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.READ_CALL_LOG)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        }

        return neededPermissions
    }

    /**
     * Checks if all required runtime permissions are granted.
     */
    fun hasAllRuntimePermissions(): Boolean = getMissingRuntimePermissions().isEmpty()

    /**
     * Launch system settings for All Files Access on Android 11+.
     */
    fun launchAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.w("PopLauncher", "Could not open app all files access permission settings, falling back to general settings", e)
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (e2: Exception) {
                    Log.e("PopLauncher", "Failed to launch all files settings", e2)
                    Toast.makeText(this, "Unable to open storage settings: ${e2.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Request permissions for Contacts, Call Log, SMS, Photos/Media, Storage, and Notifications.
     * Prevents launching runtime dialog and system settings simultaneously.
     */
    fun requestSearchPermissions() {
        val neededPermissions = getMissingRuntimePermissions()

        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            launchAllFilesSettings()
        }
    }

    fun requestContactSearchPermission() {
        requestPermissionForProvider(SearchType.PERSON)
    }

    fun hasContactSearchPermission(): Boolean =
        hasPermissionForProvider(SearchType.PERSON)

    /**
     * Requests ONLY the specific permissions required for the provided set of SearchTypes.
     */
    fun requestPermissionsForProviders(providers: Set<SearchType>) {
        val neededPermissions = mutableListOf<String>()
        var needsStorageSettings = false

        for (provider in providers) {
            val missing = getMissingPermissionsForProvider(provider)
            neededPermissions.addAll(missing)
            if (provider == SearchType.FILE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                needsStorageSettings = true
            }
        }

        val distinctNeeded = neededPermissions.distinct()
        if (distinctNeeded.isNotEmpty()) {
            pendingAllFilesAccessAfterRuntimePermissions = needsStorageSettings
            permissionLauncher.launch(distinctNeeded.toTypedArray())
        } else if (needsStorageSettings) {
            launchAllFilesSettings()
        }
    }

    /**
     * Returns the specific missing permissions for a given SearchType provider.
     */
    fun getMissingPermissionsForProvider(provider: SearchType): List<String> {
        val missing = mutableListOf<String>()
        when (provider) {
            SearchType.PERSON -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    missing.add(Manifest.permission.READ_CONTACTS)
                }
            }
            SearchType.CALL_LOG -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                    missing.add(Manifest.permission.READ_CALL_LOG)
                }
            }
            SearchType.FILE -> {
                // Files/Documents require broad storage access on Android 11+.
                // READ_MEDIA_* only covers photos/videos and cannot make MediaStore.Files
                // a general document index.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // No runtime permission can grant this; requestPermissionForProvider()
                    // / requestPermissionsForProviders() will open the All Files settings page.
                } else if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    missing.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            SearchType.MEDIA -> {
                if (getMediaAccessState() == MediaAccessState.DENIED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        missing.add(Manifest.permission.READ_MEDIA_IMAGES)
                        missing.add(Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        missing.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            }
            else -> {}
        }
        return missing
    }

    /**
     * Checks if permissions for a given SearchType provider are granted on the device.
     */
    fun hasPermissionForProvider(provider: SearchType): Boolean {
        return when (provider) {
            SearchType.FILE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                getFileAccessState() != FileAccessState.DENIED
            }
            SearchType.MEDIA -> getMediaAccessState() != MediaAccessState.DENIED
            else -> getMissingPermissionsForProvider(provider).isEmpty()
        }
    }

    /**
     * Requests ONLY the specific permissions required for the specified provider.
     */
    fun requestPermissionForProvider(provider: SearchType) {
        val missing = getMissingPermissionsForProvider(provider)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else if (provider == SearchType.FILE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            launchAllFilesSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appRepository = AppRepository(this)
        settingsRepository = SettingsRepository(this)
        searchRepository = SearchRepository(this, appRepository, settingsRepository)
        widgetManager = WidgetManager(this)
        launcherAppWidgetHost = LauncherAppWidgetHost(this)
        appWidgetManager = AppWidgetManager.getInstance(this)
        customWidgetRepository = CustomWidgetRepository(this)
        customWidgetCatalogRepository = CustomWidgetCatalogRepository(
            this,
            getString(R.string.custom_widgets_manifest_url)
        )

        lifecycleScope.launch {
            val apps = appRepository.refreshApps()
            if (apps.isNotEmpty()) {
                settingsRepository.cleanupUninstalled(apps.map { it.packageName }.toSet())
            }
        }

        handlePinItemRequest(intent)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        setContent {
            val settings by settingsRepository.settings.collectAsState()
            PopLauncherTheme(palette = settings.themePalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (settings.showWallpaper) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background
                ) {
                    LauncherApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePinItemRequest(intent)
        // Reset to Home screen when user presses physical or gesture Home button
        if (intent.action == Intent.ACTION_MAIN) {
            if (currentScreen.value != LauncherScreen.HOME) {
                currentScreen.value = LauncherScreen.HOME
            }
            searchRepository.clearSearch()
        }
    }

    private fun handlePinItemRequest(targetIntent: Intent?) {
        if (targetIntent == null) return
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return
        val pinItemRequest = try {
            launcherApps.getPinItemRequest(targetIntent)
        } catch (_: Exception) {
            null
        } ?: return

        if (pinItemRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET) {
            val providerInfo = pinItemRequest.getAppWidgetProviderInfo(this)
            if (providerInfo != null) {
                try {
                    val allocatedId = launcherAppWidgetHost.allocateAppWidgetId()
                    val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(allocatedId, providerInfo.provider)
                    if (canBind) {
                        val bundle = Bundle().apply {
                            putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
                        }
                        val accepted = pinItemRequest.accept(bundle)
                        if (!accepted) {
                            launcherAppWidgetHost.deleteAppWidgetId(allocatedId)
                            Toast.makeText(this, "Widget pin request was rejected", Toast.LENGTH_SHORT).show()
                            return
                        }
                        pendingWidgetId = allocatedId
                        pendingWidgetInfo = providerInfo
                        pendingPinItemRequest = null
                        proceedAfterWidgetBound(allocatedId, providerInfo)
                    } else {
                        pendingWidgetId = allocatedId
                        pendingWidgetInfo = providerInfo
                        pendingPinItemRequest = pinItemRequest
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                        }
                        widgetBindLauncher.launch(bindIntent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not pin widget: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        searchRepository.triggerReindexIfDue()
    }

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
            lifecycleScope.launch { appRepository.refreshApps() }
        }
        override fun onPackageAdded(packageName: String, user: android.os.UserHandle) {
            lifecycleScope.launch { appRepository.refreshApps() }
        }
        override fun onPackageChanged(packageName: String, user: android.os.UserHandle) {
            lifecycleScope.launch { appRepository.refreshApps() }
        }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) {
            lifecycleScope.launch { appRepository.refreshApps() }
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) {
            lifecycleScope.launch { appRepository.refreshApps() }
        }
        override fun onPackagesSuspended(packageNames: Array<out String>, user: android.os.UserHandle) {
            lifecycleScope.launch { appRepository.refreshApps() }
        }
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: android.os.UserHandle) {
            lifecycleScope.launch { appRepository.refreshApps() }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            launcherAppWidgetHost.startListening()
        } catch (_: Exception) {}
        try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            launcherApps?.registerCallback(launcherAppsCallback)
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try {
            launcherAppWidgetHost.stopListening()
        } catch (_: Exception) {}
        try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            launcherApps?.unregisterCallback(launcherAppsCallback)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleWidgetSelection(info: AppWidgetProviderInfo) {
        try {
            val allocatedId = launcherAppWidgetHost.allocateAppWidgetId()
            pendingWidgetId = allocatedId
            pendingWidgetInfo = info

            val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(allocatedId, info.provider)
            if (canBind) {
                proceedAfterWidgetBound(allocatedId, info)
            } else {
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                }
                widgetBindLauncher.launch(bindIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not add widget: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun proceedAfterWidgetBound(id: Int, info: AppWidgetProviderInfo) {
        try {
            val appWidgetInfo = appWidgetManager.getAppWidgetInfo(id) ?: info
            if (appWidgetInfo.configure != null) {
                val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = appWidgetInfo.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                widgetConfigureLauncher.launch(configIntent)
            } else {
                widgetManager.addPageWidget(id)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                pendingWidgetInfo = null
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Widget setup failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            pendingWidgetInfo = null
        }
    }

    private fun launchSearchEntity(entity: SearchEntity, query: String = "") {
        if (query.isNotBlank()) {
            lifecycleScope.launch {
                try {
                    searchRepository.searchIndexRepository.recordLaunch(query, entity.id)
                } catch (_: Exception) {}
            }
        }
        try {
            if (entity.id.startsWith("system:private_space")) {
                val opened = com.sushanth.poplauncher.util.PrivateSpaceHelper.openPrivateSpace(this)
                if (!opened) {
                    Toast.makeText(this, "Private Space is not available on this device", Toast.LENGTH_SHORT).show()
                }
                return
            }

            if (entity is com.sushanth.poplauncher.data.model.AppEntity && entity.componentName != null && entity.userHandle != null) {
                val launcherApps = getSystemService(android.content.pm.LauncherApps::class.java)
                if (launcherApps != null) {
                    try {
                        launcherApps.startMainActivity(entity.componentName, entity.userHandle, null, null)
                        return
                    } catch (_: Exception) {}
                }
            }

            val shortcutPackage = entity.launchIntent?.getStringExtra("shortcut_package")
                ?: (entity as? com.sushanth.poplauncher.data.model.ConversationEntity)?.appPackage
            val shortcutId = entity.launchIntent?.getStringExtra("shortcut_id")
            if (!shortcutPackage.isNullOrBlank() && !shortcutId.isNullOrBlank()) {
                val launcherApps = getSystemService(android.content.pm.LauncherApps::class.java)
                if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                    try {
                        launcherApps.startShortcut(shortcutPackage, shortcutId, null, null, android.os.Process.myUserHandle())
                        return
                    } catch (_: Exception) {}
                }
                // Direct fallback to launching the target app — NEVER display system App Chooser dialog
                val appIntent = packageManager.getLaunchIntentForPackage(shortcutPackage)
                if (appIntent != null) {
                    appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(appIntent)
                    return
                }
            }

            val rawIntent = entity.launchIntent
            val isDummyShortcutIntent = rawIntent?.action == "com.sushanth.poplauncher.ACTION_LAUNCH_SHORTCUT"

            val intent = if (isDummyShortcutIntent) {
                null
            } else {
                rawIntent
            } ?: (entity as? com.sushanth.poplauncher.data.model.AppEntity)?.packageName?.let {
                packageManager.getLaunchIntentForPackage(it)
            } ?: (entity as? com.sushanth.poplauncher.data.model.ConversationEntity)?.appPackage?.let {
                packageManager.getLaunchIntentForPackage(it)
            }

            if (intent != null) {
                try {
                    // If it's a conversation entity with a target app package, guarantee package is set
                    (entity as? ConversationEntity)?.appPackage?.let { targetPkg ->
                        if (intent.`package`.isNullOrBlank() && intent.component == null) {
                            intent.setPackage(targetPkg)
                        }
                    }

                    if (intent.component == null && !intent.`package`.isNullOrBlank()) {
                        try {
                            val resolve = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                            if (resolve?.activityInfo != null) {
                                intent.component =
                                    ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)
                            }
                        } catch (_: Exception) {}
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (intent.action == Intent.ACTION_MAIN) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot launch application: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Cannot launch ${entity.title}", Toast.LENGTH_SHORT).show()
            }
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Application not found or disabled", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Permission denied to open ${entity.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    private fun LauncherApp() {
        var screenState by currentScreen

        BackHandler(enabled = screenState != LauncherScreen.HOME) {
            when (screenState) {
                LauncherScreen.FRICTION_APPS -> screenState = LauncherScreen.SETTINGS
                LauncherScreen.HIDDEN_APPS_SETUP -> screenState = LauncherScreen.SETTINGS
                LauncherScreen.CONVERSATION_APPS -> screenState = LauncherScreen.SETTINGS
                LauncherScreen.APP_GROUPS -> screenState = LauncherScreen.SETTINGS
                LauncherScreen.SETTINGS -> screenState = LauncherScreen.HOME
                LauncherScreen.WIDGET_PICKER -> screenState = LauncherScreen.HOME
                LauncherScreen.HOME -> { /* Launcher does not exit on back press */ }
            }
        }

        Crossfade(targetState = screenState, label = "ScreenTransition") { screen ->
            when (screen) {
                LauncherScreen.HOME -> {
                    HomeScreen(
                        appRepository = appRepository,
                        searchRepository = searchRepository,
                        settingsRepository = settingsRepository,
                        widgetManager = widgetManager,
                        customWidgetRepository = customWidgetRepository,
                        appWidgetHost = launcherAppWidgetHost,
                        onOpenSettings = { screenState = LauncherScreen.SETTINGS },
                        onOpenHiddenAppsSetup = { screenState = LauncherScreen.HIDDEN_APPS_SETUP },
                        onAddWidgetClick = { screenState = LauncherScreen.WIDGET_PICKER },
                        onLaunchEntity = { entity, query -> launchSearchEntity(entity, query) }
                    )
                }
                LauncherScreen.SETTINGS -> {
                    SettingsScreen(
                        settingsRepository = settingsRepository,
                        searchRepository = searchRepository,
                        appRepository = appRepository,
                        onBackClick = { screenState = LauncherScreen.HOME },
                        onOpenFrictionApps = { screenState = LauncherScreen.FRICTION_APPS },
                        onOpenConversationApps = { screenState = LauncherScreen.CONVERSATION_APPS },
                        onOpenHiddenApps = { screenState = LauncherScreen.HIDDEN_APPS_SETUP },
                        onOpenAppGroups = { screenState = LauncherScreen.APP_GROUPS }
                    )
                }
                LauncherScreen.FRICTION_APPS -> {
                    FrictionAppsScreen(
                        appRepository = appRepository,
                        settingsRepository = settingsRepository,
                        onBackClick = { screenState = LauncherScreen.SETTINGS }
                    )
                }
                LauncherScreen.HIDDEN_APPS_SETUP -> {
                    com.sushanth.poplauncher.settings.HiddenAppsSetupScreen(
                        appRepository = appRepository,
                        settingsRepository = settingsRepository,
                        onComplete = {
                            settingsRepository.setHasSetupHiddenApps(true)
                            screenState = LauncherScreen.HOME
                        }
                    )
                }
                LauncherScreen.CONVERSATION_APPS -> {
                    ConversationAppsScreen(
                        appRepository = appRepository,
                        settingsRepository = settingsRepository,
                        onBackClick = { screenState = LauncherScreen.SETTINGS }
                    )
                }
                LauncherScreen.APP_GROUPS -> {
                    com.sushanth.poplauncher.settings.AppGroupsScreen(
                        appRepository = appRepository,
                        settingsRepository = settingsRepository,
                        onBackClick = { screenState = LauncherScreen.SETTINGS }
                    )
                }
                LauncherScreen.WIDGET_PICKER -> {
                    VisualWidgetPickerScreen(
                        customWidgetRepository = customWidgetRepository,
                        customWidgetCatalogRepository = customWidgetCatalogRepository,
                        onWidgetSelected = { info ->
                            screenState = LauncherScreen.HOME
                            handleWidgetSelection(info)
                        },
                        onDismiss = { screenState = LauncherScreen.HOME }
                    )
                }
            }
        }
    }
}