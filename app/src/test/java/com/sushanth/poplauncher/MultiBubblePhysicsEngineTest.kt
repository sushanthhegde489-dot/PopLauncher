package com.sushanth.poplauncher

import com.sushanth.poplauncher.bubble.MultiBubblePhysicsEngine
import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.BubbleConfig
import com.sushanth.poplauncher.data.model.BubbleSpeed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class MultiBubblePhysicsEngineTest {

    private fun createApp(id: String, name: String): AppEntity {
        return AppEntity(
            id = id,
            title = name,
            packageName = "com.test.$id",
            launchIntent = null
        )
    }

    @Test
    fun testBubblesStayWithinBounds() {
        val apps = listOf(
            createApp("app1", "App One"),
            createApp("app2", "App Two"),
            createApp("app3", "App Three")
        )
        val engine = MultiBubblePhysicsEngine(apps, BubbleConfig(speed = BubbleSpeed.FAST), bubbleSizePx = 100f)
        val minX = 20f
        val maxX = 500f
        val minY = 50f
        val maxY = 800f
        engine.updateBounds(minX, maxX, minY, maxY)

        for (i in 0 until 120) {
            engine.update(1f / 60f)
            for (body in engine.bodies) {
                assertTrue("X ${body.posX} >= minX $minX", body.posX >= minX - 0.1f)
                assertTrue("X ${body.posX} <= maxX $maxX", body.posX <= maxX + 0.1f)
                assertTrue("Y ${body.posY} >= minY $minY", body.posY >= minY - 0.1f)
                assertTrue("Y ${body.posY} <= maxY $maxY", body.posY <= maxY + 0.1f)
            }
        }
    }

    @Test
    fun testTwoBubblesBounceOffEachOther() {
        val apps = listOf(
            createApp("app1", "App One"),
            createApp("app2", "App Two")
        )
        val engine = MultiBubblePhysicsEngine(apps, BubbleConfig(speed = BubbleSpeed.NORMAL), bubbleSizePx = 100f)
        engine.updateBounds(0f, 600f, 0f, 600f)

        val b1 = engine.bodies[0]
        val b2 = engine.bodies[1]

        b1.posX = 150f
        b1.posY = 300f
        b1.velX = 100f
        b1.velY = 0f

        b2.posX = 350f
        b2.posY = 300f
        b2.velX = -100f
        b2.velY = 0f

        var bounced = false
        for (i in 0 until 120) {
            engine.update(1f / 60f)
            val dx = b2.posX - b1.posX
            val dy = b2.posY - b1.posY
            val dist = sqrt(dx * dx + dy * dy)
            assertTrue("Bubbles should never significantly penetrate below collision diameter. dist=$dist", dist >= engine.collisionDiameter - 1f)

            if (b1.velX < 0f && b2.velX > 0f) {
                bounced = true
            }
        }

        assertTrue("Bubbles should have collided and reversed velocities", bounced)
    }

    @Test
    fun testDraggedBubblePushesOtherBubbleAway() {
        val apps = listOf(
            createApp("app1", "App One"),
            createApp("app2", "App Two")
        )
        val engine = MultiBubblePhysicsEngine(apps, BubbleConfig(), bubbleSizePx = 100f)
        engine.updateBounds(0f, 800f, 0f, 800f)

        val b1 = engine.bodies[0]
        val b2 = engine.bodies[1]

        b2.posX = 400f
        b2.posY = 400f

        engine.startDrag(b1.app.id)
        engine.updateDrag(b1.app.id, 380f, 400f)

        engine.update(1f / 60f)

        val dx = b2.posX - b1.posX
        val dy = b2.posY - b1.posY
        val dist = sqrt(dx * dx + dy * dy)
        assertTrue("b2 should have been displaced so that distance >= collisionDiameter. dist=$dist", dist >= engine.collisionDiameter - 1f)
    }

    @Test
    fun testZeroDistanceCollisionDoesNotProduceNaN() {
        val apps = listOf(
            createApp("app1", "App One"),
            createApp("app2", "App Two")
        )
        val engine = MultiBubblePhysicsEngine(apps, BubbleConfig(), bubbleSizePx = 100f)
        engine.updateBounds(0f, 800f, 0f, 800f)

        val b1 = engine.bodies[0]
        val b2 = engine.bodies[1]

        b1.posX = 300f
        b1.posY = 300f
        b1.velX = 0f
        b1.velY = 0f

        b2.posX = 300f
        b2.posY = 300f
        b2.velX = 0f
        b2.velY = 0f

        engine.resolvePairCollisions(updateVelocities = true)

        assertFalse("b1.posX must not be NaN", b1.posX.isNaN())
        assertFalse("b1.posY must not be NaN", b1.posY.isNaN())
        assertFalse("b2.posX must not be NaN", b2.posX.isNaN())
        assertFalse("b2.posY must not be NaN", b2.posY.isNaN())
        assertFalse("b1.velX must not be NaN", b1.velX.isNaN())
        assertFalse("b2.velX must not be NaN", b2.velX.isNaN())
    }

    @Test
    fun testIsSettledWhenVelocitiesAreLow() {
        val apps = listOf(
            createApp("app1", "App One")
        )
        val engine = MultiBubblePhysicsEngine(apps, BubbleConfig(), bubbleSizePx = 100f)
        engine.updateBounds(0f, 800f, 0f, 800f)

        val b1 = engine.bodies[0]
        b1.velX = 0.01f
        b1.velY = 0.01f

        assertTrue("Engine should be settled when velocities are near zero", engine.isSettled)

        b1.velX = 50f
        assertFalse("Engine should not be settled when velocity is high", engine.isSettled)
    }

    @Test
    fun testObstacleDeflectsBubble() {
        val apps = listOf(
            createApp("app1", "App One")
        )
        val engine = MultiBubblePhysicsEngine(apps, BubbleConfig(speed = BubbleSpeed.NORMAL), bubbleSizePx = 100f)
        engine.updateBounds(0f, 1000f, 0f, 1000f)

        // Place obstacle at (500, 500) with radius 50
        engine.updateObstacle(500f, 500f, 50f, 0f, 0f)

        val b1 = engine.bodies[0]
        // Place bubble moving directly right towards the obstacle
        b1.posX = 350f
        b1.posY = 500f - 50f // Center is at 500
        b1.velX = 200f
        b1.velY = 0f

        var deflected = false
        for (i in 0 until 120) {
            engine.update(1f / 60f)
            if (b1.velX < 0f) {
                deflected = true
                break
            }
        }

        assertTrue("Bubble should have deflected off the rigid obstacle and reversed X velocity", deflected)
    }
}
