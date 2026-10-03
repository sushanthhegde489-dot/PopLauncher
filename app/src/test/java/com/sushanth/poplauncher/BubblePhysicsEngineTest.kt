package com.sushanth.poplauncher

import com.sushanth.poplauncher.bubble.BubblePhysicsEngine
import com.sushanth.poplauncher.data.model.BubbleBehavior
import com.sushanth.poplauncher.data.model.BubbleConfig
import com.sushanth.poplauncher.data.model.BubbleSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BubblePhysicsEngineTest {

    @Test
    fun testPhysicsStaysWithinBounds() {
        val config = BubbleConfig(speed = BubbleSpeed.NORMAL, behavior = BubbleBehavior.CONTINUOUS)
        val engine = BubblePhysicsEngine(config)

        val minX = 20f
        val maxX = 400f
        val minY = 50f
        val maxY = 700f

        engine.updateBounds(minX, maxX, minY, maxY)

        // Run simulation for 200 frames (approx 3.3 seconds at 60fps)
        val dt = 1f / 60f
        for (i in 0 until 200) {
            engine.update(dt)
            assertTrue("X ${engine.posX} should be >= minX $minX", engine.posX >= minX - 0.01f)
            assertTrue("X ${engine.posX} should be <= maxX $maxX", engine.posX <= maxX + 0.01f)
            assertTrue("Y ${engine.posY} should be >= minY $minY", engine.posY >= minY - 0.01f)
            assertTrue("Y ${engine.posY} should be <= maxY $maxY", engine.posY <= maxY + 0.01f)
        }
    }

    @Test
    fun testUserInteractionSlowsDownBubble() {
        val config = BubbleConfig(speed = BubbleSpeed.FAST, behavior = BubbleBehavior.CONTINUOUS)
        val engine = BubblePhysicsEngine(config)
        engine.updateBounds(0f, 500f, 0f, 500f)

        // Normal movement has significant velocity
        val initialSpeed = abs(engine.velX) + abs(engine.velY)
        assertTrue("Initial speed should be non-zero", initialSpeed > 50f)

        // When user touches/interacts with bubble
        engine.setInteracting(true)
        val dt = 1f / 60f
        for (i in 0 until 10) {
            engine.update(dt)
        }

        val dampedSpeed = abs(engine.velX) + abs(engine.velY)
        assertTrue("Damped speed ($dampedSpeed) should be much lower than initial speed ($initialSpeed)", dampedSpeed < initialSpeed * 0.2f)
    }

    @Test
    fun testPhysicsClampingUnderHighDeltaTime() {
        val config = BubbleConfig(speed = BubbleSpeed.FAST, behavior = BubbleBehavior.CONTINUOUS)
        val engine = BubblePhysicsEngine(config)
        
        val minX = 0f
        val maxX = 100f
        val minY = 0f
        val maxY = 100f
        engine.updateBounds(minX, maxX, minY, maxY)

        // Simulate a massive 5 second frame drop lag spike
        engine.update(5.0f)
        
        // Physics clamp should prevent the bubble from penetrating way past bounds
        assertTrue("X ${engine.posX} should be clamped within bounds + small margin", engine.posX in minX - 10f..maxX + 10f)
        assertTrue("Y ${engine.posY} should be clamped within bounds + small margin", engine.posY in minY - 10f..maxY + 10f)
    }
}
