package com.sushanth.poplauncher.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import com.sushanth.poplauncher.ui.theme.ChartAmber
import com.sushanth.poplauncher.ui.theme.ChartCoral
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticlePool {
    private val pool = mutableListOf<Particle>()

    fun getParticle(angle: Float, speed: Float, color: Color, initialRadius: Float): Particle {
        synchronized(pool) {
            return if (pool.isNotEmpty()) {
                val p = pool.removeAt(pool.lastIndex)
                p.angle = angle
                p.speed = speed
                p.color = color
                p.initialRadius = initialRadius
                p
            } else {
                Particle(angle, speed, color, initialRadius)
            }
        }
    }

    fun recycle(particle: Particle) {
        synchronized(pool) {
            pool.add(particle)
        }
    }

    fun recycleAll(particles: List<Particle>) {
        synchronized(pool) {
            pool.addAll(particles)
        }
    }
}

val particlePool = ParticlePool()

class Particle(
    var angle: Float,
    var speed: Float,
    var color: Color,
    var initialRadius: Float
)

@Composable
fun PopExplosion(
    centerX: Float,
    centerY: Float,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val secondary = androidx.compose.material3.MaterialTheme.colorScheme.secondary
    val tertiary = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
    val primaryContainer = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer

    val particles = remember(primary, secondary, tertiary) {
        val colors = listOf(primary, secondary, tertiary, primaryContainer, ChartAmber, ChartCoral)
        List(28) {
            particlePool.getParticle(
                angle = Random.nextFloat() * 2f * Math.PI.toFloat(),
                speed = 120f + Random.nextFloat() * 280f,
                color = colors[Random.nextInt(colors.size)],
                initialRadius = 5f + Random.nextFloat() * 7f
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 350, easing = LinearEasing)
        )
        onComplete()
    }

    DisposableEffect(particles) {
        onDispose {
            particlePool.recycleAll(particles)
        }
    }

    val currentProgress = progress.value
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(centerX, centerY)

        // Expanding shockwave ring
        val ringRadius = currentProgress * 140f
        val ringAlpha = (1f - currentProgress).coerceIn(0f, 1f)
        drawCircle(
            color = primary.copy(alpha = ringAlpha * 0.7f),
            radius = ringRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f * (1f - currentProgress))
        )

        // Burst particles
        particles.forEach { p ->
            val dist = p.speed * currentProgress
            val x = centerX + cos(p.angle) * dist
            val y = centerY + sin(p.angle) * dist
            val alpha = (1f - currentProgress).coerceIn(0f, 1f)
            val radius = p.initialRadius * (1f - currentProgress * 0.7f)

            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y)
            )
        }
    }
}
