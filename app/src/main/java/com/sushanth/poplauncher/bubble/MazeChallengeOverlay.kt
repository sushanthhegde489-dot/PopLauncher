package com.sushanth.poplauncher.bubble

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.ui.theme.LocalIsMonochromeTheme
import com.sushanth.poplauncher.util.drawableToBitmap
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun MazeChallengeOverlay(
    app: AppEntity,
    iconDrawable: Drawable?,
    onSuccess: (SearchEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    DisposableEffect(vibrator) {
        onDispose {
            vibrator?.cancel()
        }
    }

    val isMonochrome = LocalIsMonochromeTheme.current
    val appBitmap = remember(iconDrawable, isMonochrome) {
        iconDrawable?.let { drawableToBitmap(it, isMonochrome) }
    }

    // Maze Dimensions (280dp x 340dp)
    val mazeWidthDp = 280.dp
    val mazeHeightDp = 340.dp
    val mazeWidthPx = with(density) { mazeWidthDp.toPx() }
    val mazeHeightPx = with(density) { mazeHeightDp.toPx() }

    val corridorWidthPx = with(density) { 56.dp.toPx() }
    val iconRadiusPx = with(density) { 26.dp.toPx() }
    val flagHalfSizePx = with(density) { 16.dp.toPx() }

    // Procedurally generated random maze waypoints on each launch
    val waypoints = remember(mazeWidthPx, mazeHeightPx) {
        generateRandomMazeWaypoints(mazeWidthPx, mazeHeightPx)
    }
    val startNode = waypoints.first()
    val goalNode = waypoints.last()

    val animX = remember(startNode) { Animatable(startNode.x) }
    val animY = remember(startNode) { Animatable(startNode.y) }

    var isSuccess by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(mazeWidthDp + 24.dp, mazeHeightDp + 90.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp,
            border = BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Mindful Maze Navigation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Drag ${app.title} along the path to the finish flag to open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Maze Playground Canvas & Drag Target
                Box(
                    modifier = Modifier
                        .size(mazeWidthDp, mazeHeightDp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    val path = remember(waypoints) {
                        Path().apply {
                            moveTo(waypoints.first().x, waypoints.first().y)
                            for (i in 1 until waypoints.size) {
                                lineTo(waypoints[i].x, waypoints[i].y)
                            }
                        }
                    }

                    // Draw Maze Path Corridors and Goal Target
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Background Path Corridor (Outer Wall Border)
                        drawPath(
                            path = path,
                            color = Color.DarkGray.copy(alpha = 0.5f),
                            style = Stroke(
                                width = corridorWidthPx + 6f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Open Inner Path Corridor
                        drawPath(
                            path = path,
                            color = Color.LightGray.copy(alpha = 0.25f),
                            style = Stroke(
                                width = corridorWidthPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Goal Node Glowing Circle
                        drawCircle(
                            color = Color(0xFF4CAF50).copy(alpha = 0.35f),
                            radius = corridorWidthPx * 0.65f,
                            center = goalNode
                        )
                        drawCircle(
                            color = Color(0xFF4CAF50),
                            radius = corridorWidthPx * 0.45f,
                            center = goalNode,
                            style = Stroke(width = 3f)
                        )
                    }

                    // Goal Target Flag Icon
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (goalNode.x - flagHalfSizePx).roundToInt(),
                                    (goalNode.y - flagHalfSizePx).roundToInt()
                                )
                            }
                            .size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = "Goal",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Draggable Popped App Icon
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (animX.value - iconRadiusPx).roundToInt(),
                                    (animY.value - iconRadiusPx).roundToInt()
                                )
                            }
                            .size(52.dp)
                            .shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                ),
                                shape = CircleShape
                            )
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Draggable token for ${app.title}. Navigate maze or activate to complete."
                                onClick(label = "Complete maze and launch ${app.title}") {
                                    isSuccess = true
                                    triggerHeavyHaptic(vibrator)
                                    onSuccess(app)
                                    true
                                }
                            }
                            .pointerInput(waypoints) {
                                detectDragGestures(
                                    onDragStart = {
                                        triggerLightHaptic(vibrator)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (isSuccess) return@detectDragGestures

                                        val newX = animX.value + dragAmount.x
                                        val newY = animY.value + dragAmount.y
                                        val currentPos = Offset(newX, newY)

                                        // Check if touch is inside the corridor
                                        val distToPath = minDistanceToPath(currentPos, waypoints)
                                        val maxAllowedDist = (corridorWidthPx / 2f) - (iconRadiusPx * 0.35f)

                                        if (distToPath <= maxAllowedDist) {
                                            // Valid move along the path
                                            scope.launch {
                                                animX.snapTo(newX)
                                                animY.snapTo(newY)
                                            }

                                            // Check Goal Reach
                                            val distToGoal = hypot(newX - goalNode.x, newY - goalNode.y)
                                            if (distToGoal <= (iconRadiusPx * 1.2f)) {
                                                isSuccess = true
                                                triggerHeavyHaptic(vibrator)
                                                onSuccess(app)
                                            }
                                        } else {
                                            // Wall Collision! Reset to Start
                                            triggerWallCollisionHaptic(vibrator)
                                            scope.launch {
                                                launch { animX.animateTo(startNode.x, spring()) }
                                                launch { animY.animateTo(startNode.y, spring()) }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (!isSuccess) {
                                            scope.launch {
                                                launch { animX.animateTo(startNode.x, spring()) }
                                                launch { animY.animateTo(startNode.y, spring()) }
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (appBitmap != null && !appBitmap.isRecycled) {
                            Image(
                                bitmap = appBitmap.asImageBitmap(),
                                contentDescription = app.title,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 2D Line Segment Distance Math for Maze Corridors
private fun minDistanceToPath(p: Offset, waypoints: List<Offset>): Float {
    var minDist = Float.MAX_VALUE
    for (i in 0 until waypoints.size - 1) {
        val dist = distanceToSegment(p, waypoints[i], waypoints[i + 1])
        if (dist < minDist) minDist = dist
    }
    return minDist
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    if (l2 == 0f) return hypot(p.x - a.x, p.y - a.y)
    var t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2
    t = t.coerceIn(0f, 1f)
    val proj = Offset(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
    return hypot(p.x - proj.x, p.y - proj.y)
}

private fun triggerLightHaptic(vibrator: Vibrator?) {
    vibrator?.takeIf { it.hasVibrator() }?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(12)
        }
    }
}

private fun triggerWallCollisionHaptic(vibrator: Vibrator?) {
    vibrator?.takeIf { it.hasVibrator() }?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(40)
        }
    }
}

private fun triggerHeavyHaptic(vibrator: Vibrator?) {
    vibrator?.takeIf { it.hasVibrator() }?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(75)
        }
    }
}

private val MAZE_DIRECTIONS = listOf(-1 to 0, 1 to 0, 0 to 1, 0 to -1)

private fun generateRandomMazeWaypoints(widthPx: Float, heightPx: Float): List<Offset> {
    if (widthPx <= 0f || heightPx <= 0f) {
        return listOf(Offset(50f, 50f), Offset(200f, 300f))
    }

    val cols = 4
    val rows = 5
    val paddingX = widthPx * 0.12f
    val paddingY = heightPx * 0.12f
    val usableWidth = widthPx - (paddingX * 2f)
    val usableHeight = heightPx - (paddingY * 2f)

    val cellW = usableWidth / (cols - 1)
    val cellH = usableHeight / (rows - 1)

    fun gridToOffset(c: Int, r: Int): Offset {
        return Offset(paddingX + c * cellW, paddingY + r * cellH)
    }

    val startC = Random.nextInt(0, cols)
    val startR = 0
    val goalC = Random.nextInt(0, cols)
    val goalR = rows - 1

    val path = mutableListOf<Pair<Int, Int>>()
    val visited = Array(cols) { BooleanArray(rows) }

    fun dfs(c: Int, r: Int, minLength: Int): Boolean {
        path.add(c to r)
        visited[c][r] = true
        if (c == goalC && r == goalR && path.size >= minLength) {
            return true
        }

        val dirs = MAZE_DIRECTIONS.shuffled()
        for ((dc, dr) in dirs) {
            val nc = c + dc
            val nr = r + dr
            if (nc in 0 until cols && nr in 0 until rows && !visited[nc][nr]) {
                if (dfs(nc, nr, minLength)) return true
            }
        }

        visited[c][r] = false
        path.removeAt(path.size - 1)
        return false
    }

    if (dfs(startC, startR, 6) || dfs(startC, startR, 4)) {
        return path.map { (c, r) -> gridToOffset(c, r) }
    }

    return listOf(
        gridToOffset(startC, 0),
        gridToOffset(startC, 2),
        gridToOffset(goalC, 2),
        gridToOffset(goalC, rows - 1)
    )
}
