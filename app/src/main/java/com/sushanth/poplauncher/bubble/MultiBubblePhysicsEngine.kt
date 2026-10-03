package com.sushanth.poplauncher.bubble

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.BubbleBehavior
import com.sushanth.poplauncher.data.model.BubbleConfig
import com.sushanth.poplauncher.data.model.BubbleSpeed
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class BubbleBody(
    val app: AppEntity,
    initialX: Float = 0f,
    initialY: Float = 0f,
    val seedIndex: Int = 0
) {
    var posX by mutableFloatStateOf(initialX)
    var posY by mutableFloatStateOf(initialY)

    var velX: Float = 0f
    var velY: Float = 0f

    var isDragging: Boolean = false
    var dragStartX: Float = 0f
    var dragStartY: Float = 0f
    var idleTimerMs: Long = 0L
}

class MultiBubblePhysicsEngine(
    val apps: List<AppEntity>,
    private var config: BubbleConfig = BubbleConfig(),
    val bubbleSizePx: Float = 200f
) {
    // Collision diameter: distance between centers where bubbles touch
    // bubbleSizePx is the circular icon diameter. We add 10% safety margin so labels and bubbles never overlap.
    val collisionDiameter: Float = bubbleSizePx * 1.10f

    var minBoundX: Float = 0f
        private set
    var maxBoundX: Float = 0f
        private set
    var minBoundY: Float = 0f
        private set
    var maxBoundY: Float = 0f
        private set

    var isInitialized: Boolean = false
        private set

    val bodies: List<BubbleBody> = apps.mapIndexed { index, app ->
        BubbleBody(app = app, seedIndex = index)
    }

    val speedMultiplier: Float
        get() = when (config.speed) {
            BubbleSpeed.SLOW -> 90f
            BubbleSpeed.NORMAL -> 170f
            BubbleSpeed.FAST -> 280f
        }

    fun updateConfig(newConfig: BubbleConfig) {
        this.config = newConfig
    }

    fun updateBounds(minX: Float, maxX: Float, minY: Float, maxY: Float) {
        val safeMinX = minX
        val safeMaxX = max(minX, maxX)
        val safeMinY = minY
        val safeMaxY = max(minY, maxY)

        this.minBoundX = safeMinX
        this.maxBoundX = safeMaxX
        this.minBoundY = safeMinY
        this.maxBoundY = safeMaxY

        if (!isInitialized && safeMaxX > safeMinX && safeMaxY > safeMinY) {
            spawnAllDistributed()
        } else {
            // Keep all bodies clamped within new bounds
            for (body in bodies) {
                if (!body.isDragging) {
                    body.posX = body.posX.coerceIn(minBoundX, maxBoundX)
                    body.posY = body.posY.coerceIn(minBoundY, maxBoundY)
                }
            }
        }
    }

    fun spawnAllDistributed() {
        if (maxBoundX <= minBoundX || maxBoundY <= minBoundY || bodies.isEmpty()) return

        val count = bodies.size
        val availableW = maxBoundX - minBoundX
        val availableH = maxBoundY - minBoundY

        if (count == 1) {
            val b = bodies[0]
            b.posX = minBoundX + availableW * 0.5f
            b.posY = minBoundY + availableH * 0.45f
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = speedMultiplier
            b.velX = cos(angle) * speed
            b.velY = sin(angle) * speed
        } else {
            val centerX = minBoundX + availableW * 0.5f
            val centerY = minBoundY + availableH * 0.45f
            val baseRadius = min(availableW, availableH) * 0.32f

            for (i in 0 until count) {
                val b = bodies[i]
                val angle = (2f * Math.PI.toFloat() * i / count) + (Random.nextFloat() * 0.2f - 0.1f)
                val r = baseRadius * (0.6f + Random.nextFloat() * 0.4f)
                val targetX = (centerX + cos(angle) * r).coerceIn(minBoundX, maxBoundX)
                val targetY = (centerY + sin(angle) * r).coerceIn(minBoundY, maxBoundY)

                b.posX = targetX
                b.posY = targetY

                val speed = speedMultiplier * (0.8f + Random.nextFloat() * 0.4f)
                val moveAngle = angle + (Math.PI.toFloat() / 2f) + (Random.nextFloat() * 0.4f - 0.2f)
                b.velX = cos(moveAngle) * speed
                b.velY = sin(moveAngle) * speed
            }

            // Relaxation step: run 15 passes to ensure NO initial overlap
            for (step in 0 until 15) {
                resolvePairCollisions(updateVelocities = false)
                for (b in bodies) {
                    b.posX = b.posX.coerceIn(minBoundX, maxBoundX)
                    b.posY = b.posY.coerceIn(minBoundY, maxBoundY)
                }
            }
        }

        isInitialized = true
    }

    fun startDrag(id: String) {
        val body = bodies.firstOrNull { it.app.id == id } ?: return
        body.isDragging = true
        body.dragStartX = body.posX
        body.dragStartY = body.posY
        body.velX = 0f
        body.velY = 0f
    }

    fun updateDrag(id: String, newPosX: Float, newPosY: Float) {
        val body = bodies.firstOrNull { it.app.id == id } ?: return
        body.posX = newPosX.coerceIn(minBoundX, maxBoundX)
        body.posY = newPosY.coerceIn(minBoundY, maxBoundY)
    }

    fun releaseDrag(id: String, flingVx: Float, flingVy: Float) {
        val body = bodies.firstOrNull { it.app.id == id } ?: return
        body.isDragging = false
        body.idleTimerMs = System.currentTimeMillis()
        val speed = sqrt(flingVx * flingVx + flingVy * flingVy)
        if (speed > 50f) {
            body.velX = flingVx.coerceIn(-1800f, 1800f)
            body.velY = flingVy.coerceIn(-1800f, 1800f)
        } else {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            body.velX = cos(angle) * (speedMultiplier * 0.8f)
            body.velY = sin(angle) * (speedMultiplier * 0.8f)
        }
    }

    fun update(deltaSeconds: Float) {
        if (!isInitialized || maxBoundX <= minBoundX || maxBoundY <= minBoundY) return
        val safeDelta = if (deltaSeconds.isNaN() || deltaSeconds.isInfinite() || deltaSeconds <= 0f) 0.016f else deltaSeconds
        val dt = min(safeDelta.coerceIn(0.001f, 0.1f), 0.033f)

        // 1. Update drift velocity and individual positions
        for (body in bodies) {
            if (body.isDragging) continue

            // Idle recovery
            val timeSinceRelease = System.currentTimeMillis() - body.idleTimerMs
            if (body.idleTimerMs > 0L && timeSinceRelease < config.idleDelayMs) {
                val ratio = timeSinceRelease.toFloat() / config.idleDelayMs.toFloat()
                body.velX += (body.velX.coerceIn(-10f, 10f)) * ratio
                body.velY += (body.velY.coerceIn(-10f, 10f)) * ratio
            }

            // Behavioral physics
            when (config.behavior) {
                BubbleBehavior.DRIFT -> {
                    val wanderAngle = (Random.nextFloat() - 0.5f) * 0.35f
                    val curSpeed = sqrt(body.velX * body.velX + body.velY * body.velY).coerceAtLeast(speedMultiplier * 0.5f)
                    val curAngle = atan2(body.velY, body.velX) + wanderAngle * dt * 4f
                    body.velX = cos(curAngle) * curSpeed
                    body.velY = sin(curAngle) * curSpeed
                }
                BubbleBehavior.RANDOMIZED -> {
                    if (Random.nextFloat() < 0.03f) {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        val targetSpeed = speedMultiplier * (0.7f + Random.nextFloat() * 0.6f)
                        body.velX = body.velX * 0.75f + cos(angle) * targetSpeed * 0.25f
                        body.velY = body.velY * 0.75f + sin(angle) * targetSpeed * 0.25f
                    }
                }
                BubbleBehavior.CONTINUOUS -> {
                    val curSpeed = sqrt(body.velX * body.velX + body.velY * body.velY)
                    if (curSpeed < speedMultiplier * 0.8f) {
                        body.velX *= 1.04f
                        body.velY *= 1.04f
                    }
                }
            }

            // Global linear damping
            body.velX *= 0.992f
            body.velY *= 0.992f

            // Move
            body.posX += body.velX * dt
            body.posY += body.velY * dt
        }

        // 2. Resolve bubble-to-bubble collisions (2 solver iterations for high physical stability)
        for (iter in 0 until 2) {
            resolvePairCollisions(updateVelocities = (iter == 0))
        }

        // 3. Wall collisions & clamping
        val bounce = config.edgeBounce
        for (body in bodies) {
            if (body.isDragging) continue

            if (body.posX <= minBoundX) {
                body.posX = minBoundX
                body.velX = kotlin.math.abs(body.velX) * bounce
            } else if (body.posX >= maxBoundX) {
                body.posX = maxBoundX
                body.velX = -kotlin.math.abs(body.velX) * bounce
            }

            if (body.posY <= minBoundY) {
                body.posY = minBoundY
                body.velY = kotlin.math.abs(body.velY) * bounce
            } else if (body.posY >= maxBoundY) {
                body.posY = maxBoundY
                body.velY = -kotlin.math.abs(body.velY) * bounce
            }
        }
    }

    fun resolvePairCollisions(updateVelocities: Boolean = true) {
        val bounce = config.edgeBounce
        val n = bodies.size
        for (i in 0 until n) {
            val b1 = bodies[i]
            for (j in i + 1 until n) {
                val b2 = bodies[j]

                val dx = b2.posX - b1.posX
                val dy = b2.posY - b1.posY
                val distSq = dx * dx + dy * dy
                val minDist = collisionDiameter

                if (distSq < minDist * minDist) {
                    val dist = sqrt(distSq.toDouble()).toFloat()
                    val (nx, ny) = if (dist > 0.0001f) {
                        (dx / dist) to (dy / dist)
                    } else {
                        1f to 0f
                    }
                    val overlap = minDist - dist

                    // Position separation
                    if (b1.isDragging && !b2.isDragging) {
                        b2.posX += nx * overlap
                        b2.posY += ny * overlap
                    } else if (b2.isDragging && !b1.isDragging) {
                        b1.posX -= nx * overlap
                        b1.posY -= ny * overlap
                    } else if (!b1.isDragging && !b2.isDragging) {
                        val half = overlap * 0.5f
                        b1.posX -= nx * half
                        b1.posY -= ny * half
                        b2.posX += nx * half
                        b2.posY += ny * half
                    }

                    // Velocity collision response
                    if (updateVelocities) {
                        val rvx = b2.velX - b1.velX
                        val rvy = b2.velY - b1.velY
                        val velAlongNormal = rvx * nx + rvy * ny

                        if (velAlongNormal < 0f) {
                            val impulse = -(1f + bounce) * velAlongNormal * 0.5f

                            if (!b1.isDragging && !b2.isDragging) {
                                b1.velX -= impulse * nx
                                b1.velY -= impulse * ny
                                b2.velX += impulse * nx
                                b2.velY += impulse * ny
                            } else if (b1.isDragging && !b2.isDragging) {
                                b2.velX += impulse * 2f * nx
                                b2.velY += impulse * 2f * ny
                            } else if (b2.isDragging && !b1.isDragging) {
                                b1.velX -= impulse * 2f * nx
                                b1.velY -= impulse * 2f * ny
                            }
                        }
                    }
                }
            }
        }
    }
}
