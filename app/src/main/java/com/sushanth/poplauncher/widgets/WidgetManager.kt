package com.sushanth.poplauncher.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class WidgetLayout(
    val key: String,
    val gridX: Int = 0,
    val gridY: Int = 0,
    val spanX: Int = WidgetManager.DEFAULT_SPAN_X,
    val spanY: Int = WidgetManager.DEFAULT_SPAN_Y
)

class WidgetManager(context: Context) {

    private val appWidgetManager = AppWidgetManager.getInstance(context)

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_WIDGETS, Context.MODE_PRIVATE)

    private val _pageWidgetIds = MutableStateFlow(loadPageWidgetIds())
    val pageWidgetIds: StateFlow<List<Int>> = _pageWidgetIds.asStateFlow()

    private val _widgetLayouts = MutableStateFlow<Map<String, WidgetLayout>>(loadWidgetLayouts())
    val widgetLayouts: StateFlow<Map<String, WidgetLayout>> = _widgetLayouts.asStateFlow()

    private fun loadPageWidgetIds(): List<Int> {
        val raw = prefs.getString(KEY_PAGE_WIDGET_IDS, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        val loadedIds = raw.split(",").asSequence().mapNotNull { it.trim().toIntOrNull() }.distinct().toList()
        
        // Clean up orphan widget IDs that no longer exist in the system to prevent crashes
        val validIds = loadedIds.filter { id ->
            appWidgetManager.getAppWidgetInfo(id) != null
        }
        
        if (validIds.size != loadedIds.size) {
            prefs.edit { putString(KEY_PAGE_WIDGET_IDS, validIds.joinToString(",")) }
        }
        
        return validIds
    }

    private fun loadWidgetLayouts(): Map<String, WidgetLayout> {
        val raw = prefs.getString(KEY_WIDGET_LAYOUTS, null) ?: return emptyMap()
        val gridVersion = prefs.getInt(KEY_GRID_VERSION, 1)
        return runCatching {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, WidgetLayout>()
            val keys = json.keys()
            val scale = if (gridVersion < CURRENT_GRID_VERSION) 2 else 1
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = json.getJSONObject(k)
                map[k] = WidgetLayout(
                    key = k,
                    gridX = (obj.optInt("gridX", 0) * scale).coerceIn(0, GRID_COLUMNS - 1),
                    gridY = (obj.optInt("gridY", 0) * scale).coerceIn(0, GRID_ROWS - 1),
                    spanX = (obj.optInt("spanX", DEFAULT_SPAN_X / scale) * scale).coerceIn(1, GRID_COLUMNS),
                    spanY = (obj.optInt("spanY", DEFAULT_SPAN_Y / scale) * scale).coerceIn(1, GRID_ROWS)
                )
            }
            if (gridVersion < CURRENT_GRID_VERSION) {
                prefs.edit { putInt(KEY_GRID_VERSION, CURRENT_GRID_VERSION) }
                saveWidgetLayouts(map)
            }
            map
        }.getOrDefault(emptyMap())
    }

    fun findBestFitSlot(
        spanX: Int = DEFAULT_SPAN_X,
        spanY: Int = DEFAULT_SPAN_Y,
        existingLayouts: Map<String, WidgetLayout> = _widgetLayouts.value,
        excludeKey: String? = null
    ): Pair<Int, Int> {
        return Companion.findBestFitSlot(spanX, spanY, existingLayouts, excludeKey)
    }

    fun addPageWidget(appWidgetId: Int) {
        val current = _pageWidgetIds.value.toMutableList()
        if (!current.contains(appWidgetId)) {
            current.add(appWidgetId)
            savePageWidgetIds(current)
            val key = "android:$appWidgetId"
            if (!_widgetLayouts.value.containsKey(key)) {
                val (gx, gy) = findBestFitSlot(DEFAULT_SPAN_X, DEFAULT_SPAN_Y, _widgetLayouts.value, excludeKey = key)
                updateWidgetLayout(key, gx, gy, DEFAULT_SPAN_X, DEFAULT_SPAN_Y)
            }
        }
    }

    fun removePageWidget(appWidgetId: Int, appWidgetHost: LauncherAppWidgetHost) {
        val current = _pageWidgetIds.value.toMutableList()
        if (current.remove(appWidgetId)) {
            try {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            } catch (_: Exception) {}
            savePageWidgetIds(current)
            removeLayout("android:$appWidgetId")
        }
    }

    fun updateWidgetLayout(key: String, gridX: Int, gridY: Int, spanX: Int, spanY: Int) {
        val current = _widgetLayouts.value.toMutableMap()
        val safeSpanX = spanX.coerceIn(1, GRID_COLUMNS)
        val safeSpanY = spanY.coerceIn(1, GRID_ROWS)
        val safeGridX = gridX.coerceIn(0, (GRID_COLUMNS - safeSpanX).coerceAtLeast(0))
        val safeGridY = gridY.coerceIn(0, (GRID_ROWS - safeSpanY).coerceAtLeast(0))
        current[key] = WidgetLayout(
            key = key,
            gridX = safeGridX,
            gridY = safeGridY,
            spanX = safeSpanX,
            spanY = safeSpanY
        )
        saveWidgetLayouts(current)
    }

    fun removeLayout(key: String) {
        val current = _widgetLayouts.value.toMutableMap()
        if (current.remove(key) != null) {
            saveWidgetLayouts(current)
        }
    }

    private fun savePageWidgetIds(ids: List<Int>) {
        prefs.edit { putString(KEY_PAGE_WIDGET_IDS, ids.joinToString(",")) }
        _pageWidgetIds.value = ids
    }

    private fun saveWidgetLayouts(map: Map<String, WidgetLayout>) {
        val root = JSONObject()
        map.forEach { (k, layout) ->
            val obj = JSONObject().apply {
                put("gridX", layout.gridX)
                put("gridY", layout.gridY)
                put("spanX", layout.spanX)
                put("spanY", layout.spanY)
            }
            root.put(k, obj)
        }
        prefs.edit { putString(KEY_WIDGET_LAYOUTS, root.toString()) }
        _widgetLayouts.value = map
    }

    companion object {
        const val GRID_COLUMNS = 8
        const val GRID_ROWS = 12
        const val DEFAULT_SPAN_X = 8
        const val DEFAULT_SPAN_Y = 4

        private const val PREFS_WIDGETS = "pop_launcher_widgets_v2"
        private const val KEY_PAGE_WIDGET_IDS = "page_widget_ids"
        private const val KEY_WIDGET_LAYOUTS = "widget_layouts"
        private const val KEY_GRID_VERSION = "grid_version"
        private const val CURRENT_GRID_VERSION = 2

        fun findBestFitSlot(
            spanX: Int = DEFAULT_SPAN_X,
            spanY: Int = DEFAULT_SPAN_Y,
            existingLayouts: Map<String, WidgetLayout> = emptyMap(),
            excludeKey: String? = null
        ): Pair<Int, Int> {
            val safeSpanX = spanX.coerceIn(1, GRID_COLUMNS)
            val safeSpanY = spanY.coerceIn(1, GRID_ROWS)

            // 8x12 grid representing occupied cells
            val occupied = Array(GRID_COLUMNS) { BooleanArray(GRID_ROWS) }
            for ((key, layout) in existingLayouts) {
                if (key == excludeKey) continue
                val startX = layout.gridX.coerceIn(0, GRID_COLUMNS - 1)
                val startY = layout.gridY.coerceIn(0, GRID_ROWS - 1)
                val endX = (startX + layout.spanX).coerceAtMost(GRID_COLUMNS)
                val endY = (startY + layout.spanY).coerceAtMost(GRID_ROWS)
                for (x in startX until endX) {
                    for (y in startY until endY) {
                        occupied[x][y] = true
                    }
                }
            }

            val maxY = (GRID_ROWS - safeSpanY).coerceAtLeast(0)
            val maxX = (GRID_COLUMNS - safeSpanX).coerceAtLeast(0)

            // Find first completely free rectangle
            for (y in 0..maxY) {
                for (x in 0..maxX) {
                    var canFit = true
                    for (checkX in x until (x + safeSpanX)) {
                        for (checkY in y until (y + safeSpanY)) {
                            if (occupied[checkX][checkY]) {
                                canFit = false
                                break
                            }
                        }
                        if (!canFit) break
                    }
                    if (canFit) {
                        return Pair(x, y)
                    }
                }
            }

            // Fallback: least overlap slot
            var bestScore = Int.MAX_VALUE
            var bestCoord = Pair(0, 0)
            for (y in 0..maxY) {
                for (x in 0..maxX) {
                    var collisionCount = 0
                    for (checkX in x until (x + safeSpanX)) {
                        for (checkY in y until (y + safeSpanY)) {
                            if (occupied[checkX][checkY]) collisionCount++
                        }
                    }
                    if (collisionCount < bestScore) {
                        bestScore = collisionCount
                        bestCoord = Pair(x, y)
                    }
                }
            }
            return bestCoord
        }
    }
}
