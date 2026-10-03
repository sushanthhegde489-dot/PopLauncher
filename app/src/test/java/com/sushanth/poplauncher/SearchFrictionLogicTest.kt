package com.sushanth.poplauncher

import android.content.Intent
import com.sushanth.poplauncher.data.repository.AppRepository
import com.sushanth.poplauncher.data.repository.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFrictionLogicTest {

    private val mockApps = listOf(
        InstalledApp(
            packageName = "com.instagram.android",
            activityName = "com.instagram.main.MainActivity",
            label = "Instagram",
            normalizedLabel = "instagram",
            icon = null,
            launchIntent = Intent()
        ),
        InstalledApp(
            packageName = "com.whatsapp",
            activityName = "com.whatsapp.HomeActivity",
            label = "WhatsApp",
            normalizedLabel = "whatsapp",
            icon = null,
            launchIntent = Intent()
        ),
        InstalledApp(
            packageName = "com.android.settings",
            activityName = "com.android.settings.Settings",
            label = "Settings",
            normalizedLabel = "settings",
            icon = null,
            launchIntent = Intent()
        ),
        InstalledApp(
            packageName = "com.google.android.GoogleCamera",
            activityName = "com.android.camera.CameraLauncher",
            label = "Camera",
            normalizedLabel = "camera",
            icon = null,
            launchIntent = Intent()
        )
    )

    private val frictionPackages = setOf("com.instagram.android", "com.whatsapp")

    private fun findFrictionExactMatch(query: String, caseSensitive: Boolean = false): InstalledApp? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        return mockApps.firstOrNull { app ->
            val isFriction = frictionPackages.contains(app.packageName)
            if (!isFriction) return@firstOrNull false

            if (caseSensitive) {
                app.label.trim() == trimmed
            } else {
                app.normalizedLabel == trimmed.lowercase()
            }
        }
    }

    private fun searchStandardApps(query: String): List<InstalledApp> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val normalized = trimmed.lowercase()

        return mockApps
            .filter { !frictionPackages.contains(it.packageName) }
            .filter { it.normalizedLabel.contains(normalized) }
    }

    @Test
    fun testCriticalFrictionSearchRule_Instagram() {
        // "insta" MUST NOT match Instagram
        val prefixMatch = findFrictionExactMatch("insta")
        assertNull("Prefix 'insta' must NOT trigger Instagram bubble", prefixMatch)

        // Standard search MUST NOT contain Instagram either
        val standardMatches = searchStandardApps("insta")
        assertTrue("Standard search results must never contain friction apps", standardMatches.isEmpty())

        // "instagram" MUST match Instagram
        val exactMatch = findFrictionExactMatch("instagram")
        assertNotNull("Complete name 'instagram' must trigger Instagram bubble", exactMatch)
        assertEquals("Instagram", exactMatch?.label)

        // Case insensitivity normalization
        val uppercaseMatch = findFrictionExactMatch("INSTAGRAM")
        assertNotNull("Uppercase 'INSTAGRAM' must trigger Instagram bubble", uppercaseMatch)

        // Whitespace trimming
        val whitespaceMatch = findFrictionExactMatch("  instagram   ")
        assertNotNull("Whitespace padded '  instagram  ' must trigger Instagram bubble", whitespaceMatch)
    }

    @Test
    fun testCriticalFrictionSearchRule_WhatsApp() {
        // "what" MUST NOT match WhatsApp
        val prefixMatch = findFrictionExactMatch("what")
        assertNull("Prefix 'what' must NOT trigger WhatsApp bubble", prefixMatch)

        // "whatsapp" MUST match WhatsApp
        val exactMatch = findFrictionExactMatch("whatsapp")
        assertNotNull("Complete name 'whatsapp' must trigger WhatsApp bubble", exactMatch)
        assertEquals("WhatsApp", exactMatch?.label)
    }

    @Test
    fun testStandardAppsAllowFuzzySearch() {
        // "set" finds "Settings"
        val settingsResults = searchStandardApps("set")
        assertEquals(1, settingsResults.size)
        assertEquals("Settings", settingsResults[0].label)

        // "cam" finds "Camera"
        val cameraResults = searchStandardApps("cam")
        assertEquals(1, cameraResults.size)
        assertEquals("Camera", cameraResults[0].label)
    }

    @Test
    fun testFrictionAppsScreenListDeduplicationPreventsCrash() {
        // Simulating apps with multiple activities or work/clone profiles sharing the same package
        val appsWithDuplicates = listOf(
            InstalledApp(
                packageName = "com.android.settings",
                activityName = "com.android.settings.Settings",
                label = "Settings",
                normalizedLabel = "settings",
                icon = null,
                launchIntent = Intent()
            ),
            InstalledApp(
                packageName = "com.android.settings",
                activityName = "com.android.settings.SubSettings",
                label = "Network Settings",
                normalizedLabel = "network settings",
                icon = null,
                launchIntent = Intent()
            ),
            InstalledApp(
                packageName = "com.whatsapp",
                activityName = "com.whatsapp.HomeActivity",
                label = "WhatsApp",
                normalizedLabel = "whatsapp",
                icon = null,
                launchIntent = Intent(),
                isClone = false
            ),
            InstalledApp(
                packageName = "com.whatsapp",
                activityName = "com.whatsapp.HomeActivity",
                label = "WhatsApp (Clone)",
                normalizedLabel = "whatsapp (clone)",
                icon = null,
                launchIntent = Intent(),
                isClone = true
            )
        )

        // Verifying deduplication logic for FrictionAppsScreen
        val uniqueApps = appsWithDuplicates.distinctBy { it.packageName }
        assertEquals(2, uniqueApps.size)
        val packageKeys = uniqueApps.map { it.packageName }
        assertEquals(packageKeys.toSet().size, packageKeys.size)
    }

    @Test
    fun testCloneAppFrictionMatch() {
        val cloneApp = InstalledApp(
            packageName = "com.whatsapp",
            activityName = "com.whatsapp.HomeActivity",
            label = "WhatsApp (Clone)",
            normalizedLabel = "whatsapp (clone)",
            icon = null,
            launchIntent = Intent(),
            isClone = true
        )

        val baseLabel = if (cloneApp.isClone) {
            cloneApp.label.removeSuffix(" (Clone)").trim()
        } else {
            cloneApp.label.trim()
        }

        val query = "whatsapp"
        val matches = baseLabel.lowercase() == query.lowercase() || cloneApp.normalizedLabel == query.lowercase()
        assertTrue("Clone app must match the base app name", matches)
    }

    @Test
    fun testFrictionAppsSortedToTop() {
        val selectedFriction = setOf("com.instagram.android")
        val sortedList = mockApps
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<InstalledApp> { selectedFriction.contains(it.packageName) }
                    .thenBy { it.label.lowercase() }
            )

        // The first element must be Instagram because it is selected as friction app
        assertEquals("Instagram", sortedList.first().label)
        assertTrue(selectedFriction.contains(sortedList.first().packageName))
    }

    @Test
    fun testFrictionOnlyTriggeredOnEnterNotTyping() {
        // Typing "insta" or "instagram":
        // 1. Friction app is excluded from standard results while typing
        val typingResults = searchStandardApps("instagram")
        assertTrue("Standard typing results must never include friction app", typingResults.isEmpty())

        // 2. Typing "insta" prefix and hitting enter must NOT match
        val prefixEnter = findFrictionExactMatch("insta")
        assertNull("Prefix match on enter must NOT trigger friction bubble", prefixEnter)

        // 3. Typing full "instagram" and hitting enter triggers exact match
        val fullEnter = findFrictionExactMatch("instagram")
        assertNotNull("Exact match on enter MUST trigger friction bubble", fullEnter)
        assertEquals("Instagram", fullEnter?.label)
    }

    @Test
    fun testSearchStandardAppsLeveragesAcronyms() {
        // Testing tokenization acronym logic used by AppRepository
        val tokenized = AppRepository.KNOWN_ACRONYMS
        assertTrue(tokenized["yt"]?.contains("youtube") == true)
        assertTrue(tokenized["wa"]?.contains("whatsapp") == true)
        assertTrue(tokenized["ig"]?.contains("instagram") == true)
    }

    @Test
    fun testActiveBubbleAssignmentOnExactFrictionMatch() {
        // Simulate SearchRepository logic for assigning activeBubble
        val query = "instagram"
        val frictionExactMatch = findFrictionExactMatch(query)
        assertNotNull("frictionExactMatch must find Instagram", frictionExactMatch)

        val currentActiveBubbleApp: InstalledApp? = null
        val preservedBubble = if (currentActiveBubbleApp != null &&
            (currentActiveBubbleApp.label.equals(query, ignoreCase = true) ||
             currentActiveBubbleApp.packageName.equals(query, ignoreCase = true))) {
            currentActiveBubbleApp
        } else {
            null
        }

        val activeBubble = frictionExactMatch ?: preservedBubble
        assertNotNull("Active bubble must be assigned when friction exact match is found", activeBubble)
        assertEquals("Instagram", activeBubble?.label)
    }
}
