package com.sushanth.poplauncher.bubble

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sushanth.poplauncher.ui.theme.ChartAmber
import com.sushanth.poplauncher.ui.theme.ChartCoral
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticlePool {
    private val pool = ArrayDeque<Particle>(MAX_POOL_SIZE)

    fun getParticle(angle: Float, speed: Float, color: Color, initialRadius: Float): Particle {
        synchronized(pool) {
            return if (pool.isNotEmpty()) {
                val p = pool.removeLast()
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

    fun recycleAll(particles: List<Particle>) {
        synchronized(pool) {
            for (particle in particles) {
                if (pool.size >= MAX_POOL_SIZE) break
                pool.addLast(particle)
            }
        }
    }

    companion object {
        private const val MAX_POOL_SIZE = 64
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

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    val particles = remember(primary, secondary, tertiary, primaryContainer) {
        val colors = arrayOf(primary, secondary, tertiary, primaryContainer, ChartAmber, ChartCoral)
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

    Canvas(modifier = Modifier.fillMaxSize()) {
        val currentProgress = progress.value
        val center = Offset(centerX, centerY)

        // Expanding shockwave ring
        val ringRadius = currentProgress * 140f
        val ringAlpha = (1f - currentProgress).coerceIn(0f, 1f)
        drawCircle(
            color = primary.copy(alpha = ringAlpha * 0.7f),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 4f * ringAlpha)
        )

        // Burst particles
        val radiusScale = 1f - currentProgress * 0.7f
        particles.forEach { p ->
            val dist = p.speed * currentProgress
            val x = centerX + cos(p.angle) * dist
            val y = centerY + sin(p.angle) * dist

            drawCircle(
                color = p.color.copy(alpha = ringAlpha),
                radius = p.initialRadius * radiusScale,
                center = Offset(x, y)
            )
        }
    }
}
