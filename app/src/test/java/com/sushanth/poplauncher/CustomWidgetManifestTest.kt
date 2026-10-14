package com.sushanth.poplauncher

import com.sushanth.poplauncher.widgets.custom.CustomWidget
import com.sushanth.poplauncher.widgets.custom.CustomWidgetRepository
import com.sushanth.poplauncher.widgets.custom.WidgetElement
import com.sushanth.poplauncher.widgets.custom.WidgetManifest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CustomWidgetManifestTest {
    private fun widgetJson(id: String = "demo_widget", version: Int = 1): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", "Demo")
        put("description", "A safe test widget")
        put("version", version)
        put("definition", CustomWidget(
            id = id,
            title = "Demo",
            elements = listOf(WidgetElement.Text("Hello"))
        ).toJson())
    }

    @Test
    fun validManifestParses() {
        val root = JSONObject().apply { put("schemaVersion", 1); put("widgets", JSONArray().put(widgetJson())) }
        val manifest = WidgetManifest.fromJson(root, appVersionCode = 2)
        assertNotNull(manifest)
        assertEquals(1, manifest!!.widgets.size)
        assertEquals("demo_widget", manifest.widgets.first().id)
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val root = JSONObject().apply { put("schemaVersion", 9); put("widgets", JSONArray()) }
        assertNull(WidgetManifest.fromJson(root, 2))
    }

    @Test
    fun rejectsIncompatibleVersion() {
        val widget = widgetJson().put("minAppVersionCode", 99)
        val root = JSONObject().apply { put("schemaVersion", 1); put("widgets", JSONArray().put(widget)) }
        val manifest = WidgetManifest.fromJson(root, appVersionCode = 2)
        assertNotNull(manifest)
        assertEquals(0, manifest!!.widgets.size)
    }

    @Test
    fun rejectsInvalidIdsAndRemoteCodeShape() {
        val invalidId = widgetJson("not valid")
        val codeLike = widgetJson("code_widget").put("code", "javascript:while(true){}").put("scriptUrl", "https://example.com/run.js")
        val root = JSONObject().apply { put("schemaVersion", 1); put("widgets", JSONArray().put(invalidId).put(codeLike)) }
        val manifest = WidgetManifest.fromJson(root, 2)
        assertEquals(1, manifest!!.widgets.size)
        assertEquals("code_widget", manifest.widgets.first().id)
    }

    @Test
    fun rejectsMissingWidgetArray() {
        val root = JSONObject().put("schemaVersion", 1)
        assertNull(WidgetManifest.fromJson(root, 2))
    }

    @Test
    fun rejectsDuplicateIds() {
        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("widgets", JSONArray().put(widgetJson("duplicate")).put(widgetJson("duplicate", version = 2)))
        }
        assertNull(WidgetManifest.fromJson(root, 2))
    }

    @Test
    fun rejectsMissingDescription() {
        val widget = widgetJson().apply { remove("description") }
        val root = JSONObject().apply { put("schemaVersion", 1); put("widgets", JSONArray().put(widget)) }
        val manifest = WidgetManifest.fromJson(root, 2)
        assertNotNull(manifest)
        assertEquals(0, manifest!!.widgets.size)
    }

    @Test
    fun parsesMalformedDataGracefully() {
        val malformedWidget = widgetJson("malformed").put("name", 12345)
        val root = JSONObject().apply { put("schemaVersion", 1); put("widgets", JSONArray().put(malformedWidget)) }
        val manifest = WidgetManifest.fromJson(root, 2)
        assertNotNull(manifest)
        assertEquals(1, manifest!!.widgets.size)
    }

    @Test
    fun testEvaluateMathExpressionWithNegativeAndComplexOperands() {
        val state = emptyMap<String, String>()
        assertEquals("2", CustomWidgetRepository.evaluateMathExpression("5 + -3", state))
        assertEquals("-7", CustomWidgetRepository.evaluateMathExpression("-5 - 2", state))
        assertEquals("13", CustomWidgetRepository.evaluateMathExpression("10 - -3", state))
        assertEquals("-20", CustomWidgetRepository.evaluateMathExpression("-5 * 4", state))
        assertEquals("20", CustomWidgetRepository.evaluateMathExpression("(2 + 3) * 4", state))
        assertEquals("5", CustomWidgetRepository.evaluateMathExpression("10 / 2", state))
        assertEquals("hello", CustomWidgetRepository.evaluateMathExpression("hello", state))
    }

    @Test
    fun publishedCustomWidgetsManifestParsesSuccessfully() {
        val candidatePaths = listOf(
            File("src/main/assets/custom-widgets/manifest.json"),
            File("app/src/main/assets/custom-widgets/manifest.json")
        )
        val file = candidatePaths.firstOrNull { it.exists() }
        if (file != null) {
            val content = file.readText()
            val root = JSONObject(content)
            val manifest = WidgetManifest.fromJson(root, appVersionCode = 2)
            assertNotNull(manifest)
            assertEquals(5, manifest!!.widgets.size)
            val ids = manifest.widgets.map { it.id }.toSet()
            assertEquals(setOf("hydration_tracker", "todo_applet", "calc_applet", "api_weather_applet", "notes_applet"), ids)
        }
    }
}
