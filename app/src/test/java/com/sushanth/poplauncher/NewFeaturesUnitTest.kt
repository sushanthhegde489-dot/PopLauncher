package com.sushanth.poplauncher

import com.sushanth.poplauncher.data.model.GestureActionType
import com.sushanth.poplauncher.data.model.GestureConfig
import com.sushanth.poplauncher.data.model.GestureType
import com.sushanth.poplauncher.widgets.WidgetLayout
import com.sushanth.poplauncher.widgets.WidgetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewFeaturesUnitTest {

    @Test
    fun testGestureConfigSerialization() {
        val config = GestureConfig(
            actionType = GestureActionType.OPEN_APP,
            targetPackage = "com.android.chrome",
            targetLabel = "Chrome"
        )
        val serialized = config.toSerialized()
        val parsed = GestureConfig.fromSerialized(serialized)

        assertEquals(GestureActionType.OPEN_APP, parsed.actionType)
        assertEquals("com.android.chrome", parsed.targetPackage)
        assertEquals("Chrome", parsed.targetLabel)
    }

    @Test
    fun testGestureConfigDefaultEmptySerialization() {
        val parsed = GestureConfig.fromSerialized(null)
        assertEquals(GestureActionType.NONE, parsed.actionType)
        assertEquals(null, parsed.targetPackage)

        val emptyParsed = GestureConfig.fromSerialized("")
        assertEquals(GestureActionType.NONE, emptyParsed.actionType)

        // Lock screen migration to NONE (since admin permission is not used)
        val lockScreenParsed = GestureConfig.fromSerialized("LOCK_SCREEN||")
        assertEquals(GestureActionType.NONE, lockScreenParsed.actionType)

        // Legacy OPEN_NOTIFICATIONS migration to OPEN_NOTIFICATION_SHADE
        val notifParsed = GestureConfig.fromSerialized("OPEN_NOTIFICATIONS||")
        assertEquals(GestureActionType.OPEN_NOTIFICATION_SHADE, notifParsed.actionType)

        // OPEN_PRIVATE_SPACE deserialization
        val privateSpaceParsed = GestureConfig.fromSerialized("OPEN_PRIVATE_SPACE||")
        assertEquals(GestureActionType.OPEN_PRIVATE_SPACE, privateSpaceParsed.actionType)
    }

    @Test
    fun testGestureTypeDisplayNames() {
        assertEquals(4, GestureType.values().size)
        assertEquals("Double Tap", GestureType.DOUBLE_TAP.displayName)
        assertEquals("Triple Tap", GestureType.TRIPLE_TAP.displayName)
        assertEquals("Swipe Down", GestureType.SWIPE_DOWN.displayName)
        assertEquals("Swipe Up", GestureType.SWIPE_UP.displayName)
    }

    @Test
    fun testWidgetFindBestFitSlotNoOverlap() {
        // Place first widget at (0, 0) with span (4, 4)
        val existingLayouts = mutableMapOf(
            "widget_1" to WidgetLayout(key = "widget_1", gridX = 0, gridY = 0, spanX = 4, spanY = 4)
        )

        // Find next slot for another 4x4 widget
        val (col2, row2) = WidgetManager.findBestFitSlot(spanX = 4, spanY = 4, existingLayouts = existingLayouts)
        assertEquals(4, col2)
        assertEquals(0, row2)

        // Place second widget at (4, 0)
        existingLayouts["widget_2"] = WidgetLayout(key = "widget_2", gridX = col2, gridY = row2, spanX = 4, spanY = 4)

        // Now first row (row 0, span 4) is full (columns 0..7). Next 4x4 widget should be placed on row 4!
        val (col3, row3) = WidgetManager.findBestFitSlot(spanX = 4, spanY = 4, existingLayouts = existingLayouts)
        assertEquals(0, col3)
        assertEquals(4, row3)
    }

    @Test
    fun testWidgetClampingWithinGrid() {
        val spanX = 10 // exceeds GRID_COLUMNS = 8
        val spanY = 15 // exceeds GRID_ROWS = 12

        val clampedSpanX = spanX.coerceIn(1, WidgetManager.GRID_COLUMNS)
        val clampedSpanY = spanY.coerceIn(1, WidgetManager.GRID_ROWS)

        assertEquals(8, clampedSpanX)
        assertEquals(12, clampedSpanY)

        val (col, row) = WidgetManager.findBestFitSlot(clampedSpanX, clampedSpanY, emptyMap<String, WidgetLayout>())
        assertEquals(0, col)
        assertEquals(0, row)
    }
}
