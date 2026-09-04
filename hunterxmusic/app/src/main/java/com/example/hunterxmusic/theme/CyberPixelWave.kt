package com.example.hunterxmusic.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * CyberPixelWave from codescan:
 * Real-time reactive matrix equalizer wave that animates dynamically behind or below the now playing bar.
 */
@Composable
fun CyberPixelWaveVisualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    isPlaying: Boolean = true,
    accentColor: Color = Color(0xFF38BDF8)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 1400 else 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        val totalWidth = size.width
        val maxHeight = size.height
        val barWidth = (totalWidth / (barCount * 1.6f)).coerceIn(2.5f, 7f)
        val gap = (totalWidth - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)

        val gradient = Brush.verticalGradient(
            colors = listOf(
                accentColor,
                accentColor.copy(alpha = 0.35f)
            )
        )

        for (i in 0 until barCount) {
            val normalizedI = i.toFloat() / barCount.toFloat()
            val wave1 = sin(phase + normalizedI * 4.5f)
            val wave2 = sin(phase * 1.5f + normalizedI * 2.2f)
            val combined = ((wave1 + wave2) / 2f + 1f) / 2f // [0, 1]

            val dynamicHeight = if (isPlaying) {
                (maxHeight * (0.2f + 0.8f * combined)).coerceIn(4f, maxHeight)
            } else {
                maxHeight * 0.15f
            }

            val x = i * (barWidth + gap)
            val y = maxHeight - dynamicHeight

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, dynamicHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
