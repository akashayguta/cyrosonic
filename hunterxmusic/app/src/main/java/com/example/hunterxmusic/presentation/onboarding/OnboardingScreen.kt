package com.example.hunterxmusic.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hunterxmusic.R
import com.example.hunterxmusic.data.repository.UserProfileManager
import com.example.hunterxmusic.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * State-of-the-art cinematic onboarding experience:
 * 1. 3D Cosmic Universe Intro Animation
 * 2. Personalized Name Input (Zero Age Policy)
 * 3. Country / Region Discovery Selector
 * 4. Welcome Celebration & Instant Home Entry
 */
@Composable
fun OnboardingScreen(
    userProfile: UserProfileManager,
    onFinished: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("Global") }
    val keyboard = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060609))
    ) {
        // Dynamic Cosmic Particle Environment
        CosmicParticleField(modifier = Modifier.fillMaxSize())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.1f))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally(tween(400)) { it } + fadeIn(tween(400)))
                        .togetherWith(slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300)))
                },
                modifier = Modifier.weight(0.8f),
                label = "OnboardingSteps"
            ) { currentStep ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (currentStep) {
                        0 -> CinematicIntroStep(onContinue = { step = 1 })
                        1 -> NameStep(
                            name = name,
                            onNameChange = { if (it.length <= 20) name = it },
                            onContinue = {
                                keyboard?.hide()
                                if (name.isNotBlank()) step = 2
                            }
                        )
                        2 -> CountryStep(
                            selectedCountry = selectedCountry,
                            onSelectCountry = { selectedCountry = it },
                            onContinue = {
                                step = 3
                            }
                        )
                        3 -> WelcomeFinishStep(
                            name = name,
                            country = selectedCountry,
                            onEnterApp = {
                                userProfile.completeOnboarding(name, selectedCountry)
                                onFinished()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))
        }
    }
}

/**
 * Step 0: Cinematic Universe Intro with rotating harmonic rings and logo flare.
 */
@Composable
private fun CinematicIntroStep(onContinue: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "introSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(800))
        logoScale.animateTo(
            1f,
            spring(dampingRatio = 0.65f, stiffness = 300f)
        )
        delay(2600)
        onContinue()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onContinue)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Harmonic Sound Rings
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF7DD3FC).copy(alpha = 0.18f), Color.Transparent),
                        center = center,
                        radius = size.width * 0.48f * ringPulse
                    ),
                    center = center,
                    radius = size.width * 0.48f * ringPulse
                )
            }

            // Orbital Geometry
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = ringPulse
                        scaleY = ringPulse
                    }
                    .border(
                        1.5.dp,
                        Brush.sweepGradient(
                            listOf(Color(0xFF7DD3FC), Color(0xFFA855F7), Color(0xFF38BDF8), Color.Transparent)
                        ),
                        CircleShape
                    )
            )

            // Logo Centerpiece
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "CyroSonic Logo",
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "CYROSONIC",
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 12.sp,
            style = TextStyle(
                brush = Brush.horizontalGradient(
                    listOf(Color.White, Color(0xFF7DD3FC), Color(0xFFC084FC))
                )
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "cosmic chill · pure sound",
            color = Color(0xFFA1A1AA),
            fontSize = 13.5.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Step 1: Personalized Name Input (Strict Zero Age Policy).
 */
@Composable
private fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(350)
        try { focusRequester.requestFocus() } catch (_: Exception) { }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "What should we call you?",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your name stays private on your device.",
            color = Color(0xFFA1A1AA),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = {
                Text(
                    text = "Your name",
                    color = Color(0xFF52525B),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onContinue() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF7DD3FC),
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                cursorColor = Color(0xFF7DD3FC),
                focusedContainerColor = Color(0xFF13131A),
                unfocusedContainerColor = Color(0xFF0F0F14)
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(30.dp))

        PrimaryActionButton(
            label = if (name.isBlank()) "Type your name" else "Continue",
            enabled = name.isNotBlank(),
            onClick = onContinue
        )
    }
}

/**
 * Step 2: Country / Region Selector for personalized regional discoveries.
 */
@Composable
private fun CountryStep(
    selectedCountry: String,
    onSelectCountry: (String) -> Unit,
    onContinue: () -> Unit
) {
    val regions = remember {
        listOf(
            RegionOption("Global", "🌍", "Worldwide Hits & Charts"),
            RegionOption("India", "🇮🇳", "Bollywood, Punjabi, Indie"),
            RegionOption("United States", "🇺🇸", "Pop, Hip-Hop, R&B"),
            RegionOption("United Kingdom", "🇬🇧", "UK Drill, Pop, Grime"),
            RegionOption("Japan", "🇯🇵", "J-Pop, Anime, City Pop"),
            RegionOption("South Korea", "🇰🇷", "K-Pop, K-R&B, OST"),
            RegionOption("Latin America", "🇧🇷", "Reggaeton, Funk, Latin"),
            RegionOption("Europe", "🇪🇺", "Dance, EDM, European Pop")
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Choose your region",
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "We will tailor your discovery charts and trending mixes.",
            color = Color(0xFFA1A1AA),
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(regions) { option ->
                val isSelected = selectedCountry == option.name
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.03f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                    label = "regionScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF111116))
                        .border(
                            1.5.dp,
                            if (isSelected) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectCountry(option.name) }
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = option.flag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF7DD3FC))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = option.name,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = option.subtitle,
                            color = Color(0xFF71717A),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PrimaryActionButton(
            label = "Continue with $selectedCountry",
            enabled = true,
            onClick = onContinue
        )
    }
}

/**
 * Step 3: Welcome celebration and smooth handover to Home.
 */
@Composable
private fun WelcomeFinishStep(
    name: String,
    country: String,
    onEnterApp: () -> Unit
) {
    val popScale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        popScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    scaleX = popScale.value
                    scaleY = popScale.value
                }
                .clip(CircleShape)
                .background(Color(0xFF7DD3FC).copy(alpha = 0.15f))
                .border(2.dp, Color(0xFF7DD3FC), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFF7DD3FC),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Welcome, $name!",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your personal cosmic soundscape is prepared for $country.",
            color = Color(0xFFA1A1AA),
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        PrimaryActionButton(
            label = "Enter CyroSonic",
            enabled = true,
            onClick = onEnterApp
        )
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) Brush.horizontalGradient(
                    listOf(Color(0xFF7DD3FC), Color(0xFF38BDF8))
                ) else Brush.linearGradient(
                    listOf(Color(0xFF1E293B), Color(0xFF1E293B))
                )
            )
            .nClick(pressedScale = 0.96f) { if (enabled) onClick() }
    ) {
        Text(
            text = label,
            color = if (enabled) Color.Black else Color(0xFF71717A),
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 3D Ambient Particle Starfield.
 * Draws subtle cosmic dust and energy particles at 60fps draw-phase without recomposition overhead.
 */
@Composable
fun CosmicParticleField(
    modifier: Modifier = Modifier,
    particleCount: Int = 38
) {
    if (com.example.hunterxmusic.data.local.ThemeManager.reduceMotion) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            for (i in 0 until 12) {
                val seed = i * 137.5f
                val px = (seed * 97f) % w
                val py = (seed * 43f) % h
                drawCircle(
                    color = Color(0xFF7DD3FC).copy(alpha = 0.15f),
                    radius = 1.5f,
                    center = Offset(px, py)
                )
            }
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        for (i in 0 until particleCount) {
            val seed = i * 137.5f
            val px = (seed * 97f) % w
            val baseY = (seed * 43f) % h
            val speed = 0.3f + ((i % 5) * 0.15f)
            val currentY = (baseY - (phase * h * speed) + h) % h

            val radius = 1.2f + ((i % 4) * 0.8f)
            val alpha = (0.25f + (sin(phase * 6.28f + i) * 0.18f)).coerceIn(0.08f, 0.55f)

            drawCircle(
                color = if (i % 3 == 0) Color(0xFF7DD3FC).copy(alpha = alpha) else Color.White.copy(alpha = alpha * 0.7f),
                radius = radius,
                center = Offset(px, currentY)
            )
        }
    }
}

private data class RegionOption(
    val name: String,
    val flag: String,
    val subtitle: String
)
