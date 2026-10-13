package com.sushanth.poplauncher.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.sushanth.poplauncher.data.model.SearchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class InstalledApp(
    val packageName: String,
    val activityName: String,
    val componentName: ComponentName? = null,
    val userHandle: UserHandle? = null,
    val isClone: Boolean = false,
    val isPrivate: Boolean = false,
    val label: String,
    val normalizedLabel: String,
    val icon: Drawable?,
    val launchIntent: Intent,
    val words: List<String> = emptyList(),
    val initials: String = "",
) {
    val uniqueId: String
        get() = when {
            isPrivate -> "$packageName#private#${userHandle?.hashCode() ?: 1}"
            isClone -> "$packageName#clone#${userHandle?.hashCode() ?: 1}"
            else -> packageName
        }

    fun matchesIdentifier(id: String): Boolean {
        return id == uniqueId || (!isClone && !isPrivate && id == packageName)
    }

    fun isContainedIn(idSet: Set<String>): Boolean {
        return idSet.contains(uniqueId) || (!isClone && !isPrivate && idSet.contains(packageName))
    }
}

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _installedPackageNames = MutableStateFlow<Set<String>>(emptySet())

    fun isPackageInstalled(pkg: String?): Boolean {
        if (pkg == null) return true
        if (_installedPackageNames.value.contains(pkg)) return true
        return try {
            packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun refreshApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val myUserHandle = Process.myUserHandle()
        val profiles: List<UserHandle> = userManager?.userProfiles ?: listOf(myUserHandle)
        val myPackageName = context.packageName

        val apps = mutableListOf<InstalledApp>()

        if (launcherApps != null) {
            for (profile in profiles) {
                val isPrivate = com.sushanth.poplauncher.util.PrivateSpaceHelper.isPrivateProfile(userManager, profile, context)
                val isQuiet = try { userManager?.isQuietModeEnabled(profile) ?: false } catch (_: Throwable) { false }

                // If private profile is currently locked in quiet mode, never expose its apps
                if (isPrivate && isQuiet) {
                    continue
                }

                val isCloneProfile = profile != myUserHandle && !isPrivate
                val activities = try {
                    launcherApps.getActivityList(null, profile)
                } catch (_: Exception) {
                    emptyList<LauncherActivityInfo>()
                }

                for (act in activities) {
                    try {
                        val pkg = act.applicationInfo.packageName
                        if (pkg == myPackageName) continue

                        val rawLabel = act.label.toString()
                        val displayLabel = when {
                            isPrivate -> "$rawLabel (Private)"
                            isCloneProfile -> "$rawLabel (Clone)"
                            else -> rawLabel
                        }
                        val compName = act.componentName
                        val icon = try {
                            act.getBadgedIcon(context.resources.displayMetrics.densityDpi)
                        } catch (_: Exception) {
                            act.getIcon(context.resources.displayMetrics.densityDpi)
                        }

                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            component = compName
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        }

                        val norm = displayLabel.trim().lowercase()
                        val w = norm.split(NON_ALPHANUMERIC_REGEX).filter { it.isNotBlank() }
                        val ini = w.asSequence().mapNotNull { it.firstOrNull() }.joinToString("")

                        apps.add(
                            InstalledApp(
                                packageName = pkg,
                                activityName = compName.className,
                                componentName = compName,
                                userHandle = profile,
                                isClone = isCloneProfile,
                                isPrivate = isPrivate,
                                label = displayLabel,
                                normalizedLabel = norm,
                                icon = icon,
                                launchIntent = intent,
                                words = w,
                                initials = ini
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        // Fallback to PackageManager if LauncherApps yielded no results
        if (apps.isEmpty()) {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = try {
                packageManager.queryIntentActivities(launcherIntent, 0)
            } catch (_: Exception) {
                emptyList()
            }
            for (info in resolveInfos) {
                try {
                    val pkg = info.activityInfo.packageName
                    if (pkg == myPackageName) continue
                    val actName = info.activityInfo.name
                    val label = info.loadLabel(packageManager).toString()
                    val comp = ComponentName(pkg, actName)
                    val icon = info.loadIcon(packageManager)
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        ?: Intent().apply {
                            component = comp
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    val norm = label.trim().lowercase()
                    val w = norm.split(NON_ALPHANUMERIC_REGEX).filter { it.isNotBlank() }
                    val ini = w.mapNotNull { it.firstOrNull() }.joinToString("")

                    apps.add(
                        InstalledApp(
                            packageName = pkg,
                            activityName = actName,
                            componentName = comp,
                            userHandle = myUserHandle,
                            isClone = false,
                            label = label,
                            normalizedLabel = norm,
                            icon = icon,
                            launchIntent = launchIntent,
                            words = w,
                            initials = ini
                        )
                    )
                } catch (_: Exception) {}
            }
        }

        val distinctApps = apps
            .distinctBy { "${it.packageName}#${it.userHandle}#${it.activityName}" }
            .sortedBy { it.label.lowercase() }

        _installedPackageNames.value = distinctApps.mapTo(HashSet()) { it.packageName }
        _installedApps.value = distinctApps
        distinctApps
    }

    fun getPrivateSpaceApps(): List<InstalledApp> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return emptyList()
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return emptyList()
        val privateProfile = com.sushanth.poplauncher.util.PrivateSpaceHelper.getPrivateProfile(context) ?: return emptyList()

        val isQuiet = try {
            userManager.isQuietModeEnabled(privateProfile)
        } catch (_: Throwable) {
            false
        }
        if (isQuiet) return emptyList()

        val activities = try {
            launcherApps.getActivityList(null, privateProfile)
        } catch (_: Exception) {
            emptyList()
        }

        val apps = mutableListOf<InstalledApp>()
        for (act in activities) {
            try {
                val pkg = act.applicationInfo.packageName
                if (pkg == context.packageName) continue

                val displayLabel = act.label.toString()
                val compName = act.componentName
                val icon = try {
                    act.getBadgedIcon(context.resources.displayMetrics.densityDpi)
                } catch (_: Exception) {
                    act.getIcon(context.resources.displayMetrics.densityDpi)
                }

                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = compName
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }

                val norm = displayLabel.trim().lowercase()
                val w = norm.split(NON_ALPHANUMERIC_REGEX).filter { it.isNotBlank() }
                val ini = w.mapNotNull { it.firstOrNull() }.joinToString("")

                apps.add(
                    InstalledApp(
                        packageName = pkg,
                        activityName = compName.className,
                        componentName = compName,
                        label = displayLabel,
                        normalizedLabel = norm,
                        words = w,
                        initials = ini,
                        icon = icon,
                        launchIntent = intent,
                        isClone = false,
                        isPrivate = true,
                        userHandle = privateProfile
                    )
                )
            } catch (_: Exception) {}
        }
        return apps.distinctBy { "${it.packageName}#${it.componentName}" }.sortedBy { it.label.lowercase() }
    }

    /**
     * Checks if the given query exactly matches any app in the user's friction list.
     * Specification:
     * "When the normalized query exactly matches the normalized application label, generate the bubble launch result."
     * E.g. "insta" -> null, "instagram" -> matches Instagram
     */
    fun findFrictionExactMatch(
        query: String,
        frictionPackages: Set<String>,
        caseSensitive: Boolean = false
    ): com.sushanth.poplauncher.data.model.AppEntity? {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return null

        val currentApps = _installedApps.value
        val match = currentApps.firstOrNull { app ->
            val isFriction = app.isContainedIn(frictionPackages)
            if (!isFriction) return@firstOrNull false

            val baseLabel = if (app.isClone) {
                app.label.removeSuffix(" (Clone)").trim()
            } else {
                app.label.trim()
            }

            if (caseSensitive) {
                baseLabel == trimmedQuery || app.label.trim() == trimmedQuery
            } else {
                val q = trimmedQuery.lowercase()
                baseLabel.lowercase() == q || app.normalizedLabel == q
            }
        } ?: return null

        return match.toSearchEntity(isFriction = true)
    }

    /**
     * Searches standard apps (apps that are NOT in the friction list) using substring/fuzzy matching.
     * Specification:
     * "And this kind of search should be for apps the user selects from a separate UI and every other
     * apps that don't have it set should use something like fuzzy search"
     */
    fun searchStandardApps(
        query: String,
        frictionPackages: Set<String>,
        hiddenPackages: Set<String> = emptySet(),
        caseSensitive: Boolean = false
    ): List<SearchEntity> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return emptyList()

        val normalizedQuery = if (caseSensitive) trimmedQuery else trimmedQuery.lowercase()
        val currentApps = _installedApps.value

        val knownAcronymTargets = KNOWN_ACRONYMS[normalizedQuery] ?: emptySet()
        val categoryTokens = CATEGORY_KEYWORDS[normalizedQuery] ?: emptySet()

        val scoredApps = mutableListOf<Pair<InstalledApp, Int>>()

        for (app in currentApps) {
            // Strictly exclude any apps marked under user friction or hidden apps
            if (app.isContainedIn(frictionPackages) || app.isContainedIn(hiddenPackages)) continue

            val target = if (caseSensitive) app.label else app.normalizedLabel
            val words = app.words.ifEmpty { target.split(NON_ALPHANUMERIC_REGEX).filter { it.isNotBlank() } }
            val initials = app.initials.ifEmpty { words.mapNotNull { it.firstOrNull() }.joinToString("") }

            var score = 0

            // 1. Exact match (highest priority)
            if (target == normalizedQuery) {
                score = 10000
            }
            // 2. Starts-with prefix match
            else if (target.startsWith(normalizedQuery)) {
                score = 8000 - target.length
            }
            // 3. Word-boundary starts with query (e.g. "Google Maps" matches "maps", "Play Store" matches "store")
            else if (words.any { it.startsWith(normalizedQuery) }) {
                score = 6500 - target.length
            }
            // 4. Exact initials match (e.g. "gm" -> Google Maps, "ps" -> Play Store)
            else if (initials.isNotEmpty() && (initials == normalizedQuery || initials.startsWith(normalizedQuery))) {
                score = 6000 - target.length
            }
            // 5. Known acronym match (e.g. "yt" -> YouTube, "wa" -> WhatsApp, "ig" -> Instagram)
            else if (knownAcronymTargets.isNotEmpty() && knownAcronymTargets.any { target.contains(it) || app.packageName.contains(it) }) {
                score = 5500 - target.length
            }
            // 6. Semantic category keyword match (e.g. "browser" -> Chrome/Firefox, "music" -> Spotify)
            else if (categoryTokens.isNotEmpty() && categoryTokens.any { token -> target.contains(token) || app.packageName.contains(token) }) {
                score = 5000 - target.length
            }
            // 7. Substring contains
            else if (target.contains(normalizedQuery)) {
                val index = target.indexOf(normalizedQuery)
                score = 4000 - (index * 20) - target.length
            }
            // 8. Typo tolerance (Levenshtein distance <= 1) for queries of length >= 3
            else if (normalizedQuery.length >= 3) {
                if (isLevenshteinMatch(normalizedQuery, target, 1)) {
                    score = 2500 - target.length
                } else if (words.any { isLevenshteinMatch(normalizedQuery, it, 1) }) {
                    score = 2000 - target.length
                }
            }

            if (score > 0) {
                scoredApps.add(app to score)
            }
        }

        return scoredApps
            .sortedWith(
                compareByDescending<Pair<InstalledApp, Int>> { it.second }
                    .thenBy { it.first.label.lowercase() }
            )
            .take(10)
            .map { it.first.toSearchEntity(isFriction = false) }
    }

    private val monochromeIconCache = android.util.LruCache<String, Drawable>(128)

    fun getAppIcon(packageName: String, isMonochrome: Boolean = false, userHandle: UserHandle? = null): Drawable? {
        val cacheKey = "$packageName:${userHandle?.hashCode() ?: 0}"
        if (isMonochrome) {
            val cached = synchronized(monochromeIconCache) { monochromeIconCache[cacheKey] }
            if (cached != null) return cached
        }

        val baseIcon = _installedApps.value.firstOrNull {
            it.packageName == packageName && (userHandle == null || it.userHandle == userHandle)
        }?.icon
            ?: _installedApps.value.firstOrNull { it.packageName == packageName }?.icon
            ?: try {
                packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
        return if (isMonochrome && baseIcon != null) {
            val mono = com.sushanth.poplauncher.util.IconUtils.toMonochromeDrawable(baseIcon)
            synchronized(monochromeIconCache) { monochromeIconCache.put(cacheKey, mono) }
            mono
        } else {
            baseIcon
        }
    }

    fun InstalledApp.toSearchEntity(isFriction: Boolean): com.sushanth.poplauncher.data.model.AppEntity {
        return com.sushanth.poplauncher.data.model.AppEntity(
            id = "app:$packageName#${userHandle.hashCode()}#$activityName",
            title = label,
            subtitle = when {
                isFriction -> "Deliberate launch (Bubble)"
                isPrivate -> "Private Space"
                isClone -> "Dual / Cloned App"
                else -> null
            },
            source = when {
                isPrivate -> "Private Space"
                isClone -> "Cloned App"
                else -> "Application"
            },
            packageName = packageName,
            componentName = componentName,
            userHandle = userHandle,
            isClone = isClone,
            isPrivate = isPrivate,
            launchIntent = launchIntent,
            searchableTerms = listOf(label, packageName),
            isFrictionApp = isFriction
        )
    }

    companion object {
        fun isLevenshteinMatch(s1: String, s2: String, maxDistance: Int = 1): Boolean {
            val n = s1.length
            val m = s2.length
            if (abs(n - m) > maxDistance) return false
            if (n == 0) return m <= maxDistance
            if (m == 0) return n <= maxDistance

            if (maxDistance == 1) {
                if (n == m) {
                    var diff = 0
                    for (i in 0 until n) {
                        if (s1[i] != s2[i]) {
                            diff++
                            if (diff > 1) return false
                        }
                    }
                    return true
                } else if (n == m + 1) {
                    var i = 0; var j = 0; var diff = 0
                    while (i < n && j < m) {
                        if (s1[i] != s2[j]) {
                            diff++
                            if (diff > 1) return false
                            i++
                        } else {
                            i++; j++
                        }
                    }
                    return true
                } else if (m == n + 1) {
                    var i = 0; var j = 0; var diff = 0
                    while (i < n && j < m) {
                        if (s1[i] != s2[j]) {
                            diff++
                            if (diff > 1) return false
                            j++
                        } else {
                            i++; j++
                        }
                    }
                    return true
                }
            }

            var prev = IntArray(m + 1) { it }
            var curr = IntArray(m + 1)

            for (i in 1..n) {
                curr[0] = i
                val c1 = s1[i - 1]
                for (j in 1..m) {
                    val cost = if (c1 == s2[j - 1]) 0 else 1
                    curr[j] = minOf(
                        prev[j] + 1,
                        curr[j - 1] + 1,
                        prev[j - 1] + cost
                    )
                }
                val temp = prev
                prev = curr
                curr = temp
            }
            return prev[m] <= maxDistance
        }

        val NON_ALPHANUMERIC_REGEX = Regex("[^a-zA-Z0-9]+")

        val KNOWN_ACRONYMS = mapOf(
            "yt" to setOf("youtube"),
            "ytm" to setOf("youtube music"),
            "yts" to setOf("youtube studio"),
            "wa" to setOf("whatsapp"),
            "wab" to setOf("whatsapp business"),
            "fb" to setOf("facebook"),
            "msgr" to setOf("messenger"),
            "tg" to setOf("telegram"),
            "tw" to setOf("twitter", "x"),
            "sc" to setOf("snapchat"),
            "rd" to setOf("reddit"),
            "sp" to setOf("spotify"),
            "spot" to setOf("spotify"),
            "nf" to setOf("netflix"),
            "gp" to setOf("google pay", "play store"),
            "gpay" to setOf("google pay"),
            "ps" to setOf("play store", "google play"),
            "gc" to setOf("camera", "google camera"),
            "gcam" to setOf("camera", "google camera"),
            "gm" to setOf("maps", "google maps"),
            "am" to setOf("amazon", "apple music"),
            "pv" to setOf("prime video"),
            "gh" to setOf("github"),
            "tt" to setOf("tiktok"),
            "ig" to setOf("instagram")
        )

        val CATEGORY_KEYWORDS = mapOf(
            "browser" to setOf("chrome", "firefox", "brave", "edge", "opera", "safari", "duckduckgo", "browser", "internet", "web"),
            "internet" to setOf("chrome", "firefox", "brave", "edge", "opera", "browser"),
            "web" to setOf("chrome", "firefox", "brave", "edge", "opera", "browser"),
            "mail" to setOf("gmail", "outlook", "mail", "email", "inbox", "protonmail"),
            "email" to setOf("gmail", "outlook", "mail", "email", "inbox"),
            "camera" to setOf("camera", "cam", "photo", "pic"),
            "photos" to setOf("photos", "gallery", "album", "image", "images"),
            "gallery" to setOf("gallery", "photos", "album", "images"),
            "music" to setOf("spotify", "music", "audio", "songs", "sound", "apple music", "yt music", "youtube music", "soundcloud", "shazam", "wynk"),
            "songs" to setOf("spotify", "music", "audio", "apple music", "youtube music", "wynk"),
            "video" to setOf("vlc", "mx player", "video", "youtube", "netflix", "prime video", "disney"),
            "movies" to setOf("netflix", "prime video", "disney", "hotstar", "hulu", "vlc", "mx player"),
            "calc" to setOf("calculator", "math"),
            "calculator" to setOf("calculator", "calc", "math"),
            "clock" to setOf("clock", "alarm", "timer", "stopwatch"),
            "alarm" to setOf("clock", "alarm", "timer"),
            "timer" to setOf("clock", "alarm", "timer"),
            "calendar" to setOf("calendar", "schedule", "events", "agenda"),
            "notes" to setOf("notes", "keep", "memo", "notepad", "notion", "obsidian"),
            "files" to setOf("files", "file", "explorer", "storage", "documents", "manager"),
            "storage" to setOf("files", "file", "explorer", "storage"),
            "settings" to setOf("settings", "config", "preferences", "system"),
            "maps" to setOf("maps", "navigation", "gps", "waze"),
            "navigation" to setOf("maps", "navigation", "gps", "waze"),
            "phone" to setOf("phone", "dialer", "call", "calls", "truecaller"),
            "dialer" to setOf("phone", "dialer", "call"),
            "messages" to setOf("messages", "message", "sms", "messaging", "chat"),
            "sms" to setOf("messages", "sms", "message", "text"),
            "store" to setOf("play store", "google play", "store", "market", "app store")
        )
    }
}
