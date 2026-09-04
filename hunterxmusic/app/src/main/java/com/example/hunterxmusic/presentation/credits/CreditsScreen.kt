package com.example.hunterxmusic.presentation.credits

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hunterxmusic.R
import com.example.hunterxmusic.theme.CryoDisplay
import com.example.hunterxmusic.theme.NocturneMotion
import com.example.hunterxmusic.theme.CryoText
import com.example.hunterxmusic.theme.HunterTextSecondary
import com.example.hunterxmusic.theme.nClick

/**
 * Kinetic Credits — "the burden of the sun". A glowing cosmic star orbits and
 * radiates heat pulses behind frozen glass cards that float, tilt, and react
 * to touch. Replaces the static credits card entirely.
 */
@Composable
fun CreditsScreen(
    versionName: String,
    onBack: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "cryoCredits")
    val orbit by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing)),
        label = "orbit"
    )
    val heat by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "heat"
    )
    val floatPhase by transition.animateFloat(
        initialValue = 0f, targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "float"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03040A))
    ) {
        // Orbiting star + heat pulses behind the glass
        Canvas(Modifier.fillMaxSize()) {
            val centerStar = Offset(size.width * 0.5f, size.height * 0.34f)
            val orbitRadius = size.minDimension * 0.34f
            val starPos = Offset(
                centerStar.x + orbitRadius * kotlin.math.cos(orbit * 0.017453292f),
                centerStar.y + orbitRadius * 0.42f * kotlin.math.sin(orbit * 0.017453292f)
            )

            // Orbit trail
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF7DD3FC).copy(alpha = 0.10f), Color.Transparent),
                    center = centerStar,
                    radius = orbitRadius * 1.15f
                ),
                radius = orbitRadius * 1.15f,
                center = centerStar
            )

            // Radiating heat pulses — three staggered expanding rings
            repeat(3) { i ->
                val phase = (heat + i * 0.33f) % 1f
                drawCircle(
                    color = Color(0xFFF6C453).copy(alpha = (1f - phase) * 0.22f),
                    radius = 40f + phase * size.minDimension * 0.55f,
                    center = centerStar,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                )
            }

            // The burden itself — the sun
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFCE38A), Color(0xFFF6C453).copy(alpha = 0.65f), Color.Transparent),
                    center = centerStar,
                    radius = 90f
                ),
                radius = 90f,
                center = centerStar
            )

            // The orbiting companion star
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFA5F3FC), Color(0xFF7DD3FC).copy(alpha = 0.4f), Color.Transparent),
                    center = starPos,
                    radius = 34f
                ),
                radius = 34f,
                center = starPos
            )
        }

        // Close
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .size(40.dp)
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Floating glass cards
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
            // The creator card leads. Before this, the app credited nobody.
            CreatorCard(phase = floatPhase)
            Spacer(Modifier.height(12.dp))
            GlassCreditCard(
                title = "CyroSonic $versionName",
                subtitle = "Frozen crystal chill × pure resonant sound",
                phase = floatPhase,
                phaseOffset = 1.1f
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "cosmic chill · pure sound",
                color = HunterTextSecondary,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                fontFamily = CryoText,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/** Hero credit — who actually made this. */
@Composable
private fun CreatorCard(phase: Float) {
    val dy = kotlin.math.sin(phase) * 7f
    val tilt = kotlin.math.sin(phase + 0.5f) * 1.2f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dy
                rotationZ = tilt
            }
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f))
                )
            )
            .border(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.30f), RoundedCornerShape(24.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFA5F3FC), Color(0xFF7DD3FC), Color(0xFFA5B4FC))
                    )
                )
        ) {
            Text(
                text = "SP",
                color = Color(0xFF04060C),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = CryoDisplay
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Sandeep Patel",
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = CryoDisplay,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Creator · Design & Engineering",
            color = Color(0xFF7DD3FC),
            fontSize = 12.sp,
            fontFamily = CryoText,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Designed and built entirely by one person, for people who live in their headphones.",
            color = HunterTextSecondary,
            fontSize = 11.5.sp,
            fontFamily = CryoText,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun GlassCreditCard(
    title: String,
    subtitle: String,
    phase: Float,
    phaseOffset: Float
) {
    // Float + gentle gyroscopic-style tilt driven by the shared phase.
    val dy = kotlin.math.sin(phase + phaseOffset) * 6f
    val tilt = kotlin.math.sin(phase + phaseOffset + 0.8f) * 1.6f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dy
                rotationZ = tilt
            }
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = CryoDisplay
                )
                Text(
                    text = subtitle,
                    color = HunterTextSecondary,
                    fontSize = 11.5.sp,
                    fontFamily = CryoText,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
