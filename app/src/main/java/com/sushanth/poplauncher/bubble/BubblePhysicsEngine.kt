package com.sushanth.poplauncher.bubble

import com.sushanth.poplauncher.data.model.BubbleBehavior
import com.sushanth.poplauncher.data.model.BubbleConfig
import com.sushanth.poplauncher.data.model.BubbleSpeed
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
    private var idleTimerMs: Long = 0L

    // Base speed in dp/sec scaled to pixels later
    private val speedMultiplier: Float
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
        idleTimerMs = 0L
    }

    fun setInteracting(interacting: Boolean) {
        if (isUserInteracting != interacting) {
            isUserInteracting = interacting
            if (!interacting) {
                idleTimerMs = System.currentTimeMillis()
            }
        }
    }

    fun updateDragPosition(newX: Float, newY: Float) {
        posX = newX.coerceIn(minBoundX, maxBoundX)
        posY = newY.coerceIn(minBoundY, maxBoundY)
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

        // Check idle delay before regaining full drift velocity
        val timeSinceRelease = System.currentTimeMillis() - idleTimerMs
        val isRecovering = timeSinceRelease < config.idleDelayMs

        if (isRecovering) {
            val ratio = timeSinceRelease.toFloat() / config.idleDelayMs.toFloat()
            velX += (velX.coerceIn(-10f, 10f)) * ratio
            velY += (velY.coerceIn(-10f, 10f)) * ratio
        }

        // Apply behavioral variations (Drift / Random organic wandering)
        when (config.behavior) {
            BubbleBehavior.DRIFT -> {
                // Subtle organic steering perturbation
                val wanderAngle = (Random.nextFloat() - 0.5f) * 0.4f
                val curSpeed = sqrt(velX * velX + velY * velY).coerceAtLeast(speedMultiplier * 0.5f)
                val curAngle = kotlin.math.atan2(velY, velX) + wanderAngle * dt * 5f
                velX = cos(curAngle) * curSpeed
                velY = sin(curAngle) * curSpeed
            }
            BubbleBehavior.RANDOMIZED -> {
                // Occasional randomized velocity kicks
                if (Random.nextFloat() < 0.04f) {
                    val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                    val targetSpeed = speedMultiplier * (0.7f + Random.nextFloat() * 0.6f)
                    velX = velX * 0.7f + cos(angle) * targetSpeed * 0.3f
                    velY = velY * 0.7f + sin(angle) * targetSpeed * 0.3f
                }
            }
            BubbleBehavior.CONTINUOUS -> {
                // Stable continuous motion with slight gentle damping/normalization
                val curSpeed = sqrt(velX * velX + velY * velY)
                if (curSpeed < speedMultiplier * 0.8f) {
                    velX *= 1.05f
                    velY *= 1.05f
                }
            }
        }

        // Apply global linear damping
        velX *= 0.99f
        velY *= 0.99f

        // Update position
        posX += velX * dt
        posY += velY * dt

        // Collision detection with bounds & restitution bouncing
        val bounce = config.edgeBounce

        if (posX <= minBoundX) {
            posX = minBoundX
            velX = kotlin.math.abs(velX) * bounce
        } else if (posX >= maxBoundX) {
            posX = maxBoundX
            velX = -kotlin.math.abs(velX) * bounce
        }

        if (posY <= minBoundY) {
            posY = minBoundY
            velY = kotlin.math.abs(velY) * bounce
        } else if (posY >= maxBoundY) {
            posY = maxBoundY
            velY = -kotlin.math.abs(velY) * bounce
        }
    }
}
