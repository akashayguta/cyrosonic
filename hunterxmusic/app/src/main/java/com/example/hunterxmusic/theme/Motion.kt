package com.example.hunterxmusic.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Nocturne Motion System — the Compose-native translation of framer-motion's
 * signature language: spring physics on every interaction, staggered entrance
 * cascades, whileTap scale micro-interactions, and slow ambient gradient drift
 * (the "animated gradient background" pattern). Every spec in the app pulls
 * from these tokens so the whole app shares one motion personality.
 */
object NocturneMotion {
    // Springs — framer's bouncy default feel (slight overshoot, quick settle)
    const val BOUNCY_DAMPING = 0.55f
    const val BOUNCY_STIFFNESS = 380f

    // Springs — smooth settle, no visible overshoot (panels, offsets)
    const val SMOOTH_DAMPING = 0.8f
    const val SMOOTH_STIFFNESS = 300f

    // Tween durations (ms) — Material emphasized-ish curves
    const val DUR_FAST = 150
    const val DUR_NORMAL = 260
    const val DUR_SLOW = 420

    // Stagger cascade step between siblings
    const val STAGGER_STEP = 55L
}

/**
 * whileTap, the framer-motion classic: press and the element springs down to
 * [pressedScale], release and it springs back with a hint of overshoot.
 * Replaces plain `.clickable` so every tappable card shares one tactile voice.
 */
fun Modifier.nClick(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = NocturneMotion.BOUNCY_DAMPING,
            stiffness = NocturneMotion.BOUNCY_STIFFNESS
        ),
        label = "nClickScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Staggered entrance: fade in while rising smoothly via draw-phase translationY.
 * Draw-phase only — zero layout recalculation per frame, preventing list jitter.
 */
@Composable
fun StaggerIn(
    delayMs: Long = 0L,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs)
        visible = true
    }
    val animAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(NocturneMotion.DUR_SLOW, easing = FastOutSlowInEasing),
        label = "staggerAlpha"
    )
    val risePx by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = spring(
            dampingRatio = NocturneMotion.SMOOTH_DAMPING,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "staggerRise"
    )
    Box(
        modifier
            .graphicsLayer {
                alpha = animAlpha
                translationY = risePx
            }
    ) {
        content()
    }
}

/**
 * Subtle album artwork dynamic breathing: smooth, gentle scale when audio plays.
 */
fun Modifier.nBreathing(isPlaying: Boolean, maxScale: Float = 1.025f): Modifier = composed {
    val target = if (isPlaying) maxScale else 1.0f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = NocturneMotion.SMOOTH_DAMPING,
            stiffness = NocturneMotion.SMOOTH_STIFFNESS
        ),
        label = "nBreathing"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Animated Play/Pause icon with tactile spring bounce and rotational ease.
 */
@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.94f,
        animationSpec = spring(dampingRatio = NocturneMotion.BOUNCY_DAMPING, stiffness = 420f),
        label = "playPauseScale"
    )
    Icon(
        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (isPlaying) "Pause" else "Play",
        tint = tint,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    )
}

/**
 * Animated Heart icon with spring pop feedback on favorite/unfavorite.
 */
@Composable
fun AnimatedHeartIcon(
    isLiked: Boolean,
    modifier: Modifier = Modifier,
    likedTint: Color = HunterLiked,
    unlikedTint: Color = HunterTextSecondary,
    size: androidx.compose.ui.unit.Dp = 22.dp
) {
    val scale by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 480f),
        label = "heartPopScale"
    )
    Icon(
        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = if (isLiked) "Liked" else "Like",
        tint = if (isLiked) likedTint else unlikedTint,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    )
}

/**
 * Ambient aurora — two large radial glows (moonlight cyan + dusk violet)
 * drifting on slow sine paths behind the UI. Draw-phase only: the animated
 * phase is read inside drawBehind, so nothing recomposes per frame. Alphas
 * stay low to respect the AMOLED black canvas.
 */
fun Modifier.nocturneAurora(
    brand: Color = Color(0xFF7DD3FC),
    accent: Color = Color(0xFFA5B4FC)
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing)
        ),
        label = "auroraPhase"
    )
    drawBehind {
        val w = size.width
        val h = size.height
        val tau = phase * 2f * Math.PI.toFloat()

        val orb1 = Offset(
            x = w * (0.22f + 0.16f * sin(tau)),
            y = h * (0.10f + 0.05f * sin(tau + 1.2f))
        )
        val orb2 = Offset(
            x = w * (0.80f - 0.14f * cos(tau)),
            y = h * (0.24f + 0.06f * sin(tau + 2.6f))
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(brand.copy(alpha = 0.10f), Color.Transparent),
                center = orb1,
                radius = w * 0.65f
            ),
            radius = w * 0.65f,
            center = orb1
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.08f), Color.Transparent),
                center = orb2,
                radius = w * 0.60f
            ),
            radius = w * 0.60f,
            center = orb2
        )
    }
}

/**
 * Number ticker — counts up from zero on first composition (the 21st.dev
 * "animated number" pattern). Used for stat readouts.
 */
@Composable
fun RollingCount(
    value: Int,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier
) {
    val count = remember { Animatable(0f) }
    LaunchedEffect(value) {
        count.animateTo(
            targetValue = value.toFloat(),
            animationSpec = tween(1100, easing = FastOutSlowInEasing)
        )
    }
    androidx.compose.material3.Text(
        text = count.value.roundToInt().toString(),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

/**
 * Sweeping progress bar — animates from zero to [fraction] on a soft curve
 * every time it enters composition, replacing the static LinearProgressIndicator.
 */
@Composable
fun AnimatedBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.1f)
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = fraction.coerceIn(0f, 1f),
            animationSpec = tween(750, easing = FastOutSlowInEasing)
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.value)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

/**
 * Skeleton shimmer — a diagonal highlight sweeping across a placeholder block.
 * Draw-phase only, so nothing recomposes per frame. Use on any surface that is
 * standing in for content still loading.
 */
fun Modifier.nShimmer(
    base: Color = Color.White.copy(alpha = 0.05f),
    highlight: Color = Color.White.copy(alpha = 0.13f)
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = LinearEasing)
        ),
        label = "shimmerPhase"
    )
    drawBehind {
        drawRect(color = base)
        val sweep = size.width * 1.6f
        val x = -sweep + phase * (size.width + sweep * 2f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x, 0f),
                end = Offset(x + sweep, size.height)
            )
        )
    }
}

/**
 * Pulsing glow ring — marks the row or tile that is currently playing so it
 * reads at a glance without needing a label.
 */
fun Modifier.nGlow(
    active: Boolean,
    color: Color = Color(0xFF7DD3FC),
    cornerRadiusDp: Float = 14f
): Modifier = composed {
    if (!active) return@composed this
    val transition = rememberInfiniteTransition(label = "glow")
    val pulse by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "glowPulse"
    )
    drawBehind {
        drawRoundRect(
            color = color.copy(alpha = pulse),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                cornerRadiusDp * density,
                cornerRadiusDp * density
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density)
        )
    }
}

/**
 * Scroll-linked parallax: content drifts at a fraction of the scroll offset so
 * hero images sit "behind" the page. Pass the list's first-visible offset.
 */
fun Modifier.nParallax(scrollOffsetPx: Float, factor: Float = 0.35f): Modifier =
    this.graphicsLayer { translationY = scrollOffsetPx * factor }

/**
 * Depth for a pager page: the settling page scales to full size and opacity
 * while neighbours sit back. [pageOffset] is 0 for the centred page and grows
 * toward 1 for adjacent ones.
 */
fun Modifier.nPagerDepth(
    pageOffset: Float,
    minScale: Float = 0.92f,
    minAlpha: Float = 0.55f
): Modifier = this.graphicsLayer {
    val t = 1f - pageOffset.coerceIn(0f, 1f)
    val s = minScale + (1f - minScale) * t
    scaleX = s
    scaleY = s
    alpha = minAlpha + (1f - minAlpha) * t
}
