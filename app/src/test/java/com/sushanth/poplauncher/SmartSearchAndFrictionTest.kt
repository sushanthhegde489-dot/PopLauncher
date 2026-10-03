package com.sushanth.poplauncher

import android.content.Intent
import com.sushanth.poplauncher.data.repository.AppRepository
import com.sushanth.poplauncher.data.repository.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchAndFrictionTest {

    private val testApps = listOf(
        InstalledApp(
            packageName = "com.google.android.youtube",
            activityName = "com.google.android.youtube.MainActivity",
            label = "YouTube",
            normalizedLabel = "youtube",
            icon = null,
            launchIntent = Intent(),
            words = listOf("youtube"),
            initials = "y"
        ),
        InstalledApp(
            packageName = "com.android.chrome",
            activityName = "com.google.android.apps.chrome.Main",
            label = "Chrome",
            normalizedLabel = "chrome",
            icon = null,
            launchIntent = Intent(),
            words = listOf("chrome"),
            initials = "c"
        ),
        InstalledApp(
            packageName = "com.spotify.music",
            activityName = "com.spotify.music.MainActivity",
            label = "Spotify",
            normalizedLabel = "spotify",
            icon = null,
            launchIntent = Intent(),
            words = listOf("spotify"),
            initials = "s"
        ),
        InstalledApp(
            packageName = "com.android.vending",
            activityName = "com.google.android.finsky.activities.MainActivity",
            label = "Play Store",
            normalizedLabel = "play store",
            icon = null,
            launchIntent = Intent(),
            words = listOf("play", "store"),
            initials = "ps"
        ),
        InstalledApp(
            packageName = "com.google.android.apps.maps",
            activityName = "com.google.android.maps.MapsActivity",
            label = "Google Maps",
            normalizedLabel = "google maps",
            icon = null,
            launchIntent = Intent(),
            words = listOf("google", "maps"),
            initials = "gm"
        ),
        InstalledApp(
            packageName = "com.google.android.calculator",
            activityName = "com.android.calculator2.Calculator",
            label = "Calculator",
            normalizedLabel = "calculator",
            icon = null,
            launchIntent = Intent(),
            words = listOf("calculator"),
            initials = "c"
        ),
        InstalledApp(
            packageName = "com.instagram.android",
            activityName = "com.instagram.main.MainActivity",
            label = "Instagram",
            normalizedLabel = "instagram",
            icon = null,
            launchIntent = Intent(),
            words = listOf("instagram"),
            initials = "i"
        )
    )

    private fun smartScoreApp(
        app: InstalledApp,
        query: String,
        frictionPackages: Set<String>
    ): Int {
        if (frictionPackages.contains(app.packageName)) return 0

        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return 0

        val target = app.normalizedLabel
        val words = app.words
        val initials = app.initials

        val knownAcronymTargets = AppRepository.KNOWN_ACRONYMS[normalizedQuery] ?: emptySet()
        val categoryTokens = AppRepository.CATEGORY_KEYWORDS[normalizedQuery] ?: emptySet()

        if (target == normalizedQuery) return 10000
        if (target.startsWith(normalizedQuery)) return 8000 - target.length
        if (words.any { it.startsWith(normalizedQuery) }) return 6500 - target.length
        if (initials.isNotEmpty() && (initials == normalizedQuery || initials.startsWith(normalizedQuery))) return 6000 - target.length
        if (knownAcronymTargets.isNotEmpty() && knownAcronymTargets.any { target.contains(it) || app.packageName.contains(it) }) return 5500 - target.length
        if (categoryTokens.isNotEmpty() && categoryTokens.any { token -> target.contains(token) || app.packageName.contains(token) }) return 5000 - target.length
        if (target.contains(normalizedQuery)) {
            val index = target.indexOf(normalizedQuery)
            return 4000 - (index * 20) - target.length
        }
        if (normalizedQuery.length >= 3) {
            if (AppRepository.isLevenshteinMatch(normalizedQuery, target, 1)) return 2500 - target.length
            if (words.any { AppRepository.isLevenshteinMatch(normalizedQuery, it, 1) }) return 2000 - target.length
        }
        return 0
    }

    private fun searchApps(query: String, frictionPackages: Set<String>): List<InstalledApp> {
        return testApps
            .map { it to smartScoreApp(it, query, frictionPackages) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<InstalledApp, Int>> { it.second }.thenBy { it.first.label })
            .map { it.first }
    }

    @Test
    fun testAcronymMatching() {
        // "yt" matches YouTube
        val ytResults = searchApps("yt", emptySet())
        assertTrue("yt should match YouTube", ytResults.any { it.label == "YouTube" })

        // "ps" matches Play Store (initials)
        val psResults = searchApps("ps", emptySet())
        assertEquals("Play Store", psResults.first().label)

        // "gm" matches Google Maps (initials)
        val gmResults = searchApps("gm", emptySet())
        assertEquals("Google Maps", gmResults.first().label)
    }

    @Test
    fun testSemanticCategoryKeywords() {
        // "browser" finds Chrome
        val browserResults = searchApps("browser", emptySet())
        assertTrue("browser should find Chrome", browserResults.any { it.label == "Chrome" })

        // "music" finds Spotify
        val musicResults = searchApps("music", emptySet())
        assertTrue("music should find Spotify", musicResults.any { it.label == "Spotify" })

        // "calc" finds Calculator
        val calcResults = searchApps("calc", emptySet())
        assertTrue("calc should find Calculator", calcResults.any { it.label == "Calculator" })
    }

    @Test
    fun testWordBoundaryMatching() {
        // "maps" finds Google Maps directly even though it starts with "Google"
        val mapsResults = searchApps("maps", emptySet())
        assertEquals("Google Maps", mapsResults.first().label)

        // "store" finds Play Store
        val storeResults = searchApps("store", emptySet())
        assertEquals("Play Store", storeResults.first().label)
    }

    @Test
    fun testTypoTolerance() {
        // "spotfy" finds Spotify
        val typoSpotify = searchApps("spotfy", emptySet())
        assertTrue("spotfy typo should find Spotify", typoSpotify.any { it.label == "Spotify" })

        // "chome" finds Chrome
        val typoChrome = searchApps("chome", emptySet())
        assertTrue("chome typo should find Chrome", typoChrome.any { it.label == "Chrome" })
    }

    @Test
    fun testInstagramFrictionExclusion() {
        val friction = setOf("com.instagram.android")

        // "insta" MUST NOT return Instagram in standard search when friction is active
        val instaResults = searchApps("insta", friction)
        assertFalse("insta must NOT match Instagram when in friction list", instaResults.any { it.packageName == "com.instagram.android" })

        // "instagram" MUST NOT return in standard search either
        val instagramStandard = searchApps("instagram", friction)
        assertFalse("instagram must NOT return in standard search", instagramStandard.any { it.packageName == "com.instagram.android" })
    }
}
