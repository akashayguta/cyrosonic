package com.example.hunterxmusic.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Dynamic theming from album art: extracts the vibrant color of the current
 * track's artwork (Coil + Palette, off the main thread, cached per URL) and
 * exposes it as an animated Compose state. Feeds [Modifier.dynamicArtGlow]
 * so the app's background breathes with whatever is playing.
 */
@Composable
fun rememberTrackGlowColor(albumArtUrl: String?): Color? {
    val context = LocalContext.current
    var extracted by remember { mutableStateOf<Color?>(null) }
    var cachedUrl by remember { mutableStateOf<String?>(null) }
    // Last-known color: while the NEXT track's palette extracts, keep the old
    // glow alive so it crossfades instead of popping off.
    var lastKnown by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(albumArtUrl) {
        val url = albumArtUrl
        if (url.isNullOrBlank() || url == cachedUrl) return@LaunchedEffect
        extracted = null
        try {
            // Coil's process-wide singleton — a fresh ImageLoader here used to
            // leak its caches/threads on every track change.
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(128)
                .build()
            val bitmap = (loader.execute(request) as? SuccessResult)?.drawable
                ?.let { it as? android.graphics.drawable.BitmapDrawable }
                ?.bitmap
            if (bitmap != null) {
                // Palette generation is CPU work — keep it off the main thread.
                val dom = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    androidx.palette.graphics.Palette.from(bitmap).generate()
                }.let { palette ->
                    palette.getVibrantColor(palette.getDominantColor(0xFF7DD3FC.toInt()))
                }
                extracted = Color(dom)
                lastKnown = Color(dom)
                cachedUrl = url
            }
        } catch (_: Exception) { }
    }

    val animated = animateColorAsState(
        targetValue = extracted ?: Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "artGlow"
    )
    // New color ready → animated in; still extracting → HOLD the previous
    // track's glow so the transition crossfades instead of pop-off/pop-in.
    return extracted?.let { animated.value } ?: lastKnown
}

/**
 * Draws a soft vertical glow of the current song's dominant color over the
 * top of a screen. Draw-phase only — zero recomposition per frame. Respects
 * the ThemeManager.dynamicColor toggle and stays off in light mode.
 */
fun Modifier.dynamicArtGlow(glowColor: Color?, maxAlpha: Float = 0.16f): Modifier =
    drawBehind {
        val enabled = com.example.hunterxmusic.data.local.ThemeManager.dynamicColor &&
            !com.example.hunterxmusic.data.local.ThemeManager.isLight
        val color = glowColor ?: return@drawBehind
        if (!enabled || color == Color.Transparent) return@drawBehind
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = maxAlpha), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.45f
            )
        )
    }