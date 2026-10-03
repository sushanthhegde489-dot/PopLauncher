package com.sushanth.poplauncher.bubble

import kotlin.math.abs
import kotlin.random.Random

class GravityIconPhysicsEngine(
    val gravity: Float = 1200f,
    val bounceCoefficient: Float = 0.88f,
    val frictionX: Float = 0.995f,
    val frictionY: Float = 0.998f,
    val minSpeedX: Float = 120f
) {
    var posX: Float = 0f
        private set
    var posY: Float = 0f
        private set

    var velX: Float = 0f
        private set
    var velY: Float = 0f
        private set

    var rotation: Float = 0f
        private set
    var angularVelocity: Float = 0f
        private set

    private var minBoundX: Float = 0f
    private var maxBoundX: Float = 0f
    private var minBoundY: Float = 0f
    private var maxBoundY: Float = 0f

    var isDragging: Boolean = false
        private set

    private var _isSettled: Boolean = false
    val isSettled: Boolean
        get() = _isSettled

    fun initialize(
        startX: Float,
        startY: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ) {
        this.minBoundX = minX
        this.maxBoundX = maxOf(minX, maxX)
        this.minBoundY = minY
        this.maxBoundY = maxOf(minY, maxY)

        this.posX = startX.coerceIn(this.minBoundX, this.maxBoundX)
        this.posY = startY.coerceIn(this.minBoundY, this.maxBoundY)

        // Initial burst velocity from bubble pop
        val dirX = if (Random.nextBoolean()) 1f else -1f
        this.velX = dirX * (150f + Random.nextFloat() * 150f)
        this.velY = -250f - Random.nextFloat() * 150f
        this.rotation = 0f
        this.angularVelocity = dirX * (120f + Random.nextFloat() * 120f)
        this.isDragging = false
        this._isSettled = false
    }

    fun startDrag() {
        isDragging = true
        velX = 0f
        velY = 0f
        angularVelocity = 0f
        rotation = 0f
    }

    fun updateDrag(newX: Float, newY: Float) {
        posX = newX.coerceIn(minBoundX, maxBoundX)
        posY = newY.coerceIn(minBoundY, maxBoundY)
        rotation = 0f
    }

    fun releaseDrag(releaseVx: Float, releaseVy: Float) {
        isDragging = false
        _isSettled = false
        val vx = releaseVx.coerceIn(-3000f, 3000f)
        val vy = releaseVy.coerceIn(-3000f, 3000f)

        velX = vx
        velY = vy
        angularVelocity = (velX * 0.15f).coerceIn(-360f, 360f)
    }

    fun update(deltaSeconds: Float) {
        if (isDragging || _isSettled) return
        val safeDelta = if (deltaSeconds.isNaN() || deltaSeconds.isInfinite() || deltaSeconds <= 0f) 0.016f else deltaSeconds
        val dt = safeDelta.coerceIn(0.001f, 0.05f)

        // Apply gravity and air friction
        velY += gravity * dt
        velX *= frictionX
        velY *= frictionY

        // Update positions
        posX += velX * dt
        posY += velY * dt
        rotation += angularVelocity * dt

        // Floor collision & restitution bouncing
        if (posY >= maxBoundY) {
            posY = maxBoundY
            if (abs(velY) > 60f) {
                velY = -abs(velY) * bounceCoefficient
                angularVelocity = -angularVelocity * 0.5f + (velX * 0.2f)
            } else {
                velY = 0f
                // Apply surface friction when sliding on floor
                velX *= 0.88f
                angularVelocity *= 0.8f
                // Icon has come to rest — stop the physics loop
                if (abs(velX) < 10f) {
                    velX = 0f
                    angularVelocity = 0f
                    _isSettled = true
                }
            }
        }

        // Ceiling collision
        if (posY <= minBoundY) {
            posY = minBoundY
            if (abs(velY) > 60f) {
                velY = abs(velY) * bounceCoefficient
            } else {
                velY = 0f
            }
        }

        // Left wall collision
        if (posX <= minBoundX) {
            posX = minBoundX
            velX = abs(velX) * bounceCoefficient
            angularVelocity = (velY * 0.2f)
        }

        // Right wall collision
        if (posX >= maxBoundX) {
            posX = maxBoundX
            velX = -abs(velX) * bounceCoefficient
            angularVelocity = (-velY * 0.2f)
        }
    }
}
