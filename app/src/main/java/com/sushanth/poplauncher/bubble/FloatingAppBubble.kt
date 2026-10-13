package com.sushanth.poplauncher.bubble

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.BubbleConfig
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.ui.theme.ChartAmber
import com.sushanth.poplauncher.ui.theme.DarkOnBackground
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class BubbleStage {
    FLOATING_BUBBLE,
    POPPING,
    GRAVITY_FALLING_ICON,
    MAZE_CHALLENGE
}

@Composable
fun FloatingAppBubbleContainer(
    app: AppEntity,
    iconDrawable: Drawable?,
    bubbleConfig: BubbleConfig,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onAppLaunch: (SearchEntity) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    val bubbleSizeDp = 92.dp
    val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }

    val iconSizeDp = 70.dp
    val iconSizePx = with(density) { iconSizeDp.toPx() }

    val physicsEngine = remember { BubblePhysicsEngine(bubbleConfig) }
    val gravityEngine = remember { GravityIconPhysicsEngine() }

    var currentStage by remember { mutableStateOf(BubbleStage.FLOATING_BUBBLE) }

    var bubblePosX by remember { mutableFloatStateOf(0f) }
    var bubblePosY by remember { mutableFloatStateOf(0f) }

    var gravityPosX by remember { mutableFloatStateOf(0f) }
    var gravityPosY by remember { mutableFloatStateOf(0f) }
    var gravityRotation by remember { mutableFloatStateOf(0f) }

    var popCoordinates by remember { mutableStateOf<Offset?>(null) }
    var isCharging by remember { mutableStateOf(value = false) }
    var chargeRatio by remember { mutableFloatStateOf(0f) }
    val chargeThresholdMs = bubbleConfig.popDifficulty.holdThresholdMs

    var screenBounds by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f, 0f)) }

    // Grace period before background tap-to-dismiss is enabled to avoid stray touches during soft keyboard dismissal
    var canDismissByBackgroundTap by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300L)
        canDismissByBackgroundTap = true
    }

    // Breathing pulse for floating bubble
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        pulseScale.animateTo(
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    val isMonochrome = com.sushanth.poplauncher.ui.theme.LocalIsMonochromeTheme.current
    val appBitmap = remember(iconDrawable, isMonochrome) {
        iconDrawable?.let { com.sushanth.poplauncher.util.drawableToBitmap(it, isMonochrome) }
    }

    // Stage 1: Floating Bubble Physics Loop
    LaunchedEffect(currentStage) {
        if (currentStage != BubbleStage.FLOATING_BUBBLE) return@LaunchedEffect
        var lastFrame = 0L

        while (currentStage == BubbleStage.FLOATING_BUBBLE) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val dt = (now - lastFrame) / 1_000_000_000f
                    physicsEngine.update(dt)
                    bubblePosX = physicsEngine.posX
                    bubblePosY = physicsEngine.posY
                }
                lastFrame = now
            }
        }
    }

    // Charge & Pop Controller
    LaunchedEffect(isCharging) {
        if ((!isCharging) || (currentStage != BubbleStage.FLOATING_BUBBLE)) {
            chargeRatio = 0f
            return@LaunchedEffect
        }

        val startTime = System.currentTimeMillis()
        var lastVibe = 0L

        while (isCharging && (currentStage == BubbleStage.FLOATING_BUBBLE)) {
            val elapsed = System.currentTimeMillis() - startTime
            val ratio = (elapsed.toFloat() / chargeThresholdMs).coerceIn(0f, 1f)
            chargeRatio = ratio

            if (elapsed - lastVibe > 120 - (ratio * 70).toLong()) {
                lastVibe = elapsed
                triggerLightHaptic(vibrator)
            }

            if (ratio >= 1f) {
                // FIRST POP TRIGGERED!
                triggerHeavyHaptic(vibrator)
                val centerOffset = Offset(
                    bubblePosX + bubbleSizePx / 2f,
                    bubblePosY + bubbleSizePx / 2f
                )
                popCoordinates = centerOffset

                if (bubbleConfig.popDifficulty.isMazeChallenge) {
                    currentStage = BubbleStage.MAZE_CHALLENGE
                } else {
                    currentStage = BubbleStage.POPPING
                    // Initialize Stage 2: Gravity Icon
                    gravityEngine.initialize(
                        startX = centerOffset.x - iconSizePx / 2f,
                        startY = centerOffset.y - iconSizePx / 2f,
                        minX = screenBounds[0],
                        maxX = screenBounds[1],
                        minY = screenBounds[2],
                        maxY = screenBounds[3]
                    )
                }
                break
            }
            delay(16L)
        }
    }

    // Stage 2: Gravity Falling Icon Physics Loop
    LaunchedEffect(currentStage) {
        if (currentStage != BubbleStage.GRAVITY_FALLING_ICON) return@LaunchedEffect
        var lastFrame = 0L

        while (currentStage == BubbleStage.GRAVITY_FALLING_ICON) {
            if (gravityEngine.isSettled) {
                lastFrame = 0L
                delay(50)
                continue
            }
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val dt = (now - lastFrame) / 1_000_000_000f
                    gravityEngine.update(dt)
                    gravityPosX = gravityEngine.posX
                    gravityPosY = gravityEngine.posY
                    gravityRotation = gravityEngine.rotation
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
                if (canDismissByBackgroundTap) {
                    onDismiss()
                }
            }
            .onGloballyPositioned { coords ->
                val w = coords.size.width.toFloat()
                val h = coords.size.height.toFloat()

                val minX = 24f
                val maxX = w - bubbleSizePx - 24f
                val minY = with(density) { 90.dp.toPx() }
                val maxY = h - bubbleSizePx - with(density) { 100.dp.toPx() }

                screenBounds = floatArrayOf(minX, maxX, minY, maxY)
                physicsEngine.updateBounds(minX, maxX, minY, maxY)
                bubblePosX = physicsEngine.posX
                bubblePosY = physicsEngine.posY
            }
    ) {
        // -------------------------------------------------------------
        // STAGE 1: Floating App Bubble
        // -------------------------------------------------------------
        if (currentStage == BubbleStage.FLOATING_BUBBLE) {
            val scale = (pulseScale.value + (chargeRatio * 0.18f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = bubblePosX
                        translationY = bubblePosY
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(bubbleSizeDp + 36.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Floating bubble for ${app.title}. Hold to pop."
                        onClick(label = "Pop bubble and launch ${app.title}") {
                            triggerHeavyHaptic(vibrator)
                            currentStage = BubbleStage.GRAVITY_FALLING_ICON
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            physicsEngine.setInteracting(true)
                            isCharging = true

                            val startX = physicsEngine.posX
                            val startY = physicsEngine.posY
                            var cumulativeX = 0f
                            var cumulativeY = 0f

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

                                    val targetX = startX + cumulativeX
                                    val targetY = startY + cumulativeY

                                    physicsEngine.updateDragPosition(targetX, targetY)
                                    bubblePosX = physicsEngine.posX
                                    bubblePosY = physicsEngine.posY

                                    velocityTracker.addPosition(currentPointer.uptimeMillis, Offset(down.position.x + cumulativeX, down.position.y + cumulativeY))
                                }
                            } while (event.changes.any { it.pressed })

                            val vel = velocityTracker.calculateVelocity()
                            physicsEngine.releaseDrag(vel.x, vel.y)
                            isCharging = false
                        }
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(bubbleSizeDp)
                        .shadow(
                            elevation = 18.dp,
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
                            contentDescription = app.title,
                            colorFilter = if (isMonochrome) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                        )
                    }

                    if (chargeRatio > 0.01f) {
                        val tertiaryColor = MaterialTheme.colorScheme.tertiary
                        val secondaryColor = MaterialTheme.colorScheme.secondary
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 5.dp.toPx()
                            drawArc(
                                brush = Brush.sweepGradient(listOf(ChartAmber, tertiaryColor, secondaryColor, ChartAmber)),
                                startAngle = -90f,
                                sweepAngle = chargeRatio * 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .offset(y = (-2).dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (chargeRatio > 0f) "Hold to Pop..." else app.title,
                        color = DarkOnBackground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // TRANSITION: Bubble Burst Particle Explosion
        // -------------------------------------------------------------
        if (currentStage == BubbleStage.POPPING && popCoordinates != null) {
            PopExplosion(
                centerX = popCoordinates!!.x,
                centerY = popCoordinates!!.y
            ) {
                currentStage = BubbleStage.GRAVITY_FALLING_ICON
            }
        }

        // -------------------------------------------------------------
        // STAGE 2: Gravity falling & bouncing icon (Interactive DVD Screensaver)
        // -------------------------------------------------------------
        if (currentStage == BubbleStage.GRAVITY_FALLING_ICON) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = gravityPosX
                        translationY = gravityPosY
                        rotationZ = gravityRotation
                    }
                    .size(iconSizeDp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Bouncing icon for ${app.title}. Tap to open."
                        onClick(label = "Launch ${app.title}") {
                            triggerHeavyHaptic(vibrator)
                            onAppLaunch(app)
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            var dragTotal = 0f
                            val startTime = System.currentTimeMillis()

                            val startX = gravityEngine.posX
                            val startY = gravityEngine.posY
                            var cumulativeX = 0f
                            var cumulativeY = 0f

                            gravityEngine.startDrag()

                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, Offset(startX, startY))

                            var isReleased = false

                            do {
                                val event = awaitPointerEvent()
                                val currentPointer = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (currentPointer != null && currentPointer.pressed) {
                                    val dragAmount = currentPointer.positionChange()
                                    currentPointer.consume()

                                    val dx = dragAmount.x
                                    val dy = dragAmount.y
                                    cumulativeX += dx
                                    cumulativeY += dy
                                    dragTotal += hypot(dx.toDouble(), dy.toDouble()).toFloat()

                                    val targetX = startX + cumulativeX
                                    val targetY = startY + cumulativeY

                                    gravityEngine.updateDrag(targetX, targetY)
                                    gravityPosX = gravityEngine.posX
                                    gravityPosY = gravityEngine.posY

                                    velocityTracker.addPosition(currentPointer.uptimeMillis, Offset(gravityPosX, gravityPosY))
                                } else if (currentPointer != null) {
                                    isReleased = true
                                }
                            } while (!isReleased && event.changes.any { it.pressed })

                            val elapsed = System.currentTimeMillis() - startTime
                            val vel = velocityTracker.calculateVelocity()

                            if (dragTotal < 25f && elapsed < 350) {
                                // Direct tap: launch app
                                triggerHeavyHaptic(vibrator)
                                onAppLaunch(app)
                            } else {
                                // Flung / thrown: impart momentum directly from finger velocity
                                gravityEngine.releaseDrag(vel.x, vel.y)
                            }
                        }
                    }
                    .shadow(16.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), MaterialTheme.colorScheme.primaryContainer)
                        ),
                        shape = CircleShape
                    )
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(8.dp)
            ) {
                if (appBitmap != null && !appBitmap.isRecycled) {
                    Image(
                        bitmap = appBitmap.asImageBitmap(),
                        contentDescription = app.title,
                        colorFilter = if (isMonochrome) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // STAGE 3: Mindful Maze Drag Navigation Challenge
        // -------------------------------------------------------------
        if (currentStage == BubbleStage.MAZE_CHALLENGE) {
            MazeChallengeOverlay(
                app = app,
                iconDrawable = iconDrawable,
                onSuccess = { launchedApp ->
                    onAppLaunch(launchedApp)
                },
                onDismiss = onDismiss
            )
        }
    }
}

private fun triggerLightHaptic(vibrator: Vibrator?) {
    vibrator?.takeIf { it.hasVibrator() }?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(15)
        }
    }
}

private fun triggerHeavyHaptic(vibrator: Vibrator?) {
    vibrator?.takeIf { it.hasVibrator() }?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(70)
        }
    }
}
