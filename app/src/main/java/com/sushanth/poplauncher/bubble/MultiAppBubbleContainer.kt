package com.sushanth.poplauncher.bubble

import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.BubbleConfig
import com.sushanth.poplauncher.ui.theme.DarkOnBackground
import com.sushanth.poplauncher.ui.theme.LocalIsMonochromeTheme
import com.sushanth.poplauncher.util.drawableToBitmap
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MultiAppBubbleContainer(
    apps: List<AppEntity>,
    bubbleConfig: BubbleConfig,
    getAppIcon: (String, UserHandle?) -> Drawable?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onAppLaunch: (AppEntity) -> Unit,
) {
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                isResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val bubbleSizeDp = 76.dp
    val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }

    var screenBounds by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f, 0f)) }

    val physicsEngine = remember(apps, bubbleSizePx) {
        MultiBubblePhysicsEngine(apps, bubbleConfig, bubbleSizePx)
    }

    LaunchedEffect(bubbleConfig) {
        physicsEngine.updateConfig(bubbleConfig)
    }

    LaunchedEffect(screenBounds) {
        if ((screenBounds[1] > screenBounds[0]) && (screenBounds[3] > screenBounds[2])) {
            physicsEngine.updateBounds(screenBounds[0], screenBounds[1], screenBounds[2], screenBounds[3])
        }
    }

    // Unified 60/120fps physics simulation loop (pauses when activity is in background)
    LaunchedEffect(physicsEngine, isResumed) {
        if (!isResumed) return@LaunchedEffect
        var lastFrame = 0L
        while (isActive && isResumed) {
            if (physicsEngine.isSettled) {
                lastFrame = 0L
                delay(60.milliseconds)
                continue
            }
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val dt = (now - lastFrame) / 1_000_000_000f
                    physicsEngine.update(dt)
                }
                lastFrame = now
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            }
            .onGloballyPositioned { coords ->
                val w = coords.size.width.toFloat()
                val h = coords.size.height.toFloat()
                val minX = 24f
                val maxX = (w - bubbleSizePx - 24f).coerceAtLeast(minX)
                val minY = with(density) { 90.dp.toPx() }
                val maxY = (h - bubbleSizePx - with(density) { 100.dp.toPx() }).coerceAtLeast(minY)
                screenBounds = floatArrayOf(minX, maxX, minY, maxY)
            }
    ) {
        if (screenBounds[1] > screenBounds[0]) {
            val isMonochrome = LocalIsMonochromeTheme.current
            physicsEngine.bodies.forEach { body ->
                val iconDrawable = remember(body.app.packageName, body.app.userHandle, isMonochrome) {
                    getAppIcon(body.app.packageName, body.app.userHandle)
                }
                PhysicalBubbleItem(
                    body = body,
                    iconDrawable = iconDrawable,
                    isResumed = isResumed,
                    physicsEngine = physicsEngine,
                    bubbleSizeDp = bubbleSizeDp,
                    onAppLaunch = onAppLaunch
                )
            }
        }
    }
}

@Composable
private fun PhysicalBubbleItem(
    body: BubbleBody,
    iconDrawable: Drawable?,
    isResumed: Boolean,
    physicsEngine: MultiBubblePhysicsEngine,
    bubbleSizeDp: Dp,
    onAppLaunch: (AppEntity) -> Unit,
) {
    val isMonochrome = LocalIsMonochromeTheme.current
    val appBitmap = remember(iconDrawable, isMonochrome) {
        iconDrawable?.let { drawableToBitmap(it, isMonochrome) }
    }

    // Gentle organic breathing pulse per bubble (pauses when activity is in background)
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(body.seedIndex, isResumed) {
        if (isResumed) {
            pulseScale.animateTo(
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200 + (body.seedIndex * 150), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            pulseScale.snapTo(1f)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                // Reading body.posX & body.posY directly inside graphicsLayer avoids recomposition!
                translationX = body.posX
                translationY = body.posY
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            }
            .size(bubbleSizeDp + 36.dp)
            .clickable(
                onClickLabel = "Launch ${body.app.title}",
                role = Role.Button
            ) {
                onAppLaunch(body.app)
            }
            .pointerInput(body.app.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    physicsEngine.startDrag(body.app.id)

                    val startX = body.posX
                    val startY = body.posY
                    var cumulativeX = 0f
                    var cumulativeY = 0f
                    var totalDragDistance = 0f
                    val startTime = System.currentTimeMillis()

                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    do {
                        val event = awaitPointerEvent()
                        val currentPointer = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (currentPointer != null && currentPointer.pressed) {
                            val dragAmount = currentPointer.positionChange()
                            currentPointer.consume()

                            cumulativeX += dragAmount.x
                            cumulativeY += dragAmount.y
                            totalDragDistance += hypot(dragAmount.x.toDouble(), dragAmount.y.toDouble()).toFloat()

                            val targetX = startX + cumulativeX
                            val targetY = startY + cumulativeY
                            physicsEngine.updateDrag(body.app.id, targetX, targetY)

                            velocityTracker.addPosition(
                                currentPointer.uptimeMillis,
                                down.position + Offset(cumulativeX, cumulativeY)
                            )
                        }
                    } while (event.changes.any { it.pressed })

                    val elapsed = System.currentTimeMillis() - startTime
                    val vel = velocityTracker.calculateVelocity()
                    physicsEngine.releaseDrag(body.app.id, vel.x, vel.y)

                    if (totalDragDistance < 20f && elapsed < 350) {
                        onAppLaunch(body.app)
                    }
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(bubbleSizeDp)
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primaryContainer,
                    spotColor = MaterialTheme.colorScheme.primary
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.98f)
                        )
                    ),
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            if (appBitmap != null && !appBitmap.isRecycled) {
                Image(
                    bitmap = appBitmap.asImageBitmap(),
                    contentDescription = body.app.title,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = body.app.title.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .offset(y = (-2).dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = body.app.title,
                color = DarkOnBackground,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
