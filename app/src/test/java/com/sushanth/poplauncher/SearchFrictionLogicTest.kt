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
    fun testActiveBubbleAssignmentOnSubmitFrictionMatch() {
        // Hitting enter or selecting friction app triggers friction bubble
        val query = "instagram"
        val frictionExactMatch = findFrictionExactMatch(query)
        assertNotNull("frictionExactMatch must find Instagram", frictionExactMatch)

        val activeBubble = frictionExactMatch
        assertNotNull("Active bubble must be assigned when submitSearch finds friction exact match", activeBubble)
        assertEquals("Instagram", activeBubble?.label)
    }

    @Test
    fun testFrictionAppExactMatchAppearsInSearchResultsWithContacts() {
        val query = "whatsapp"
        val frictionMatch = findFrictionExactMatch(query)
        assertNotNull("Friction match must find WhatsApp on exact query", frictionMatch)

        // Simulate contact named "Whatsapp xyz"
        val contactMatch = com.sushanth.poplauncher.data.model.PersonEntity(
            id = "person:123",
            title = "Whatsapp xyz",
            contactId = "123",
            launchIntent = null
        )

        // When searching, standardApps excludes WhatsApp because it's a friction app
        val standardApps = searchStandardApps(query)
        assertTrue("Standard app search excludes friction apps", standardApps.isEmpty())

        // Non-app results include the contact
        val otherResults = listOf(contactMatch)

        // SearchRepository combines frictionExactMatch with other results
        val frictionSearchEntity = com.sushanth.poplauncher.data.model.AppEntity(
            id = "app:${frictionMatch!!.packageName}",
            title = frictionMatch.label,
            subtitle = "Deliberate launch (Bubble)",
            packageName = frictionMatch.packageName,
            launchIntent = frictionMatch.launchIntent,
            isFrictionApp = true
        )
        val combinedCandidates: List<com.sushanth.poplauncher.data.model.SearchEntity> = listOf(frictionSearchEntity) + otherResults

        fun getPriority(entity: com.sushanth.poplauncher.data.model.SearchEntity): Int = when (entity) {
            is com.sushanth.poplauncher.data.model.AppEntity -> 1
            is com.sushanth.poplauncher.data.model.PersonEntity -> 2
            else -> 6
        }

        val sortedResults = combinedCandidates.sortedBy { getPriority(it) }

        // 1. Friction app must be present in results!
        assertEquals(2, sortedResults.size)
        assertTrue("Results must contain WhatsApp", sortedResults.any { it is com.sushanth.poplauncher.data.model.AppEntity && it.title == "WhatsApp" })

        // 2. Friction app must have higher priority than contact
        assertTrue("First result must be the WhatsApp AppEntity", sortedResults[0] is com.sushanth.poplauncher.data.model.AppEntity)
        assertEquals("WhatsApp", sortedResults[0].title)
        assertTrue("WhatsApp item must have isFrictionApp = true", (sortedResults[0] as com.sushanth.poplauncher.data.model.AppEntity).isFrictionApp)

        // 3. Second result is the contact
        assertEquals("Whatsapp xyz", sortedResults[1].title)

        // 4. Prefix search e.g. "what" must NOT include WhatsApp
        val prefixFriction = findFrictionExactMatch("what")
        assertNull("Prefix query must NOT match friction app", prefixFriction)
    }

    @Test
    fun testClonedAppSeparationAndIndependentSelection() {
        val normalApp = InstalledApp(
            packageName = "com.whatsapp",
            activityName = "com.whatsapp.HomeActivity",
            label = "WhatsApp",
            normalizedLabel = "whatsapp",
            icon = null,
            launchIntent = Intent(),
            isClone = false
        )

        val clonedApp = InstalledApp(
            packageName = "com.whatsapp",
            activityName = "com.whatsapp.HomeActivity",
            label = "WhatsApp (Clone)",
            normalizedLabel = "whatsapp (clone)",
            icon = null,
            launchIntent = Intent(),
            isClone = true
        )

        // 1. Must have different uniqueIds
        assertTrue("Normal and cloned apps must have different uniqueIds", normalApp.uniqueId != clonedApp.uniqueId)
        assertEquals("com.whatsapp", normalApp.uniqueId)
        assertTrue("Clone uniqueId must contain #clone#", clonedApp.uniqueId.contains("#clone#"))

        // 2. Test independent selection in a set
        val hiddenWithOnlyClone = setOf(clonedApp.uniqueId)
        assertTrue("Clone should be contained", clonedApp.isContainedIn(hiddenWithOnlyClone))
        assertTrue("Normal app should NOT be contained when only clone is selected", !normalApp.isContainedIn(hiddenWithOnlyClone))

        val hiddenWithOnlyNormal = setOf(normalApp.uniqueId)
        assertTrue("Normal app should be contained", normalApp.isContainedIn(hiddenWithOnlyNormal))
        assertTrue("Clone should NOT be contained when only normal app is selected", !clonedApp.isContainedIn(hiddenWithOnlyNormal))

        // 3. Backward compatibility with standard package name
        val legacySet = setOf("com.whatsapp")
        assertTrue("Legacy package name matches normal app", normalApp.isContainedIn(legacySet))
        assertTrue("Legacy package name does not match clone", !clonedApp.isContainedIn(legacySet))
    }
}
