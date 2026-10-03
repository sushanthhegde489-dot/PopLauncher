package com.sushanth.poplauncher.widgets.custom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomWidgetCard(
    widget: CustomWidget,
    onAction: (widgetId: String, action: ActionType, targetVar: String, param: String) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    heightDp: Int? = null
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (heightDp != null && heightDp > 0) Modifier.height(heightDp.dp) else Modifier)
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .then(if (heightDp != null && heightDp > 0) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        ) {
            Text(
                text = widget.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            widget.subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (widget.elements.isNotEmpty()) Spacer(Modifier.height(12.dp))
            var lastClickTime by remember { mutableLongStateOf(0L) }
            widget.elements.forEach { element ->
                RenderWidgetElement(
                    element = element,
                    state = widget.state,
                    onButtonClick = { action, targetVar, param ->
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime > 150L) {
                            lastClickTime = now
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAction(widget.id, action, targetVar, param)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RenderWidgetElement(
    element: WidgetElement,
    state: Map<String, String>,
    onButtonClick: (action: ActionType, targetVar: String, param: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    when (element) {
        is WidgetElement.Text -> {
            val fontSize = when (element.size) {
                TextSize.LABEL -> 12.sp
                TextSize.BODY -> 14.sp
                TextSize.TITLE -> 18.sp
                TextSize.BIG_NUMBER -> 28.sp
                TextSize.HERO -> 36.sp
            }
            val fontWeight = if (element.font == TextFont.BOLD || element.size == TextSize.HERO || element.size == TextSize.BIG_NUMBER) FontWeight.SemiBold else FontWeight.Normal
            val textColor = when (element.color) {
                ElementColor.DEFAULT -> colors.onSurface
                ElementColor.ACCENT -> colors.primary
                ElementColor.MUTED -> colors.onSurfaceVariant
                ElementColor.RED -> colors.error
                ElementColor.CYAN -> colors.secondary
                ElementColor.AMBER -> colors.tertiary
            }
            Text(
                text = interpolateState(element.text, state),
                color = textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = if (element.font == TextFont.DOT_MATRIX) FontFamily.Monospace else FontFamily.Default,
                textAlign = when (element.alignment.uppercase()) {
                    "CENTER" -> TextAlign.Center
                    "END", "RIGHT" -> TextAlign.End
                    else -> TextAlign.Start
                },
                modifier = if (element.alignment.equals("CENTER", true)) Modifier.fillMaxWidth() else Modifier
            )
        }

        is WidgetElement.Button -> {
            Button(
                onClick = { onButtonClick(element.action, element.targetVar, element.param) },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                )
            ) {
                Text(text = interpolateState(element.label, state), fontWeight = FontWeight.SemiBold)
            }
        }

        is WidgetElement.TextField -> {
            var textValue by remember(element.targetVar, state[element.targetVar]) {
                mutableStateOf(state[element.targetVar] ?: "")
            }

            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onButtonClick(ActionType.SET_VALUE, element.targetVar, it)
                },
                placeholder = { Text(element.placeholder, fontSize = 13.sp) },
                trailingIcon = {
                    IconButton(onClick = {
                        onButtonClick(element.action, element.targetVar, element.param.ifEmpty { textValue })
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Submit",
                            tint = colors.primary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onButtonClick(element.action, element.targetVar, element.param.ifEmpty { textValue })
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = colors.surfaceContainerHighest,
                    focusedContainerColor = colors.surfaceContainerHighest,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant
                ),
                singleLine = true
            )
        }

        is WidgetElement.Slider -> {
            val currentVal = state[element.currentVar]?.toFloatOrNull() ?: element.minVal

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentVal.toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
                Slider(
                    value = currentVal.coerceIn(element.minVal, element.maxVal),
                    onValueChange = { newValue ->
                        onButtonClick(ActionType.SET_VALUE, element.targetVar, newValue.toInt().toString())
                    },
                    valueRange = element.minVal..element.maxVal,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary,
                        inactiveTrackColor = colors.surfaceContainerHighest
                    )
                )
            }
        }

        is WidgetElement.Switch -> {
            val isChecked = state[element.targetVar]?.toBooleanStrictOrNull() ?: false

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        onButtonClick(ActionType.TOGGLE, element.targetVar, "")
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = interpolateState(element.label, state),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface
                )
                Switch(
                    checked = isChecked,
                    onCheckedChange = {
                        onButtonClick(ActionType.TOGGLE, element.targetVar, "")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary
                    )
                )
            }
        }

        is WidgetElement.Image -> {
            val url = interpolateState(element.url, state)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(element.heightDp.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (url.startsWith("http")) "📷 [Image: $url]" else url.ifEmpty { "🖼️" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        is WidgetElement.Chip -> {
            AssistChip(
                onClick = { onButtonClick(element.action, element.targetVar, element.param) },
                label = { Text(interpolateState(element.label, state), fontSize = 12.sp) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (element.selected) colors.primaryContainer else colors.surfaceContainerHighest,
                    labelColor = if (element.selected) colors.onPrimaryContainer else colors.onSurface
                )
            )
        }

        is WidgetElement.ProgressBar -> {
            val currentVal = state[element.currentVar]?.toFloatOrNull() ?: 0f
            val maxVal = max(1f, state[element.maxVarOrVal]?.toFloatOrNull() ?: element.maxVarOrVal.toFloatOrNull() ?: 100f)
            val progress = (currentVal / maxVal).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = when (element.color) {
                    ElementColor.RED -> colors.error
                    ElementColor.CYAN -> colors.secondary
                    ElementColor.AMBER -> colors.tertiary
                    else -> colors.primary
                },
                trackColor = colors.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }

        is WidgetElement.Row -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = when (element.alignment.uppercase()) {
                    "CENTER" -> Arrangement.Center
                    "START" -> Arrangement.Start
                    "END" -> Arrangement.End
                    else -> Arrangement.SpaceBetween
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                element.children.forEach { RenderWidgetElement(it, state, onButtonClick) }
            }
        }

        is WidgetElement.Column -> {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(element.spacingDp.dp)) {
                element.children.forEach { RenderWidgetElement(it, state, onButtonClick) }
            }
        }

        is WidgetElement.Card -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceContainerHighest.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(element.spacingDp.dp)
                ) {
                    element.children.forEach { RenderWidgetElement(it, state, onButtonClick) }
                }
            }
        }

        is WidgetElement.ItemList -> {
            val rawItems = state[element.itemsVar] ?: "[]"
            val itemsList = remember(rawItems) {
                val parsed = mutableListOf<String>()
                val jsonArr = runCatching { JSONArray(rawItems) }.getOrNull()
                if (jsonArr != null) {
                    for (i in 0 until jsonArr.length()) {
                        parsed.add(jsonArr.optString(i))
                    }
                } else if (rawItems.isNotBlank() && rawItems != "[]") {
                    parsed.addAll(rawItems.split(",").map { it.trim() })
                }
                parsed
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (itemsList.isEmpty()) {
                    Text(
                        text = "No items in list",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                } else {
                    itemsList.forEachIndexed { idx, item ->
                        val itemState = state.toMutableMap()
                        itemState["item"] = item
                        itemState["index"] = idx.toString()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                RenderWidgetElement(element.itemTemplate, itemState, onButtonClick)
                            }
                            IconButton(
                                onClick = {
                                    onButtonClick(ActionType.REMOVE_FROM_LIST, element.itemsVar, idx.toString())
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("✕", fontSize = 12.sp, color = colors.error)
                            }
                        }
                    }
                }
            }
        }

        is WidgetElement.Spacer -> Spacer(Modifier.height(element.heightDp.dp))
        WidgetElement.Divider -> HorizontalDivider(color = colors.outlineVariant)
    }
}

private val VARIABLE_REGEX = Regex("\\{([a-zA-Z0-9_]+)\\}")
private val EVAL_REGEX = Regex("\\{eval:\\s*(.*?)\\}")

private fun interpolateState(template: String, state: Map<String, String>): String {
    var result = VARIABLE_REGEX.replace(template) { matchResult ->
        val key = matchResult.groupValues[1]
        state[key] ?: matchResult.value
    }
    if (result.contains("{eval:")) {
        result = EVAL_REGEX.replace(result) { match ->
            val expr = match.groupValues[1]
            CustomWidgetRepository.evaluateMathExpression(expr, state)
        }
    }
    return result
}
