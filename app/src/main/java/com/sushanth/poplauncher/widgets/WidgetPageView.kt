package com.sushanth.poplauncher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sushanth.poplauncher.widgets.custom.ActionType
import com.sushanth.poplauncher.widgets.custom.CustomWidget
import com.sushanth.poplauncher.widgets.custom.CustomWidgetCard
import kotlin.math.roundToInt

data class SelectedWidgetTarget(
    val key: String,
    val title: String,
    val isCustom: Boolean,
    val customWidget: CustomWidget? = null,
    val androidWidgetId: Int? = null
)

private const val GRID_COLUMNS = WidgetManager.GRID_COLUMNS
private const val GRID_ROWS = WidgetManager.GRID_ROWS

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DedicatedWidgetPage(
    widgetIds: List<Int>,
    customWidgets: List<CustomWidget>,
    appWidgetHost: LauncherAppWidgetHost,
    onRemoveWidget: (Int) -> Unit,
    onRemoveCustomWidget: (String) -> Unit,
    onActionCustomWidget: (String, ActionType, String, String) -> Unit,
    onUpdateWidgetLayout: (String, Int, Int, Int, Int) -> Unit,
    onLongPressEmpty: () -> Unit,
    modifier: Modifier = Modifier,
    widgetLayouts: Map<String, WidgetLayout> = emptyMap(),
    widgetManager: WidgetManager? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }

    var selectedTarget by remember { mutableStateOf<SelectedWidgetTarget?>(null) }
    var targetToRemove by remember { mutableStateOf<SelectedWidgetTarget?>(null) }
    var activeResizingKey by remember { mutableStateOf<String?>(null) }

    // Dragging to move state
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val distinctIds = remember(widgetIds) { widgetIds.distinct() }

    // Dialog for Options (Resize vs Delete)
    selectedTarget?.let { target ->
        WidgetOptionsDialog(
            target = target,
            onResize = {
                activeResizingKey = target.key
                draggingKey = null
                selectedTarget = null
            },
            onRemove = {
                targetToRemove = target
                selectedTarget = null
            },
            onDismiss = { selectedTarget = null }
        )
    }

    // Removal confirmation dialog
    targetToRemove?.let { target ->
        ConfirmRemoveDialog(
            title = "Remove widget?",
            message = "\"${target.title}\" will be removed from your widget page.",
            onConfirm = {
                if (target.isCustom && target.customWidget != null) {
                    onRemoveCustomWidget(target.customWidget.id)
                } else if (target.androidWidgetId != null) {
                    onRemoveWidget(target.androidWidgetId)
                }
                targetToRemove = null
            },
            onDismiss = { targetToRemove = null }
        )
    }

    if (distinctIds.isEmpty() && customWidgets.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .combinedClickable(onClick = {}, onLongClick = onLongPressEmpty)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconPlaceholder()
                Text("Your widget page is empty", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Long-press here for the widget menu, then choose Add Widget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .combinedClickable(
                    onClick = { activeResizingKey = null },
                    onLongClick = onLongPressEmpty
                )
        ) {
            val cellWidth = maxWidth / GRID_COLUMNS
            val cellHeight = maxHeight / GRID_ROWS

            val localDensity = LocalDensity.current
            val density = localDensity.density
            val cellWidthPx = with(localDensity) { cellWidth.toPx() }
            val cellHeightPx = with(localDensity) { cellHeight.toPx() }

            // Collect all display items
            val allItems = remember(customWidgets, distinctIds, widgetLayouts) {
                val list = mutableListOf<SelectedWidgetTarget>()

                customWidgets.forEach { w ->
                    val key = "custom:${w.id}"
                    list.add(SelectedWidgetTarget(key, w.title, true, customWidget = w))
                }

                distinctIds.forEach { id ->
                    val info = appWidgetManager.getAppWidgetInfo(id)
                    if (info != null) {
                        val key = "android:$id"
                        val label = info.loadLabel(context.packageManager) ?: "System Widget"
                        list.add(SelectedWidgetTarget(key, label, false, androidWidgetId = id))
                    }
                }

                list
            }

            // Auto-initialize default slot layouts without overlap using findBestFitSlot
            LaunchedEffect(allItems, widgetLayouts) {
                allItems.forEach { item ->
                    if (!widgetLayouts.containsKey(item.key)) {
                        val (gx, gy) = widgetManager?.findBestFitSlot(
                            WidgetManager.DEFAULT_SPAN_X,
                            WidgetManager.DEFAULT_SPAN_Y,
                            widgetLayouts,
                            excludeKey = item.key
                        ) ?: Pair(0, 0)
                        onUpdateWidgetLayout(
                            item.key,
                            gx,
                            gy,
                            WidgetManager.DEFAULT_SPAN_X,
                            WidgetManager.DEFAULT_SPAN_Y
                        )
                    }
                }
            }

            val isGridActive = draggingKey != null || activeResizingKey != null
            val primaryColor = MaterialTheme.colorScheme.primary
            val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

            // 1. GRID OVERLAY & PLACEMENT PREVIEW (Visible during move or resize)
            if (isGridActive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (col in 0..GRID_COLUMNS) {
                        drawLine(
                            color = gridLineColor,
                            start = Offset(col * cellWidthPx, 0f),
                            end = Offset(col * cellWidthPx, GRID_ROWS * cellHeightPx),
                            strokeWidth = 1f
                        )
                    }
                    for (row in 0..GRID_ROWS) {
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, row * cellHeightPx),
                            end = Offset(GRID_COLUMNS * cellWidthPx, row * cellHeightPx),
                            strokeWidth = 1f
                        )
                    }
                }
            }

            // Render Preview Placement Box when Dragging
            if (draggingKey != null) {
                val dragTarget = allItems.firstOrNull { it.key == draggingKey }
                if (dragTarget != null && cellWidthPx > 0f && cellHeightPx > 0f) {
                    val currentLayout = widgetLayouts[dragTarget.key]
                        ?: WidgetLayout(
                            dragTarget.key,
                            0,
                            0,
                            WidgetManager.DEFAULT_SPAN_X,
                            WidgetManager.DEFAULT_SPAN_Y
                        )
                    val basePxX = cellWidthPx * currentLayout.gridX
                    val basePxY = cellHeightPx * currentLayout.gridY

                    val currentPxX = basePxX + (dragOffsetX * density)
                    val currentPxY = basePxY + (dragOffsetY * density)

                    val safeSpanX = currentLayout.spanX.coerceIn(1, GRID_COLUMNS)
                    val safeSpanY = currentLayout.spanY.coerceIn(1, GRID_ROWS)
                    val maxGridX = (GRID_COLUMNS - safeSpanX).coerceAtLeast(0)
                    val maxGridY = (GRID_ROWS - safeSpanY).coerceAtLeast(0)

                    val targetGridX = ((currentPxX / cellWidthPx).roundToInt()).coerceIn(0, maxGridX)
                    val targetGridY = ((currentPxY / cellHeightPx).roundToInt()).coerceIn(0, maxGridY)

                    val previewX = cellWidth * targetGridX
                    val previewY = cellHeight * targetGridY
                    val previewWidth = cellWidth * safeSpanX
                    val previewHeight = cellHeight * safeSpanY

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    previewX.toPx().roundToInt(),
                                    previewY.toPx().roundToInt()
                                )
                            }
                            .width(previewWidth)
                            .height(previewHeight)
                            .padding(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 2.dp,
                                color = primaryColor,
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
            }

            // 2. RENDER WIDGET ITEMS
            allItems.forEach { target ->
                key(target.key) {
                    val key = target.key
                    val layout = widgetLayouts[key]
                        ?: WidgetLayout(
                            key,
                            0,
                            0,
                            WidgetManager.DEFAULT_SPAN_X,
                            WidgetManager.DEFAULT_SPAN_Y
                        )
                    val isResizing = activeResizingKey == key
                    val isDragging = draggingKey == key
                    val canMove = activeResizingKey == null

                    val itemWidth = cellWidth * layout.spanX
                    val itemHeight = cellHeight * layout.spanY

                    val baseOffsetX = cellWidth * layout.gridX
                    val baseOffsetY = cellHeight * layout.gridY

                    val displayOffsetX = if (isDragging) baseOffsetX + dragOffsetX.dp else baseOffsetX
                    val displayOffsetY = if (isDragging) baseOffsetY + dragOffsetY.dp else baseOffsetY

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    displayOffsetX.toPx().roundToInt(),
                                    displayOffsetY.toPx().roundToInt()
                                )
                            }
                            .width(itemWidth)
                            .height(itemHeight)
                            .padding(4.dp)
                            .then(
                                if (isResizing) {
                                    Modifier.border(
                                        width = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                } else Modifier
                            )
                            .then(
                                if (canMove) {
                                    Modifier.pointerInput(key, layout, cellWidth, cellHeight) {
                                        detectDragGestures(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                activeResizingKey = null
                                                draggingKey = key
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                if (draggingKey == key && cellWidth.value > 0f && cellHeight.value > 0f) {
                                                    val deltaXCells = (dragOffsetX.dp / cellWidth).roundToInt()
                                                    val deltaYCells = (dragOffsetY.dp / cellHeight).roundToInt()

                                                    val safeSpanX = layout.spanX.coerceIn(1, GRID_COLUMNS)
                                                    val safeSpanY = layout.spanY.coerceIn(1, GRID_ROWS)
                                                    val maxGridX = (GRID_COLUMNS - safeSpanX).coerceAtLeast(0)
                                                    val maxGridY = (GRID_ROWS - safeSpanY).coerceAtLeast(0)

                                                    val newGridX = (layout.gridX + deltaXCells).coerceIn(0, maxGridX)
                                                    val newGridY = (layout.gridY + deltaYCells).coerceIn(0, maxGridY)

                                                    onUpdateWidgetLayout(key, newGridX, newGridY, safeSpanX, safeSpanY)

                                                    draggingKey = null
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                }
                                            },
                                            onDragCancel = {
                                                draggingKey = null
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetX += dragAmount.x / density
                                                dragOffsetY += dragAmount.y / density
                                            }
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        if (target.isCustom && target.customWidget != null) {
                            CustomWidgetCard(
                                widget = target.customWidget,
                                onAction = onActionCustomWidget,
                                heightDp = itemHeight.value.toInt(),
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedTarget = target
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (target.androidWidgetId != null) {
                            val info = appWidgetManager.getAppWidgetInfo(target.androidWidgetId)
                            if (info != null) {
                                Box(modifier = Modifier.fillMaxSize().combinedClickable(onClick = {}, onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedTarget = target
                                })) {
                                    WidgetHostCard(
                                        info = info,
                                        host = appWidgetHost,
                                        widgetId = target.androidWidgetId,
                                        itemWidthDp = itemWidth.value.toInt(),
                                        itemHeightDp = itemHeight.value.toInt()
                                    )
                                }
                            }
                        }

                        // 4-Directional Interactive Resizing Overlay & Span Controls
                        if (isResizing) {
                            FourDirectionalResizeOverlay(
                                layout = layout,
                                onUpdateLayout = { gx, gy, sx, sy ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateWidgetLayout(key, gx, gy, sx, sy)
                                },
                                onDone = { activeResizingKey = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FourDirectionalResizeOverlay(
    layout: WidgetLayout,
    onUpdateLayout: (gridX: Int, gridY: Int, spanX: Int, spanY: Int) -> Unit,
    onDone: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var dragAccX by remember { mutableFloatStateOf(0f) }
    var dragAccY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
    ) {
        // TOP Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.TopCenter)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .pointerInput(layout) {
                    detectDragGestures(
                        onDragStart = { dragAccY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccY += dragAmount.y
                            if (dragAccY < -30f && layout.gridY > 0) {
                                onUpdateLayout(layout.gridX, layout.gridY - 1, layout.spanX, (layout.spanY + 1).coerceAtMost(GRID_ROWS - (layout.gridY - 1)))
                                dragAccY = 0f
                            } else if (dragAccY > 30f && layout.spanY > 1) {
                                onUpdateLayout(layout.gridX, layout.gridY + 1, layout.spanX, layout.spanY - 1)
                                dragAccY = 0f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.DragHandle, contentDescription = "Resize Top", tint = Color.White, modifier = Modifier.size(16.dp))
        }

        // BOTTOM Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .pointerInput(layout) {
                    detectDragGestures(
                        onDragStart = { dragAccY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccY += dragAmount.y
                            if (dragAccY > 30f && layout.gridY + layout.spanY < GRID_ROWS) {
                                onUpdateLayout(layout.gridX, layout.gridY, layout.spanX, layout.spanY + 1)
                                dragAccY = 0f
                            } else if (dragAccY < -30f && layout.spanY > 1) {
                                onUpdateLayout(layout.gridX, layout.gridY, layout.spanX, layout.spanY - 1)
                                dragAccY = 0f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.DragHandle, contentDescription = "Resize Bottom", tint = Color.White, modifier = Modifier.size(16.dp))
        }

        // LEFT Handle
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxSize()
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .pointerInput(layout) {
                    detectDragGestures(
                        onDragStart = { dragAccX = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccX += dragAmount.x
                            if (dragAccX < -30f && layout.gridX > 0) {
                                onUpdateLayout(layout.gridX - 1, layout.gridY, (layout.spanX + 1).coerceAtMost(GRID_COLUMNS - (layout.gridX - 1)), layout.spanY)
                                dragAccX = 0f
                            } else if (dragAccX > 30f && layout.spanX > 1) {
                                onUpdateLayout(layout.gridX + 1, layout.gridY, layout.spanX - 1, layout.spanY)
                                dragAccX = 0f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.DragHandle, contentDescription = "Resize Left", tint = Color.White, modifier = Modifier.size(16.dp))
        }

        // RIGHT Handle
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxSize()
                .align(Alignment.CenterEnd)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .pointerInput(layout) {
                    detectDragGestures(
                        onDragStart = { dragAccX = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccX += dragAmount.x
                            if (dragAccX > 30f && layout.gridX + layout.spanX < GRID_COLUMNS) {
                                onUpdateLayout(layout.gridX, layout.gridY, layout.spanX + 1, layout.spanY)
                                dragAccX = 0f
                            } else if (dragAccX < -30f && layout.spanX > 1) {
                                onUpdateLayout(layout.gridX, layout.gridY, layout.spanX - 1, layout.spanY)
                                dragAccX = 0f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.DragHandle, contentDescription = "Resize Right", tint = Color.White, modifier = Modifier.size(16.dp))
        }

        // Quick Grid Size Chip Control + Done Floating Button in Center
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${layout.spanX} × ${layout.spanY} Grid",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Quick Columns & Rows Adjusters
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Width -
                    IconButton(
                        onClick = {
                            if (layout.spanX > 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateLayout(layout.gridX, layout.gridY, layout.spanX - 1, layout.spanY)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Narrower", modifier = Modifier.size(14.dp))
                    }

                    Text("W:${layout.spanX}", style = MaterialTheme.typography.labelSmall)

                    // Width +
                    IconButton(
                        onClick = {
                            if (layout.gridX + layout.spanX < GRID_COLUMNS) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateLayout(layout.gridX, layout.gridY, layout.spanX + 1, layout.spanY)
                            } else if (layout.gridX > 0 && layout.spanX < GRID_COLUMNS) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateLayout(layout.gridX - 1, layout.gridY, layout.spanX + 1, layout.spanY)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Wider", modifier = Modifier.size(14.dp))
                    }

                    Spacer(Modifier.width(6.dp))

                    // Height -
                    IconButton(
                        onClick = {
                            if (layout.spanY > 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateLayout(layout.gridX, layout.gridY, layout.spanX, layout.spanY - 1)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Shorter", modifier = Modifier.size(14.dp))
                    }

                    Text("H:${layout.spanY}", style = MaterialTheme.typography.labelSmall)

                    // Height +
                    IconButton(
                        onClick = {
                            if (layout.gridY + layout.spanY < GRID_ROWS) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateLayout(layout.gridX, layout.gridY, layout.spanX, layout.spanY + 1)
                            } else if (layout.gridY > 0 && layout.spanY < GRID_ROWS) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateLayout(layout.gridX, layout.gridY - 1, layout.spanX, layout.spanY + 1)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Taller", modifier = Modifier.size(14.dp))
                    }
                }

                // Done Button
                IconButton(
                    onClick = onDone,
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun WidgetHostCard(
    info: AppWidgetProviderInfo,
    host: LauncherAppWidgetHost,
    widgetId: Int,
    itemWidthDp: Int,
    itemHeightDp: Int
) {
    val context = LocalContext.current
    LaunchedEffect(widgetId, itemWidthDp, itemHeightDp) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val options = android.os.Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, itemWidthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, itemWidthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, itemHeightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, itemHeightDp)
            }
            appWidgetManager.updateAppWidgetOptions(widgetId, options)
        } catch (_: Exception) {}
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            key(widgetId) {
                AndroidView(
                    factory = { ctx ->
                        host.createView(ctx, widgetId, info).apply { setAppWidget(widgetId, info) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun WidgetOptionsDialog(
    target: SelectedWidgetTarget,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = onResize),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AspectRatio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Resize Widget",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = onRemove),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Delete Widget",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConfirmRemoveDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Remove",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun IconPlaceholder() {
    Icon(
        imageVector = Icons.Outlined.Widgets,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp)
    )
}
