package com.sushanth.poplauncher

import com.sushanth.poplauncher.bubble.GravityIconPhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GravityIconPhysicsEngineTest {

    @Test
    fun testGravityAcceleratesDownwards() {
        val engine = GravityIconPhysicsEngine(gravity = 1500f, bounceCoefficient = 0.6f)
        engine.initialize(
            startX = 200f,
            startY = 300f,
            minX = 0f,
            maxX = 500f,
            minY = 0f,
            maxY = 800f
        )

        val initialY = engine.posY
        // Run physics forward
        val dt = 1f / 60f
        for (i in 0 until 80) {
            engine.update(dt)
        }

        assertTrue("Icon should fall downwards due to gravity", engine.posY > initialY)
    }

    @Test
    fun testElasticBounceAtFloor() {
        val engine = GravityIconPhysicsEngine(gravity = 1500f, bounceCoefficient = 0.7f)
        val floorY = 600f
        engine.initialize(
            startX = 200f,
            startY = 550f, // Close to floor
            minX = 0f,
            maxX = 500f,
            minY = 0f,
            maxY = floorY
        )

        var hitFloorAndBounced = false
        val dt = 1f / 60f
        for (i in 0 until 120) {
            engine.update(dt)
            if (engine.posY >= floorY - 0.01f && engine.velY < 0f) {
                // Velocity reversed upwards after hitting floor!
                hitFloorAndBounced = true
                break
            }
        }

        assertTrue("Icon must bounce elastically off floor with negative velocity", hitFloorAndBounced)
    }

    @Test
    fun testIconEventuallySettles() {
        val engine = GravityIconPhysicsEngine(gravity = 1500f, bounceCoefficient = 0.92f)
        val floorY = 600f
        engine.initialize(
            startX = 200f,
            startY = 200f,
            minX = 0f,
            maxX = 500f,
            minY = 0f,
            maxY = floorY
        )

        // Run simulation long enough for the icon to dissipate kinetic energy and settle
        val dt = 1f / 60f
        for (i in 0 until 1800) { // ~30 seconds
            engine.update(dt)
            if (engine.isSettled) break
        }

        // Icon should settle on the floor after sufficient time
        assertTrue("Icon must eventually settle to rest", engine.isSettled)
        assertTrue("Icon Y position must remain bounded when settled", engine.posY in 0f..floorY)
        assertTrue("Icon X position must remain bounded when settled", engine.posX in 0f..500f)
    }

    @Test
    fun testDragAndThrowInteraction() {
        val engine = GravityIconPhysicsEngine(gravity = 1500f)
        engine.initialize(
            startX = 200f,
            startY = 200f,
            minX = 0f,
            maxX = 500f,
            minY = 0f,
            maxY = 600f
        )

        // Start drag
        engine.startDrag()
        assertTrue("Engine must be in dragging state", engine.isDragging)

        // Drag to a new position
        engine.updateDrag(300f, 150f)
        assertEquals(300f, engine.posX, 0.01f)
        assertEquals(150f, engine.posY, 0.01f)

        // Release drag with fling / throw velocity
        engine.releaseDrag(releaseVx = 450f, releaseVy = -600f)
        org.junit.Assert.assertFalse("Engine should exit dragging state after release", engine.isDragging)
        assertEquals(450f, engine.velX, 0.01f)
        assertEquals(-600f, engine.velY, 0.01f)

        // Step physics and verify it moves according to throw
        val dt = 1f / 60f
        engine.update(dt)
        assertTrue("Icon should move in throw direction X", engine.posX > 300f)
        assertTrue("Icon should move upwards initially from throw velocity Y", engine.posY < 150f)
    }
    @Test
    fun testTinyBoundsDoNotThrow() {
        val engine = GravityIconPhysicsEngine()

        // Simulates a very small/compact layout where normal margins
        // would otherwise make the max bounds smaller than the min bounds.
        engine.initialize(
            startX = 20f,
            startY = 20f,
            minX = 0f,
            maxX = -10f,
            minY = 90f,
            maxY = 40f
        )

        assertEquals(0f, engine.posX, 0.01f)
        assertEquals(90f, engine.posY, 0.01f)
        engine.update(1f / 60f)
        assertTrue("Position must stay finite after tiny-bound update", engine.posX.isFinite() && engine.posY.isFinite())
    }

}
