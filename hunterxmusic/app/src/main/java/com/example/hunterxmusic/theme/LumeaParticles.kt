package com.example.hunterxmusic.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class CosmicParticle(
    var x: Float,
    var y: Float,
    val radius: Float,
    val speedX: Float,
    val speedY: Float,
    val alpha: Float,
    val pulseSpeed: Float,
    val phase: Float,
    val color: Color
)

/**
 * Lumea Cosmic Particle & Stardust Field translated into 120fps hardware-accelerated Compose Canvas.
 * Provides subtle ambient floating stardust behind the Now Playing screen and Lyrics view.
 */
@Composable
fun LumeaParticleField(
    modifier: Modifier = Modifier,
    particleCount: Int = 36,
    accentColor: Color = Color(0xFF38BDF8),
    isPlaying: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lumea_dust")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 18_000 else 36_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_time"
    )

    val particles = remember(particleCount) {
        val colors = listOf(
            accentColor.copy(alpha = 0.45f),
            Color(0xFF818CF8).copy(alpha = 0.35f),
            Color(0xFFC084FC).copy(alpha = 0.30f),
            Color.White.copy(alpha = 0.25f)
        )
        List(particleCount) {
            CosmicParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = Random.nextFloat() * 2.2f + 1.0f,
                speedX = (Random.nextFloat() - 0.5f) * 0.08f,
                speedY = -(Random.nextFloat() * 0.06f + 0.02f),
                alpha = Random.nextFloat() * 0.6f + 0.2f,
                pulseSpeed = Random.nextFloat() * 2.5f + 1.0f,
                phase = Random.nextFloat() * 6.28f,
                color = colors[Random.nextInt(colors.size)]
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        val tRad = Math.toRadians(time.toDouble()).toFloat()

        particles.forEach { p ->
            val curX = (p.x + sin(tRad * p.pulseSpeed + p.phase) * 0.04f).mod(1f) * w
            val curY = (p.y + p.speedY * (time / 10f)).mod(1f) * h
            val pulseAlpha = (p.alpha * (0.6f + 0.4f * sin(tRad * p.pulseSpeed * 2f + p.phase))).coerceIn(0.05f, 0.95f)

            drawCircle(
                color = p.color.copy(alpha = pulseAlpha),
                radius = p.radius,
                center = Offset(curX, curY)
            )
        }
    }
}

/**
 * Volumetric Aura Glow translated from Lumea's light rays and backdrop panel.
 */
@Composable
fun LumeaAuraBackdrop(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF38BDF8),
    secondaryColor: Color = Color(0xFF6366F1),
    intensity: Float = 0.35f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lumea_aura")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width * 0.5f
        val centerY = size.height * 0.38f
        val radius = size.minDimension * 0.75f * pulse

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.28f * intensity),
                    secondaryColor.copy(alpha = 0.14f * intensity),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = radius
            ),
            radius = radius,
            center = Offset(centerX, centerY)
        )
    }
}
