package com.example.hunterxmusic.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.hunterxmusic.domain.model.Track
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spotify / Apple Music Flagship Animated Album Canvas:
 * 1. Fluid organic ambient mesh gradient.
 * 2. Rotating physical vinyl record with tactile groove reflections that slides out during playback.
 * 3. Sound-reactive neon equalizer pulse ring.
 * 4. 3D card tilt & double-tap favorite heart burst.
 */
@Composable
fun AnimatedAlbumCanvas(
    track: Track?,
    isPlaying: Boolean,
    vibrantColor: Color,
    dominantColor: Color,
    onDoubleTapLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ── Vinyl Rotation Engine ──────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Smooth vinyl slide-out distance when playing (0dp stopped -> 36dp playing)
    val vinylOffset by animateDpAsState(
        targetValue = if (isPlaying) 32.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 220f),
        label = "vinylSlide"
    )

    // Pulsing aura ring
    val pulseRing by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRing"
    )

    // Fluid Mesh Gradient shift
    val meshPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshPhase"
    )

    // Double tap like animation
    var showHeartBurst by remember { mutableStateOf(false) }
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            heartScale.snapTo(0.4f)
            heartAlpha.snapTo(1f)
            heartScale.animateTo(1.35f, spring(dampingRatio = 0.5f, stiffness = 400f))
            heartAlpha.animateTo(0f, tween(300))
            showHeartBurst = false
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .wanderlustCardTilt(maxTiltDeg = 6f, pressScale = 0.98f)
            .pointerInput(track?.id) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTapLike()
                        showHeartBurst = true
                    }
                )
            }
    ) {
        // Layer 1: Ambient Soundwave Ring
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .graphicsLayer {
                    scaleX = pulseRing
                    scaleY = pulseRing
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 8.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        vibrantColor.copy(alpha = if (isPlaying) 0.35f else 0.12f),
                        dominantColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.25f
                ),
                radius = radius * 1.25f,
                center = center
            )

            // Equalizer ring ticks
            val tickCount = 48
            for (i in 0 until tickCount) {
                val angle = (i.toFloat() / tickCount) * 2 * Math.PI.toFloat()
                val waveOffset = sin(angle * 4 + meshPhase) * 6.dp.toPx() * (if (isPlaying) 1f else 0.2f)
                val startR = radius + 4.dp.toPx()
                val endR = radius + 12.dp.toPx() + waveOffset

                val start = Offset(center.x + cos(angle) * startR, center.y + sin(angle) * startR)
                val end = Offset(center.x + cos(angle) * endR, center.y + sin(angle) * endR)

                drawLine(
                    color = vibrantColor.copy(alpha = if (isPlaying) 0.65f else 0.25f),
                    start = start,
                    end = end,
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }

        // Layer 2: Tactile Vinyl Record (Slides out smoothly behind cover)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize(0.84f)
                .offset(x = vinylOffset)
                .graphicsLayer {
                    rotationZ = if (isPlaying) vinylRotation else 0f
                }
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF0C0C0E))
                .border(1.5.dp, Color(0xFF27272A), CircleShape)
        ) {
            // Grooved vinyl textures
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val maxR = size.minDimension / 2
                for (r in listOf(0.35f, 0.48f, 0.60f, 0.72f, 0.84f, 0.94f)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = maxR * r,
                        center = c,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Vinyl center label
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize(0.34f)
                    .clip(CircleShape)
                    .background(vibrantColor)
                    .border(2.dp, Color(0xFF18181B), CircleShape)
            ) {
                if (track?.albumArtUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(track.albumArtUrl),
                        contentDescription = "Vinyl Label",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Center spindle hole
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF09090B))
                )
            }
        }

        // Layer 3: Main Artwork Card with Glass Depth
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .shadow(28.dp, RoundedCornerShape(22.dp), spotColor = vibrantColor.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF141418))
                .border(1.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
        ) {
            if (track?.albumArtUrl != null) {
                Image(
                    painter = rememberAsyncImagePainter(track.albumArtUrl),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Subtle glass sheen highlight
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
        }

        // Layer 4: Double Tap Heart Burst Animation
        if (heartAlpha.value > 0f) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Liked",
                tint = Color(0xFFF43F5E),
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = heartScale.value
                        scaleY = heartScale.value
                        alpha = heartAlpha.value
                    }
            )
        }
    }
}
