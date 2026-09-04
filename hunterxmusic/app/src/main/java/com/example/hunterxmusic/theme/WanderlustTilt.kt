package com.example.hunterxmusic.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 3D Card Tilt & Tactile Spring Bounce translated from Wanderlust's pointer-follow physics.
 * Adds tactile depth when pressing cards, album art, or hero items.
 */
fun Modifier.wanderlustCardTilt(
    maxTiltDeg: Float = 6f,
    pressScale: Float = 0.965f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 400f
        ),
        label = "wanderlust_scale"
    )

    val rotationX by animateFloatAsState(
        targetValue = if (isPressed) maxTiltDeg else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 350f
        ),
        label = "wanderlust_rotX"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.rotationX = rotationX
            cameraDistance = 12f * density
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = {
                    onClick?.invoke()
                }
            )
        }
}
