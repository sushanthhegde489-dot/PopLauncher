package com.sushanth.poplauncher.widgets.custom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CustomWidgetRepository(private val context: Context? = null) {
    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _customWidgets = MutableStateFlow(loadWidgets())
    val customWidgets: StateFlow<List<CustomWidget>> = _customWidgets.asStateFlow()

    private fun loadWidgets(): List<CustomWidget> {
        val raw = prefs?.getString(KEY_WIDGETS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until minOf(arr.length(), MAX_SAVED_WIDGETS)) {
                    arr.optJSONObject(i)?.let { add(CustomWidget.fromJson(it)) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveWidgetsList(list: List<CustomWidget>) {
        val bounded = list.take(MAX_SAVED_WIDGETS)
        val arr = JSONArray()
        bounded.forEach { arr.put(it.toJson()) }
        prefs?.edit { putString(KEY_WIDGETS, arr.toString()) }
        _customWidgets.value = bounded
    }

    fun saveCustomWidget(widget: CustomWidget) {
        val current = _customWidgets.value.toMutableList()
        val index = current.indexOfFirst { it.id == widget.id }
        if (index >= 0) current[index] = widget else current.add(0, widget)
        saveWidgetsList(current)
    }

    fun installPublishedWidget(published: PublishedCustomWidget) {
        saveCustomWidget(published.definition.copy(sourceVersion = published.version))
    }

    fun deleteCustomWidget(widgetId: String) {
        val current = _customWidgets.value.filterNot { it.id == widgetId }
        if (current.size != _customWidgets.value.size) saveWidgetsList(current)
    }

    fun updateWidgetState(widgetId: String, varName: String, newValue: String) {
        val current = _customWidgets.value.toMutableList()
        val index = current.indexOfFirst { it.id == widgetId }
        if (index < 0) return
        val old = current[index]
        val newState = old.state.toMutableMap()
        newState[varName] = newValue.take(MAX_STATE_VALUE_LENGTH)
        current[index] = old.copy(state = newState)
        saveWidgetsList(current)
    }

    fun modifyCounter(widgetId: String, varName: String, delta: Int) {
        val current = _customWidgets.value.toMutableList()
        val index = current.indexOfFirst { it.id == widgetId }
        if (index < 0) return
        val old = current[index]
        val newState = old.state.toMutableMap()
        val currentVal = newState[varName]?.toLongOrNull() ?: 0L
        newState[varName] = (currentVal + delta.toLong()).coerceIn(-999_999_999L, 999_999_999L).toString()
        current[index] = old.copy(state = newState)
        saveWidgetsList(current)
    }

    fun toggleBoolean(widgetId: String, varName: String) {
        val current = _customWidgets.value.toMutableList()
        val index = current.indexOfFirst { it.id == widgetId }
        if (index < 0) return
        val old = current[index]
        val newState = old.state.toMutableMap()
        val currentVal = newState[varName]?.toBooleanStrictOrNull() ?: false
        newState[varName] = (!currentVal).toString()
        current[index] = old.copy(state = newState)
        saveWidgetsList(current)
    }

    fun dispatchAction(
        widgetId: String,
        action: ActionType,
        targetVar: String,
        param: String,
        appContext: Context? = context
    ) {
        val current = _customWidgets.value.toMutableList()
        val index = current.indexOfFirst { it.id == widgetId }
        if (index < 0) return
        val widget = current[index]
        val state = widget.state.toMutableMap()

        fun resolveParam(p: String): String {
            if (p.startsWith("{") && p.endsWith("}")) {
                val key = p.removePrefix("{").removeSuffix("}")
                return state[key] ?: ""
            }
            return interpolate(p, state)
        }

        when (action) {
            ActionType.INCREMENT -> {
                val delta = resolveParam(param).toIntOrNull() ?: 1
                modifyCounter(widgetId, targetVar, delta)
            }
            ActionType.DECREMENT -> {
                val delta = resolveParam(param).toIntOrNull() ?: 1
                modifyCounter(widgetId, targetVar, -delta)
            }
            ActionType.RESET -> {
                val resetVal = resolveParam(param).ifEmpty { "0" }
                updateWidgetState(widgetId, targetVar, resetVal)
            }
            ActionType.SET_VALUE -> {
                val value = resolveParam(param)
                updateWidgetState(widgetId, targetVar, value)
            }
            ActionType.TOGGLE -> {
                toggleBoolean(widgetId, targetVar)
            }
            ActionType.ADD_TO_LIST -> {
                val rawVal = resolveParam(param)
                if (rawVal.isNotBlank()) {
                    val rawList = state[targetVar] ?: "[]"
                    val jsonArray = try { JSONArray(rawList) } catch (_: Exception) { JSONArray() }
                    jsonArray.put(rawVal)
                    state[targetVar] = jsonArray.toString()
                    saveCustomWidget(widget.copy(state = state))
                }
            }
            ActionType.REMOVE_FROM_LIST -> {
                val rawList = state[targetVar] ?: "[]"
                val jsonArray = try { JSONArray(rawList) } catch (_: Exception) { JSONArray() }
                val target = resolveParam(param)
                val newArray = JSONArray()
                val targetIndex = target.toIntOrNull()

                for (i in 0 until jsonArray.length()) {
                    if (targetIndex != null && i == targetIndex) continue
                    val item = jsonArray.optString(i)
                    if (targetIndex == null && item == target) continue
                    newArray.put(item)
                }
                state[targetVar] = newArray.toString()
                saveCustomWidget(widget.copy(state = state))
            }
            ActionType.CLEAR_LIST -> {
                state[targetVar] = "[]"
                saveCustomWidget(widget.copy(state = state))
            }
            ActionType.COMPUTE -> {
                val result = evaluateMathExpression(resolveParam(param), state)
                state[targetVar] = result
                saveCustomWidget(widget.copy(state = state))
            }
            ActionType.EVAL_SCRIPT -> {
                val script = resolveParam(param)
                val statements = script.splitToSequence(";").map { it.trim() }.filter { it.isNotBlank() }
                for (stmt in statements) {
                    if (stmt.contains("=")) {
                        val parts = stmt.split("=", limit = 2)
                        val vName = parts[0].trim()
                        val expr = parts[1].trim()
                        val computed = evaluateMathExpression(expr, state)
                        state[vName] = computed
                    }
                }
                saveCustomWidget(widget.copy(state = state))
            }
            ActionType.COPY_TO_CLIPBOARD -> {
                val textToCopy = resolveParam(param)
                appContext?.let { ctx ->
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Widget", textToCopy)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
            ActionType.FETCH_API -> {
                val urlString = resolveParam(param)
                if (urlString.startsWith("https://")) {
                    scope.launch {
                        val fetchedState = fetchApiData(urlString)
                        if (fetchedState.isNotEmpty()) {
                            val curWidget = _customWidgets.value.firstOrNull { it.id == widgetId } ?: widget
                            val updatedState = curWidget.state.toMutableMap()
                            fetchedState.forEach { (k, v) -> updatedState[k] = v }
                            saveCustomWidget(curWidget.copy(state = updatedState))
                        }
                    }
                }
            }
            else -> { /* Intent / App opening actions handled at UI layer */ }
        }
    }

    private suspend fun fetchApiData(urlString: String): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val results = mutableMapOf<String, String>()
                    val json = runCatching { JSONObject(responseText) }.getOrNull()
                    if (json != null) {
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = json.opt(key)?.toString() ?: ""
                            if (value.length <= MAX_STATE_VALUE_LENGTH) {
                                results["api_$key"] = value
                            }
                        }
                    } else {
                        results["api_raw"] = responseText.take(512)
                    }
                    return@withContext results
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {}
        emptyMap()
    }

    companion object {
        private const val PREFS_NAME = "pop_launcher_custom_widgets"
        private const val KEY_WIDGETS = "saved_custom_widgets"
        private const val MAX_SAVED_WIDGETS = 50
        private const val MAX_STATE_VALUE_LENGTH = 2048

        fun interpolate(template: String, state: Map<String, String>): String {
            return Regex("\\{([a-zA-Z0-9_]+)\\}").replace(template) { match ->
                val key = match.groupValues[1]
                state[key] ?: match.value
            }
        }

        fun evaluateMathExpression(expr: String, state: Map<String, String>): String {
            val resolved = interpolate(expr, state).trim()
            if (resolved.isEmpty()) return ""
            return try {
                val clean = resolved.replace("\\s+".toRegex(), "")
                val result = MathParser(clean).parse()
                result.formatClean()
            } catch (_: Exception) {
                resolved
            }
        }

        private class MathParser(private val str: String) {
            private var pos = 0

            private fun peek(): Char? = if (pos < str.length) str[pos] else null
            private fun next(): Char = str[pos++]

            fun parse(): Double {
                val result = parseExpr()
                if (pos < str.length) throw IllegalArgumentException("Unexpected char: ${str[pos]}")
                return result
            }

            private fun parseExpr(): Double {
                var value = parseFactor()
                while (true) {
                    when (peek()) {
                        '+' -> { next(); value += parseFactor() }
                        '-' -> { next(); value -= parseFactor() }
                        else -> break
                    }
                }
                return value
            }

            private fun parseFactor(): Double {
                var value = parseUnary()
                while (true) {
                    when (peek()) {
                        '*' -> { next(); value *= parseUnary() }
                        '/' -> {
                            next()
                            val denom = parseUnary()
                            value = if (denom == 0.0) 0.0 else value / denom
                        }
                        '%' -> {
                            next()
                            val denom = parseUnary()
                            value = if (denom == 0.0) 0.0 else value % denom
                        }
                        else -> break
                    }
                }
                return value
            }

            private fun parseUnary(): Double {
                if (peek() == '+') { next(); return parseUnary() }
                if (peek() == '-') { next(); return -parseUnary() }
                return parsePrimary()
            }

            private fun parsePrimary(): Double {
                if (peek() == '(') {
                    next()
                    val res = parseExpr()
                    if (peek() == ')') next()
                    return res
                }
                return parseNumber()
            }

            private fun parseNumber(): Double {
                val start = pos
                while (pos < str.length && (str[pos].isDigit() || str[pos] == '.')) {
                    pos++
                }
                if (start == pos) throw IllegalArgumentException("Expected number at $pos")
                return str.substring(start, pos).toDouble()
            }
        }

        private fun Double.formatClean(): String {
            return if (this % 1.0 == 0.0) {
                this.toLong().toString()
            } else {
                String.format(java.util.Locale.US, "%.2f", this)
            }
        }
    }
}
