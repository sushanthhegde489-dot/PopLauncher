package com.sushanth.poplauncher.bubble

import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.BubbleBehavior
import com.sushanth.poplauncher.data.model.BubbleConfig
import kotlin.math.abs
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
    var posX: Float = initialX
    var posY: Float = initialY

    var velX: Float = 0f
    var velY: Float = 0f

    var isDragging: Boolean = false
    var idleElapsedSeconds: Float = 0f
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

    var obstacleX: Float = 0f
        private set
    var obstacleY: Float = 0f
        private set
    var obstacleRadius: Float = 0f
        private set
    var obstacleVx: Float = 0f
        private set
    var obstacleVy: Float = 0f
        private set
    var hasObstacle: Boolean = false
        private set

    fun updateObstacle(centerX: Float, centerY: Float, radius: Float, vx: Float = 0f, vy: Float = 0f) {
        this.obstacleX = centerX
        this.obstacleY = centerY
        this.obstacleRadius = radius
        this.obstacleVx = vx
        this.obstacleVy = vy
        this.hasObstacle = radius > 0f
    }

    fun clearObstacle() {
        this.hasObstacle = false
        this.obstacleRadius = 0f
    }

    val bodies: List<BubbleBody> = apps.mapIndexed { index, app ->
        BubbleBody(app = app, seedIndex = index)
    }

    val isSettled: Boolean
        get() {
            if (!isInitialized || bodies.isEmpty()) return false
            if (bodies.any { it.isDragging }) return false
            var totalKe = 0f
            for (b in bodies) {
                totalKe += (b.velX * b.velX + b.velY * b.velY)
                if (totalKe >= 0.05f) return false
            }
            return true
        }

    val speedMultiplier: Float
        get() = 170f * config.speed.speedMultiplier

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
        body.velX = 0f
        body.velY = 0f
    }

    fun updateDrag(id: String, newPosX: Float, newPosY: Float) {
        val body = bodies.firstOrNull { it.app.id == id } ?: return
        var targetX = newPosX.coerceIn(minBoundX, maxBoundX)
        var targetY = newPosY.coerceIn(minBoundY, maxBoundY)

        if (hasObstacle && obstacleRadius > 0f) {
            val centerBx = targetX + (bubbleSizePx / 2f)
            val centerBy = targetY + (bubbleSizePx / 2f)
            val dx = centerBx - obstacleX
            val dy = centerBy - obstacleY
            val distSq = dx * dx + dy * dy
            val hitDist = (collisionDiameter * 0.48f) + obstacleRadius
            if (distSq < hitDist * hitDist) {
                val dist = sqrt(distSq).coerceAtLeast(0.001f)
                val nx = dx / dist
                val ny = dy / dist
                val overlap = hitDist - dist
                targetX += nx * overlap
                targetY += ny * overlap
            }
        }

        body.posX = targetX.coerceIn(minBoundX, maxBoundX)
        body.posY = targetY.coerceIn(minBoundY, maxBoundY)
    }

    fun releaseDrag(id: String, flingVx: Float, flingVy: Float) {
        val body = bodies.firstOrNull { it.app.id == id } ?: return
        body.isDragging = false
        body.idleElapsedSeconds = 0f
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

            body.idleElapsedSeconds += dt

            // Smooth idle recovery after drag release
            val idleDelaySeconds = (config.idleDelayMs / 1000f).coerceAtLeast(0.001f)
            val recoveryFactor = if (config.idleDelayMs > 0L && body.idleElapsedSeconds < idleDelaySeconds) {
                (body.idleElapsedSeconds / idleDelaySeconds).coerceIn(0.2f, 1f)
            } else {
                1f
            }

            // Behavioral physics
            when (config.behavior) {
                BubbleBehavior.DRIFT -> {
                    val wanderAngle = (Random.nextFloat() - 0.5f) * 0.35f
                    val minDriftSpeed = speedMultiplier * 0.5f * recoveryFactor
                    val curSpeed = sqrt(body.velX * body.velX + body.velY * body.velY).coerceAtLeast(minDriftSpeed)
                    val curAngle = atan2(body.velY, body.velX) + wanderAngle * dt * 4f
                    body.velX = cos(curAngle) * curSpeed
                    body.velY = sin(curAngle) * curSpeed
                }
                BubbleBehavior.RANDOMIZED -> {
                    if (Random.nextFloat() < 0.03f) {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        val targetSpeed = speedMultiplier * (0.7f + Random.nextFloat() * 0.6f) * recoveryFactor
                        body.velX = body.velX * 0.75f + cos(angle) * targetSpeed * 0.25f
                        body.velY = body.velY * 0.75f + sin(angle) * targetSpeed * 0.25f
                    }
                }
                BubbleBehavior.CONTINUOUS -> {
                    val curSpeed = sqrt(body.velX * body.velX + body.velY * body.velY)
                    if (curSpeed < speedMultiplier * 0.8f * recoveryFactor) {
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

        // 2b. Solid kinematic obstacle collision (Search icon hits and deflects app bubbles)
        if (hasObstacle && obstacleRadius > 0f) {
            val bubbleRadius = (collisionDiameter / 2f)
            val hitDist = bubbleRadius + obstacleRadius
            val hitDistSq = hitDist * hitDist
            for (body in bodies) {
                if (body.isDragging) continue
                val centerBx = body.posX + (bubbleSizePx / 2f)
                val centerBy = body.posY + (bubbleSizePx / 2f)
                val dx = centerBx - obstacleX
                val dy = centerBy - obstacleY
                val distSq = dx * dx + dy * dy
                if (distSq < hitDistSq) {
                    val dist = sqrt(distSq).coerceAtLeast(0.001f)
                    val nx = dx / dist
                    val ny = dy / dist
                    val overlap = hitDist - dist

                    body.posX += nx * overlap
                    body.posY += ny * overlap

                    val relVx = body.velX - obstacleVx
                    val relVy = body.velY - obstacleVy
                    val vDot = relVx * nx + relVy * ny

                    if (vDot < 0f) {
                        val bounce = (config.edgeBounce * 1.05f).coerceIn(0.75f, 1.15f)
                        body.velX -= (1f + bounce) * vDot * nx
                        body.velY -= (1f + bounce) * vDot * ny
                    }

                    val obstacleSpeed = sqrt(obstacleVx * obstacleVx + obstacleVy * obstacleVy)
                    if (obstacleSpeed > 5f) {
                        body.velX += obstacleVx * 0.4f
                        body.velY += obstacleVy * 0.4f
                    }
                }
            }
        }

        // 3. Wall collisions & clamping
        val bounce = config.edgeBounce
        for (body in bodies) {
            if (body.isDragging) continue

            if (body.posX <= minBoundX) {
                body.posX = minBoundX
                body.velX = abs(body.velX) * bounce
            } else if (body.posX >= maxBoundX) {
                body.posX = maxBoundX
                body.velX = -abs(body.velX) * bounce
            }

            if (body.posY <= minBoundY) {
                body.posY = minBoundY
                body.velY = abs(body.velY) * bounce
            } else if (body.posY >= maxBoundY) {
                body.posY = maxBoundY
                body.velY = -abs(body.velY) * bounce
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
                    var curDx = dx
                    var curDy = dy
                    var dist = sqrt(distSq.toDouble()).toFloat()
                    if (dist < 0.001f) {
                        curDx = (Random.nextFloat() - 0.5f) * 0.2f
                        if (curDx == 0f) curDx = 0.1f
                        curDy = (Random.nextFloat() - 0.5f) * 0.2f
                        dist = sqrt((curDx * curDx + curDy * curDy).toDouble()).toFloat().coerceAtLeast(0.0001f)
                    }
                    val nx = curDx / dist
                    val ny = curDy / dist
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
