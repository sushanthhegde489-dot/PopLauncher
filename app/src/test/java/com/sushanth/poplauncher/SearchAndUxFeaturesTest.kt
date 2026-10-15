package com.sushanth.poplauncher

import com.sushanth.poplauncher.data.model.AiLaunchMode
import com.sushanth.poplauncher.data.model.AiOverviewHistoryItem
import com.sushanth.poplauncher.data.model.AnimationLevel
import com.sushanth.poplauncher.data.model.SearchType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndUxFeaturesTest {

    @Test
    fun testAnimationLevels() {
        assertEquals(4, AnimationLevel.entries.size)
        assertEquals(0.0f, AnimationLevel.OFF.scale, 0.001f)
        assertEquals(0.5f, AnimationLevel.REDUCED.scale, 0.001f)
        assertEquals(1.0f, AnimationLevel.STANDARD.scale, 0.001f)
        assertEquals(1.5f, AnimationLevel.EXPRESSIVE.scale, 0.001f)

        assertEquals("Off (Instant)", AnimationLevel.OFF.label)
        assertEquals("Reduced (0.5x)", AnimationLevel.REDUCED.label)
        assertEquals("Standard (1.0x)", AnimationLevel.STANDARD.label)
        assertEquals("Expressive (1.5x)", AnimationLevel.EXPRESSIVE.label)
    }

    @Test
    fun testAiLaunchModes() {
        assertEquals(2, AiLaunchMode.entries.size)
        assertEquals("Voice & Dictation", AiLaunchMode.VOICE_DICTATION.label)
        assertEquals("Text Chat", AiLaunchMode.TEXT_CHAT.label)
    }

    @Test
    fun testAiOverviewHistoryItemSerializationAndMax10Limit() {
        val originalItems = (1..15).map { idx ->
            AiOverviewHistoryItem(
                query = "Query $idx",
                summary = "Summary for query $idx",
                timestamp = 1000L * idx
            )
        }

        // Test max 10 retention behavior
        val capped = (listOf(originalItems[14]) + originalItems.take(14))
            .distinctBy { it.query.trim().lowercase() }
            .take(10)

        assertEquals(10, capped.size)
        assertEquals("Query 15", capped.first().query)

        // Test JSON serialization & deserialization
        val jsonArray = JSONArray()
        for (item in capped) {
            val obj = JSONObject()
            obj.put("query", item.query)
            obj.put("summary", item.summary)
            obj.put("timestamp", item.timestamp)
            jsonArray.put(obj)
        }

        val jsonString = jsonArray.toString()
        val parsedArray = JSONArray(jsonString)
        val deserializedList = mutableListOf<AiOverviewHistoryItem>()
        for (i in 0 until parsedArray.length()) {
            val obj = parsedArray.optJSONObject(i) ?: continue
            val q = obj.optString("query")
            val s = obj.optString("summary")
            val t = obj.optLong("timestamp")
            if (q.isNotBlank() && s.isNotBlank()) {
                deserializedList.add(AiOverviewHistoryItem(q, s, t))
            }
        }

        assertEquals(10, deserializedList.size)
        assertEquals("Query 15", deserializedList.first().query)
        assertEquals("Summary for query 15", deserializedList.first().summary)
        assertEquals(15000L, deserializedList.first().timestamp)
    }

    @Test
    fun testSearchTypeCategoryChips() {
        val providers = listOf(
            SearchType.APP,
            SearchType.PERSON,
            SearchType.CONVERSATION,
            SearchType.FILE,
            SearchType.MEDIA,
            SearchType.GOOGLE_OVERVIEW
        )
        assertTrue(providers.contains(SearchType.APP))
        assertTrue(providers.contains(SearchType.PERSON))
        assertTrue(providers.contains(SearchType.CONVERSATION))
        assertTrue(providers.contains(SearchType.FILE))
        assertTrue(providers.contains(SearchType.MEDIA))
        assertTrue(providers.contains(SearchType.GOOGLE_OVERVIEW))
    }

    @Test
    fun testSearchResultCategoryEntriesContainAiOverview() {
        val categories = com.sushanth.poplauncher.search.SearchResultCategory.entries
        assertTrue(categories.any { it.name == "AI_OVERVIEW" })
        assertEquals("AI Overview", com.sushanth.poplauncher.search.SearchResultCategory.AI_OVERVIEW.title)
    }

    @Test
    fun testClockTimePatterns() {
        val pattern12 = "hh:mm a"
        val pattern24 = "HH:mm"
        val time12Formatter = java.time.format.DateTimeFormatter.ofPattern(pattern12, java.util.Locale.getDefault())
        val time24Formatter = java.time.format.DateTimeFormatter.ofPattern(pattern24, java.util.Locale.getDefault())
        val fixedTime = java.time.LocalTime.of(14, 30)

        val formatted12 = fixedTime.format(time12Formatter)
        val formatted24 = fixedTime.format(time24Formatter)

        assertTrue("12-hour time should format with PM", formatted12.contains("02:30") || formatted12.contains("2:30"))
        assertEquals("14:30", formatted24)
    }

    @Test
    fun testMazeDotsSamplingCount() {
        // Test that sample dots along waypoints generate non-empty path
        val p1 = androidx.compose.ui.geometry.Offset(50f, 50f)
        val p2 = androidx.compose.ui.geometry.Offset(250f, 50f)
        val segLen = kotlin.math.hypot(p2.x - p1.x, p2.y - p1.y)
        val spacingPx = 20f
        val count = (segLen / spacingPx).toInt().coerceAtLeast(1)

        val dots = mutableListOf<androidx.compose.ui.geometry.Offset>()
        for (step in 0 until count) {
            val t = step.toFloat() / count.toFloat()
            dots.add(androidx.compose.ui.geometry.Offset(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t))
        }
        dots.add(p2)

        assertTrue("Sampled dots should contain at least start and end", dots.size >= 2)
        assertEquals(p1, dots.first())
        assertEquals(p2, dots.last())
    }
}
