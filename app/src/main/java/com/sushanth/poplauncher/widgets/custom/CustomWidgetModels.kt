package com.sushanth.poplauncher.widgets.custom

import org.json.JSONArray
import org.json.JSONObject

enum class CustomWidgetStyle {
    NOTHING_MINIMAL
}

enum class TextSize {
    LABEL,
    BODY,
    TITLE,
    BIG_NUMBER,
    HERO
}

enum class TextFont {
    SYSTEM,
    DOT_MATRIX,
    BOLD
}

enum class ElementColor {
    DEFAULT,
    ACCENT,
    MUTED,
    RED,
    CYAN,
    AMBER
}

enum class ActionType {
    INCREMENT,
    DECREMENT,
    RESET,
    SET_VALUE,
    TOGGLE,
    OPEN_APP,
    OPEN_SETTINGS,
    OPEN_URL,
    OPEN_LAUNCHER_SETTINGS,
    EVAL_SCRIPT,
    ADD_TO_LIST,
    REMOVE_FROM_LIST,
    CLEAR_LIST,
    FETCH_API,
    COMPUTE,
    COPY_TO_CLIPBOARD
}

sealed class WidgetElement {
    data class Text(
        val text: String,
        val size: TextSize = TextSize.BODY,
        val font: TextFont = TextFont.SYSTEM,
        val color: ElementColor = ElementColor.DEFAULT,
        val alignment: String = "START"
    ) : WidgetElement()

    data class Button(
        val label: String,
        val icon: String? = null,
        val action: ActionType = ActionType.INCREMENT,
        val targetVar: String = "count",
        val param: String = "1"
    ) : WidgetElement()

    data class TextField(
        val placeholder: String = "Enter text...",
        val targetVar: String = "input",
        val action: ActionType = ActionType.SET_VALUE,
        val param: String = ""
    ) : WidgetElement()

    data class Slider(
        val currentVar: String = "val",
        val minVal: Float = 0f,
        val maxVal: Float = 100f,
        val step: Float = 1f,
        val targetVar: String = "val"
    ) : WidgetElement()

    data class Switch(
        val label: String = "Enable",
        val targetVar: String = "enabled"
    ) : WidgetElement()

    data class Image(
        val url: String = "",
        val heightDp: Int = 120
    ) : WidgetElement()

    data class Chip(
        val label: String = "Tag",
        val action: ActionType = ActionType.SET_VALUE,
        val targetVar: String = "tag",
        val param: String = "",
        val selected: Boolean = false
    ) : WidgetElement()

    data class ProgressBar(
        val currentVar: String = "count",
        val maxVarOrVal: String = "goal",
        val color: ElementColor = ElementColor.ACCENT
    ) : WidgetElement()

    data class Row(
        val children: List<WidgetElement> = emptyList(),
        val spacingDp: Int = 8,
        val alignment: String = "SPACE_BETWEEN"
    ) : WidgetElement()

    data class Column(
        val children: List<WidgetElement> = emptyList(),
        val spacingDp: Int = 8
    ) : WidgetElement()

    data class Card(
        val children: List<WidgetElement> = emptyList(),
        val spacingDp: Int = 8
    ) : WidgetElement()

    data class ItemList(
        val itemsVar: String = "items",
        val itemTemplate: WidgetElement = Text("{item}")
    ) : WidgetElement()

    data class Spacer(val heightDp: Int = 8) : WidgetElement()

    object Divider : WidgetElement()
}

data class CustomWidget(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val style: CustomWidgetStyle = CustomWidgetStyle.NOTHING_MINIMAL,
    val state: MutableMap<String, String> = mutableMapOf(),
    val elements: List<WidgetElement> = emptyList(),
    val sourceVersion: Int? = null
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("id", id)
        root.put("title", title)
        subtitle?.let { root.put("subtitle", it) }
        root.put("style", style.name)
        sourceVersion?.let { root.put("sourceVersion", it) }

        val stateObj = JSONObject()
        state.forEach { (k, v) -> stateObj.put(k, v) }
        root.put("state", stateObj)

        val elemArray = JSONArray()
        elements.forEach { elem ->
            elemArray.put(elementToJson(elem))
        }
        root.put("elements", elemArray)

        return root
    }

    companion object {
        fun fromJson(json: JSONObject): CustomWidget {
            val id = json.optString("id", "widget_${System.currentTimeMillis()}").take(MAX_TEXT_LENGTH)
            val title = json.optString("title", "Custom Widget").take(MAX_TEXT_LENGTH).ifBlank { "Custom Widget" }
            val subtitle = if (json.has("subtitle")) json.optString("subtitle").take(MAX_TEXT_LENGTH) else null
            // The current renderer intentionally uses one calm visual style. Older style names
            // are ignored so previously saved widgets continue to load without preserving legacy
            // neon/promotional styling.
            val style = CustomWidgetStyle.NOTHING_MINIMAL

            val stateMap = linkedMapOf<String, String>()
            val stateObj = json.optJSONObject("state")
            if (stateObj != null) {
                val keys = stateObj.keys()
                while (keys.hasNext() && stateMap.size < MAX_STATE_ENTRIES) {
                    val key = keys.next().toString().take(MAX_STATE_KEY_LENGTH)
                    if (key.isBlank()) continue
                    stateMap[key] = stateObj.optString(key, "").take(MAX_STATE_VALUE_LENGTH)
                }
            }

            val parseBudget = ParseBudget()
            val elementsList = mutableListOf<WidgetElement>()
            val elemArray = json.optJSONArray("elements")
            if (elemArray != null) {
                val limit = minOf(elemArray.length(), MAX_ROOT_ELEMENTS)
                for (i in 0 until limit) {
                    elemArray.optJSONObject(i)?.let { elementFromJson(it, parseBudget, 0) }?.let { elementsList.add(it) }
                }
            }

            return CustomWidget(
                id = id,
                title = title,
                subtitle = subtitle,
                style = style,
                state = stateMap,
                elements = elementsList,
                sourceVersion = json.optInt("sourceVersion", 0).takeIf { it > 0 }
            )
        }

        private fun elementToJson(elem: WidgetElement): JSONObject {
            val obj = JSONObject()
            when (elem) {
                is WidgetElement.Text -> {
                    obj.put("type", "text")
                    obj.put("text", elem.text)
                    obj.put("size", elem.size.name)
                    obj.put("font", elem.font.name)
                    obj.put("color", elem.color.name)
                    obj.put("alignment", elem.alignment)
                }
                is WidgetElement.Button -> {
                    obj.put("type", "button")
                    obj.put("label", elem.label)
                    elem.icon?.let { obj.put("icon", it) }
                    obj.put("action", elem.action.name)
                    obj.put("targetVar", elem.targetVar)
                    obj.put("param", elem.param)
                }
                is WidgetElement.TextField -> {
                    obj.put("type", "textfield")
                    obj.put("placeholder", elem.placeholder)
                    obj.put("targetVar", elem.targetVar)
                    obj.put("action", elem.action.name)
                    obj.put("param", elem.param)
                }
                is WidgetElement.Slider -> {
                    obj.put("type", "slider")
                    obj.put("currentVar", elem.currentVar)
                    obj.put("minVal", elem.minVal)
                    obj.put("maxVal", elem.maxVal)
                    obj.put("step", elem.step)
                    obj.put("targetVar", elem.targetVar)
                }
                is WidgetElement.Switch -> {
                    obj.put("type", "switch")
                    obj.put("label", elem.label)
                    obj.put("targetVar", elem.targetVar)
                }
                is WidgetElement.Image -> {
                    obj.put("type", "image")
                    obj.put("url", elem.url)
                    obj.put("heightDp", elem.heightDp)
                }
                is WidgetElement.Chip -> {
                    obj.put("type", "chip")
                    obj.put("label", elem.label)
                    obj.put("action", elem.action.name)
                    obj.put("targetVar", elem.targetVar)
                    obj.put("param", elem.param)
                    obj.put("selected", elem.selected)
                }
                is WidgetElement.ProgressBar -> {
                    obj.put("type", "progress_bar")
                    obj.put("currentVar", elem.currentVar)
                    obj.put("maxVarOrVal", elem.maxVarOrVal)
                    obj.put("color", elem.color.name)
                }
                is WidgetElement.Row -> {
                    obj.put("type", "row")
                    obj.put("spacingDp", elem.spacingDp)
                    obj.put("alignment", elem.alignment)
                    val arr = JSONArray()
                    elem.children.forEach { arr.put(elementToJson(it)) }
                    obj.put("children", arr)
                }
                is WidgetElement.Column -> {
                    obj.put("type", "column")
                    obj.put("spacingDp", elem.spacingDp)
                    val arr = JSONArray()
                    elem.children.forEach { arr.put(elementToJson(it)) }
                    obj.put("children", arr)
                }
                is WidgetElement.Card -> {
                    obj.put("type", "card")
                    obj.put("spacingDp", elem.spacingDp)
                    val arr = JSONArray()
                    elem.children.forEach { arr.put(elementToJson(it)) }
                    obj.put("children", arr)
                }
                is WidgetElement.ItemList -> {
                    obj.put("type", "list")
                    obj.put("itemsVar", elem.itemsVar)
                    obj.put("itemTemplate", elementToJson(elem.itemTemplate))
                }
                is WidgetElement.Spacer -> {
                    obj.put("type", "spacer")
                    obj.put("heightDp", elem.heightDp)
                }
                is WidgetElement.Divider -> {
                    obj.put("type", "divider")
                }
            }
            return obj
        }

        private fun elementFromJson(obj: JSONObject, budget: ParseBudget, depth: Int): WidgetElement? {
            if (depth > MAX_ELEMENT_DEPTH || budget.count++ >= MAX_TOTAL_ELEMENTS) return null

            val type = obj.optString("type", "").lowercase()
            fun boundedDp(value: Int) = value.coerceIn(0, 256)

            return when (type) {
                "text" -> WidgetElement.Text(
                    text = obj.optString("text", "").take(MAX_TEXT_LENGTH),
                    size = try { TextSize.valueOf(obj.optString("size", "BODY")) } catch (_: Exception) { TextSize.BODY },
                    font = try { TextFont.valueOf(obj.optString("font", "SYSTEM")) } catch (_: Exception) { TextFont.SYSTEM },
                    color = try { ElementColor.valueOf(obj.optString("color", "DEFAULT")) } catch (_: Exception) { ElementColor.DEFAULT },
                    alignment = obj.optString("alignment", "START").take(16)
                )
                "button" -> WidgetElement.Button(
                    label = obj.optString("label", "Action").take(MAX_TEXT_LENGTH),
                    icon = if (obj.has("icon")) obj.optString("icon").take(MAX_TEXT_LENGTH) else null,
                    action = try { ActionType.valueOf(obj.optString("action", "INCREMENT")) } catch (_: Exception) { ActionType.INCREMENT },
                    targetVar = obj.optString("targetVar", "count").take(MAX_STATE_KEY_LENGTH),
                    param = obj.optString("param", "1").take(MAX_STATE_VALUE_LENGTH)
                )
                "input", "textfield" -> WidgetElement.TextField(
                    placeholder = obj.optString("placeholder", "Enter text...").take(MAX_TEXT_LENGTH),
                    targetVar = obj.optString("targetVar", "input").take(MAX_STATE_KEY_LENGTH),
                    action = try { ActionType.valueOf(obj.optString("action", "SET_VALUE")) } catch (_: Exception) { ActionType.SET_VALUE },
                    param = obj.optString("param", "").take(MAX_STATE_VALUE_LENGTH)
                )
                "slider" -> WidgetElement.Slider(
                    currentVar = obj.optString("currentVar", "val").take(MAX_STATE_KEY_LENGTH),
                    minVal = obj.optDouble("minVal", 0.0).toFloat(),
                    maxVal = obj.optDouble("maxVal", 100.0).toFloat(),
                    step = obj.optDouble("step", 1.0).toFloat(),
                    targetVar = obj.optString("targetVar", "val").take(MAX_STATE_KEY_LENGTH)
                )
                "switch", "toggle" -> WidgetElement.Switch(
                    label = obj.optString("label", "Enable").take(MAX_TEXT_LENGTH),
                    targetVar = obj.optString("targetVar", "enabled").take(MAX_STATE_KEY_LENGTH)
                )
                "image" -> WidgetElement.Image(
                    url = obj.optString("url", "").take(512),
                    heightDp = boundedDp(obj.optInt("heightDp", 120))
                )
                "chip" -> WidgetElement.Chip(
                    label = obj.optString("label", "Tag").take(MAX_TEXT_LENGTH),
                    action = try { ActionType.valueOf(obj.optString("action", "SET_VALUE")) } catch (_: Exception) { ActionType.SET_VALUE },
                    targetVar = obj.optString("targetVar", "tag").take(MAX_STATE_KEY_LENGTH),
                    param = obj.optString("param", "").take(MAX_STATE_VALUE_LENGTH),
                    selected = obj.optBoolean("selected", false)
                )
                "progress_bar", "progressbar" -> WidgetElement.ProgressBar(
                    currentVar = obj.optString("currentVar", "count").take(MAX_STATE_KEY_LENGTH),
                    maxVarOrVal = obj.optString("maxVarOrVal", "goal").take(MAX_STATE_VALUE_LENGTH),
                    color = try { ElementColor.valueOf(obj.optString("color", "ACCENT")) } catch (_: Exception) { ElementColor.ACCENT }
                )
                "row" -> WidgetElement.Row(
                    children = parseChildren(obj.optJSONArray("children"), budget, depth),
                    spacingDp = boundedDp(obj.optInt("spacingDp", 8)),
                    alignment = obj.optString("alignment", "SPACE_BETWEEN").take(24)
                )
                "column" -> WidgetElement.Column(
                    children = parseChildren(obj.optJSONArray("children"), budget, depth),
                    spacingDp = boundedDp(obj.optInt("spacingDp", 8))
                )
                "card", "box" -> WidgetElement.Card(
                    children = parseChildren(obj.optJSONArray("children"), budget, depth),
                    spacingDp = boundedDp(obj.optInt("spacingDp", 8))
                )
                "list" -> {
                    val tmplObj = obj.optJSONObject("itemTemplate")
                    val template = if (tmplObj != null) elementFromJson(tmplObj, budget, depth + 1) ?: WidgetElement.Text("{item}") else WidgetElement.Text("{item}")
                    WidgetElement.ItemList(
                        itemsVar = obj.optString("itemsVar", "items").take(MAX_STATE_KEY_LENGTH),
                        itemTemplate = template
                    )
                }
                "spacer" -> WidgetElement.Spacer(heightDp = boundedDp(obj.optInt("heightDp", 8)))
                "divider" -> WidgetElement.Divider
                else -> null
            }
        }

        private fun parseChildren(arr: JSONArray?, budget: ParseBudget, depth: Int): List<WidgetElement> {
            if (arr == null || depth >= MAX_ELEMENT_DEPTH || budget.count >= MAX_TOTAL_ELEMENTS) return emptyList()
            val result = ArrayList<WidgetElement>(minOf(arr.length(), MAX_CHILDREN_PER_CONTAINER))
            val limit = minOf(arr.length(), MAX_CHILDREN_PER_CONTAINER)
            for (i in 0 until limit) {
                arr.optJSONObject(i)?.let { elementFromJson(it, budget, depth + 1) }?.let(result::add)
            }
            return result
        }

        private class ParseBudget(var count: Int = 0)

        private const val MAX_TEXT_LENGTH = 512
        private const val MAX_STATE_VALUE_LENGTH = 2048
        private const val MAX_STATE_KEY_LENGTH = 64
        private const val MAX_STATE_ENTRIES = 64
        private const val MAX_ROOT_ELEMENTS = 32
        private const val MAX_CHILDREN_PER_CONTAINER = 24
        private const val MAX_TOTAL_ELEMENTS = 100
        private const val MAX_ELEMENT_DEPTH = 6

    }
}
