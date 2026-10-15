package com.sushanth.poplauncher.bubble

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

class BubblePhysicsEngine(
    private var config: BubbleConfig = BubbleConfig()
) {
    var posX: Float = 0f
        private set
    var posY: Float = 0f
        private set

    var velX: Float = 0f
        private set
    var velY: Float = 0f
        private set

    private var minBoundX: Float = 0f
    private var maxBoundX: Float = 0f
    private var minBoundY: Float = 0f
    private var maxBoundY: Float = 0f

    private var isInitialized: Boolean = false
    private var isUserInteracting: Boolean = false
    private var idleElapsedSeconds: Float = 0f

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

    // Base speed in dp/sec scaled by BubbleSpeed multiplier
    private val speedMultiplier: Float
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
            spawnAtRandom()
        } else {
            // Keep existing position clamped within new bounds
            posX = posX.coerceIn(minBoundX, maxBoundX)
            posY = posY.coerceIn(minBoundY, maxBoundY)
        }
    }

    fun spawnAtRandom() {
        if (maxBoundX <= minBoundX || maxBoundY <= minBoundY) return

        posX = minBoundX + Random.nextFloat() * (maxBoundX - minBoundX)
        posY = minBoundY + Random.nextFloat() * (maxBoundY - minBoundY)

        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val speed = speedMultiplier * (0.8f + Random.nextFloat() * 0.4f)

        velX = cos(angle) * speed
        velY = sin(angle) * speed
        isInitialized = true
        isUserInteracting = false
        idleElapsedSeconds = config.idleDelayMs / 1000f
    }

    fun setInteracting(interacting: Boolean) {
        if (isUserInteracting != interacting) {
            isUserInteracting = interacting
            if (!interacting) {
                idleElapsedSeconds = 0f
            }
        }
    }

    fun updateDragPosition(newX: Float, newY: Float) {
        var targetX = newX.coerceIn(minBoundX, maxBoundX)
        var targetY = newY.coerceIn(minBoundY, maxBoundY)

        if (hasObstacle && obstacleRadius > 0f) {
            val centerBx = targetX + 100f // approx bubble radius ~100f
            val centerBy = targetY + 100f
            val dx = centerBx - obstacleX
            val dy = centerBy - obstacleY
            val distSq = dx * dx + dy * dy
            val hitDist = 100f + obstacleRadius
            if (distSq < hitDist * hitDist) {
                val dist = sqrt(distSq).coerceAtLeast(0.001f)
                val nx = dx / dist
                val ny = dy / dist
                val overlap = hitDist - dist
                targetX += nx * overlap
                targetY += ny * overlap
            }
        }

        posX = targetX.coerceIn(minBoundX, maxBoundX)
        posY = targetY.coerceIn(minBoundY, maxBoundY)
    }

    fun releaseDrag(flingVx: Float, flingVy: Float) {
        setInteracting(false)
        val speed = sqrt(flingVx * flingVx + flingVy * flingVy)
        if (speed > 50f) {
            velX = flingVx.coerceIn(-1500f, 1500f)
            velY = flingVy.coerceIn(-1500f, 1500f)
        }
    }

    fun update(deltaSeconds: Float) {
        if (!isInitialized || maxBoundX <= minBoundX || maxBoundY <= minBoundY) return
        val safeDelta = if (deltaSeconds.isNaN() || deltaSeconds.isInfinite() || deltaSeconds <= 0f) 0.016f else deltaSeconds
        // Clamp to a strict maximum to prevent physics bounds penetration during heavy system load/GC pauses
        val dt = min(safeDelta.coerceIn(0.001f, 0.1f), 0.033f)

        if (isUserInteracting) {
            // Strong damping while user targets or holds the bubble
            velX *= 0.15f
            velY *= 0.15f
            return
        }

        idleElapsedSeconds += dt

        // Smoothly ramp back up to target drift speed after user releases the bubble
        val idleDelaySeconds = (config.idleDelayMs / 1000f).coerceAtLeast(0.001f)
        val recoveryFactor = if (config.idleDelayMs > 0L && idleElapsedSeconds < idleDelaySeconds) {
            (idleElapsedSeconds / idleDelaySeconds).coerceIn(0.15f, 1f)
        } else {
            1f
        }

        // Apply behavioral variations (Drift / Random organic wandering)
        when (config.behavior) {
            BubbleBehavior.DRIFT -> {
                val wanderAngle = (Random.nextFloat() - 0.5f) * 0.4f
                val minDriftSpeed = speedMultiplier * 0.5f * recoveryFactor
                val curSpeed = sqrt(velX * velX + velY * velY).coerceAtLeast(minDriftSpeed)
                val curAngle = atan2(velY, velX) + wanderAngle * dt * 5f
                velX = cos(curAngle) * curSpeed
                velY = sin(curAngle) * curSpeed
            }
            BubbleBehavior.RANDOMIZED -> {
                if (Random.nextFloat() < 0.04f) {
                    val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                    val targetSpeed = speedMultiplier * (0.7f + Random.nextFloat() * 0.6f) * recoveryFactor
                    velX = velX * 0.7f + cos(angle) * targetSpeed * 0.3f
                    velY = velY * 0.7f + sin(angle) * targetSpeed * 0.3f
                }
            }
            BubbleBehavior.CONTINUOUS -> {
                val curSpeed = sqrt(velX * velX + velY * velY)
                if (curSpeed < speedMultiplier * 0.8f * recoveryFactor) {
                    if (curSpeed < 1f) {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        velX = cos(angle) * speedMultiplier * 0.3f * recoveryFactor
                        velY = sin(angle) * speedMultiplier * 0.3f * recoveryFactor
                    } else {
                        velX *= 1.05f
                        velY *= 1.05f
                    }
                }
            }
        }

        // Apply global linear damping
        velX *= 0.99f
        velY *= 0.99f

        // Update position
        posX += velX * dt
        posY += velY * dt

        // Collision with kinematic obstacle (search icon)
        if (hasObstacle && obstacleRadius > 0f) {
            val centerBx = posX + 100f
            val centerBy = posY + 100f
            val dx = centerBx - obstacleX
            val dy = centerBy - obstacleY
            val distSq = dx * dx + dy * dy
            val hitDist = 100f + obstacleRadius
            if (distSq < hitDist * hitDist) {
                val dist = sqrt(distSq).coerceAtLeast(0.001f)
                val nx = dx / dist
                val ny = dy / dist
                val overlap = hitDist - dist

                posX += nx * overlap
                posY += ny * overlap

                val relVx = velX - obstacleVx
                val relVy = velY - obstacleVy
                val vDot = relVx * nx + relVy * ny

                if (vDot < 0f) {
                    val bounce = (config.edgeBounce * 1.05f).coerceIn(0.75f, 1.15f)
                    velX -= (1f + bounce) * vDot * nx
                    velY -= (1f + bounce) * vDot * ny
                }

                val obstacleSpeed = sqrt(obstacleVx * obstacleVx + obstacleVy * obstacleVy)
                if (obstacleSpeed > 5f) {
                    velX += obstacleVx * 0.4f
                    velY += obstacleVy * 0.4f
                }
            }
        }

        // Collision detection with bounds & restitution bouncing
        val bounce = config.edgeBounce

        if (posX <= minBoundX) {
            posX = minBoundX
            velX = abs(velX) * bounce
        } else if (posX >= maxBoundX) {
            posX = maxBoundX
            velX = -abs(velX) * bounce
        }

        if (posY <= minBoundY) {
            posY = minBoundY
            velY = abs(velY) * bounce
        } else if (posY >= maxBoundY) {
            posY = maxBoundY
            velY = -abs(velY) * bounce
        }
    }
}
