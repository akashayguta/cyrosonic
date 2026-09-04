package com.example.hunterxmusic.presentation.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hunterxmusic.theme.CryoText
import com.example.hunterxmusic.theme.HunterTextHint
import com.example.hunterxmusic.theme.HunterTextSecondary
import com.example.hunterxmusic.theme.NocturneMotion

/**
 * Cryo-Waveform Seeking Bar — replaces the flat slider with frozen crystal
 * peaks. Amplitudes are deterministically seeded from the track id (stable
 * per song, no re-analysis), the played side fills with a cyan gradient, and
 * the playhead rides a glowing particle. Tap or drag to seek.
 */
@Composable
fun WaveformSeekBar(
    currentMs: Long,
    durationMs: Long,
    isBuffering: Boolean,
    activeColor: Color,
    seed: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val barCount = 52
    val amplitudes = remember(seed) {
        val rnd = java.util.Random(seed.hashCode().toLong())
        FloatArray(barCount) { 0.22f + rnd.nextFloat() * 0.78f }
    }

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val targetFraction = dragFraction
        ?: (if (durationMs > 0) (currentMs.toFloat() / durationMs) else 0f).coerceIn(0f, 1f)
    val smoothFraction: Float by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(180),
        label = "waveProgress"
    )

    fun seekAt(fraction: Float) {
        if (durationMs > 0) onSeek((fraction * durationMs).toLong().coerceIn(0L, durationMs))
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = waveTime(currentMs),
            color = HunterTextSecondary,
            fontSize = 11.sp,
            fontFamily = CryoText,
            modifier = Modifier.width(38.dp)
        )
        Spacer(Modifier.width(8.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        seekAt((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            dragFraction?.let { seekAt(it) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            dragFraction = f
                        }
                    )
                }
        ) {
            val gap = size.width / barCount
            val barWidth = gap * 0.58f
            val playedX = smoothFraction * size.width

            for (i in 0 until barCount) {
                val amp = amplitudes[i]
                val barHeight = amp * size.height * 0.86f
                val topLeft = Offset(gap * i + (gap - barWidth) / 2f, (size.height - barHeight) / 2f)
                val played = (i + 0.5f) * gap <= playedX
                if (played) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(activeColor.copy(alpha = 0.95f), activeColor.copy(alpha = 0.45f)),
                            startY = topLeft.y,
                            endY = topLeft.y + barHeight
                        ),
                        topLeft = topLeft,
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                } else {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.16f),
                        topLeft = topLeft,
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }

            // Playhead — glowing particle riding the crystal edge
            if (durationMs > 0) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(activeColor.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(playedX, center.y),
                        radius = 22f
                    ),
                    radius = 22f,
                    center = Offset(playedX, center.y)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5f,
                    center = Offset(playedX, center.y)
                )
                drawCircle(
                    color = activeColor,
                    radius = 8f,
                    center = Offset(playedX, center.y),
                    style = Stroke(width = 2f)
                )
            }

            if (isBuffering) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = 3f,
                    center = Offset(playedX, center.y)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = waveTime(durationMs),
            color = HunterTextHint,
            fontSize = 11.sp,
            fontFamily = CryoText,
            modifier = Modifier.width(38.dp)
        )
    }
}

private fun waveTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
