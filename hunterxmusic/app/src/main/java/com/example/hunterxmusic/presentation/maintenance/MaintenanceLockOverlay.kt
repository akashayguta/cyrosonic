package com.example.hunterxmusic.presentation.maintenance

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hunterxmusic.data.remote.CloudAppConfig
import com.example.hunterxmusic.theme.*

@Composable
fun MaintenanceLockOverlay(
    config: CloudAppConfig,
    onOwnerBypassTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var brandTapCount by remember { mutableIntStateOf(0) }
    var lastBrandTapTime by remember { mutableLongStateOf(0L) }

    val infiniteTransition = rememberInfiniteTransition(label = "MaintenancePulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Glow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Secret 15-Tap Stealth Header for Owner Bypass
            Text(
                text = "CYROSONIC",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val now = System.currentTimeMillis()
                        if (now - lastBrandTapTime < 800) {
                            brandTapCount++
                            if (brandTapCount >= 15) {
                                brandTapCount = 0
                                onOwnerBypassTrigger()
                            }
                        } else {
                            brandTapCount = 1
                        }
                        lastBrandTapTime = now
                    }
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Animated Warning Icon Shield
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            scaleX = glowScale
                            scaleY = glowScale
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.35f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1917))
                        .border(1.5.dp, Color(0xFFF59E0B), CircleShape)
                ) {
                    Icon(
                        imageVector = if (config.buttonAction == "UPDATE_LINK") Icons.Default.Download else Icons.Default.Construction,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Maintenance Title
            Text(
                text = config.maintenanceTitle.ifBlank { "🚧 Maintenance Mode" },
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Maintenance Message
            Text(
                text = config.maintenanceMessage.ifBlank { "CyroSonic is currently undergoing a scheduled upgrade. Please check back shortly." },
                color = HunterTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Action Button
            Button(
                onClick = {
                    if (config.buttonAction == "UPDATE_LINK" && config.updateUrl.isNotBlank()) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.updateUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    } else {
                        // Close the app
                        (context as? Activity)?.finishAffinity()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (config.buttonAction == "UPDATE_LINK") Color.White else Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = if (config.buttonAction == "UPDATE_LINK") Icons.Default.Download else Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = if (config.buttonAction == "UPDATE_LINK") Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = config.buttonText.ifBlank { if (config.buttonAction == "UPDATE_LINK") "Download Update Now" else "Close App" },
                    color = if (config.buttonAction == "UPDATE_LINK") Color.Black else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
