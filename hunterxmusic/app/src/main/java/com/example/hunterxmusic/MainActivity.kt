package com.example.hunterxmusic

import android.os.Bundle
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hunterxmusic.data.player.PlaybackState
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.presentation.ai.AiChatView
import com.example.hunterxmusic.presentation.ai.AiChatViewModel
import com.example.hunterxmusic.presentation.artist.ArtistPortalView
import com.example.hunterxmusic.presentation.home.HomeScreen
import com.example.hunterxmusic.presentation.home.HomeViewModel
import com.example.hunterxmusic.presentation.library.LibraryScreen
import com.example.hunterxmusic.presentation.library.LibraryViewModel
import com.example.hunterxmusic.presentation.more.MoreScreen
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import com.example.hunterxmusic.presentation.player.MiniPlayerBar
import com.example.hunterxmusic.presentation.player.PlayerScreen
import com.example.hunterxmusic.presentation.player.PlayerViewModel
import com.example.hunterxmusic.presentation.search.SearchScreen
import com.example.hunterxmusic.presentation.search.SearchViewModel
import com.example.hunterxmusic.core.ota.OtaState
import com.example.hunterxmusic.core.ota.OtaUpdateInfo
import com.example.hunterxmusic.theme.*
import androidx.compose.ui.text.font.FontWeight

enum class MainTab { Home, Search, Explore, Library }

class MainActivity : ComponentActivity() {

    companion object {
        /** Fired by the media notification / lockscreen / Bluetooth: open the app straight into the player. */
        const val ACTION_OPEN_PLAYER = "com.example.hunterxmusic.action.OPEN_PLAYER"
    }

    private lateinit var dependencies: AppDependencies

    /**
     * Set when the media notification (or any OPEN_PLAYER intent) is tapped.
     * Lives outside setContent so both onCreate and onNewIntent can flip it;
     * Compose observes it and expands the player screen.
     */
    private var openPlayerRequested by mutableStateOf(false)

    /**
     * Notification taps used to be read once in a LaunchedEffect keyed on the
     * original intent, so tapping a notification while the app was already
     * running did nothing at all.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDeepLink(intent)
        if (intent.action == ACTION_OPEN_PLAYER) {
            openPlayerRequested = true
        }
        val playQuery = intent.getStringExtra("EXTRA_PLAY_QUERY")
        if (!playQuery.isNullOrBlank()) {
            intent.removeExtra("EXTRA_PLAY_QUERY")
            val deps = HunterApplication.dependencies
            lifecycleScope.launch {
                try {
                    val tracks = deps.musicRepository.searchTracks(playQuery)
                    if (tracks.isNotEmpty()) {
                        deps.musicPlayerManager.playTrack(tracks.first())
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        dependencies = HunterApplication.dependencies
        enableEdgeToEdge()
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        // Dynamic permission request for Notifications & Audio Media on modern Android
        val permissionsToRequest = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            @Suppress("DEPRECATION")
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 101)
        }

        // Initialize Cloud Sync & Remote Broadcast/Maintenance Manager
        com.example.hunterxmusic.data.remote.AeroChaseCloudSyncManager.init(this, dependencies.okHttpClient)

        // In-App Over-The-Air (OTA) Release Update Check
        dependencies.otaUpdateManager.checkForUpdates(silent = true)
        handleIncomingDeepLink(intent)

        if (intent?.action == ACTION_OPEN_PLAYER) {
            openPlayerRequested = true
        }

        if (savedInstanceState == null) {
            val playQuery = intent?.getStringExtra("EXTRA_PLAY_QUERY")
            if (!playQuery.isNullOrBlank()) {
                intent?.removeExtra("EXTRA_PLAY_QUERY")
                lifecycleScope.launch {
                    try {
                        val tracks = dependencies.musicRepository.searchTracks(playQuery)
                        if (tracks.isNotEmpty()) {
                            dependencies.musicPlayerManager.playTrack(tracks.first())
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        setContent {
            HUNTERxMUSICTheme {
                @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
                SharedTransitionLayout {
                    val sharedScope = this
                    val deps = HunterApplication.dependencies

                    // ViewModels survive configuration changes and process recreation
                    val playerViewModel: PlayerViewModel = viewModel(key = "player") {
                        PlayerViewModel(deps.musicPlayerManager, deps.musicRepository, deps.recentTracksStore)
                    }
                    val homeViewModel: HomeViewModel = viewModel(key = "home") {
                        HomeViewModel(deps.musicRepository, deps.localLibraryManager, deps.homeShelvesCache, deps.recentTracksStore, deps.personalizationEngine)
                    }
                    val searchViewModel: SearchViewModel = viewModel(key = "search") {
                        SearchViewModel(deps.musicRepository)
                    }
                    val libraryViewModel: LibraryViewModel = viewModel(key = "library") {
                        LibraryViewModel(deps.musicRepository, deps.localDeviceMusicManager)
                    }
                    val exploreViewModel: com.example.hunterxmusic.presentation.explore.ExploreViewModel =
                        viewModel(key = "explore") {
                            com.example.hunterxmusic.presentation.explore.ExploreViewModel(deps.musicRepository)
                        }
                    val aiChatViewModel: AiChatViewModel = viewModel(key = "ai") {
                        AiChatViewModel(deps.aiRepository)
                    }

                    val cloudConfig by com.example.hunterxmusic.data.remote.AeroChaseCloudSyncManager.configState.collectAsState()
                    // rememberSaveable throughout: rotating the phone used to
                    // reset the tab to Home and close every open overlay.
                    var currentTab by rememberSaveable { mutableStateOf(MainTab.Home) }
                    var showAiChat by rememberSaveable { mutableStateOf(false) }
                    var showArtistPortal by rememberSaveable { mutableStateOf(false) }
                    var showOwnerPortal by rememberSaveable { mutableStateOf(false) }
                    var showOwnerPasscodeDialog by rememberSaveable { mutableStateOf(false) }
                    var ownerPasscode by rememberSaveable { mutableStateOf("") }
                    var ownerFailCount by rememberSaveable { mutableStateOf(0) }
                    var ownerLockedUntil by rememberSaveable { mutableStateOf(0L) }
                    var selectedArtistName by rememberSaveable { mutableStateOf("") }
                    var openMoodKey by rememberSaveable { mutableStateOf<String?>(null) }
                    var openPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
                    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
                    var isSponsorBlockEnabled by remember {
                        mutableStateOf(dependencies.musicRepository.isSponsorBlockEnabled())
                    }
                    var showTimeMachineDialog by rememberSaveable { mutableStateOf(false) }
                    var showCreditsScreen by rememberSaveable { mutableStateOf(false) }
                    var showSettingsOverlay by rememberSaveable { mutableStateOf(false) }
                    var timeMachineStats by remember {
                        mutableStateOf(dependencies.musicRepository.getListeningStats())
                    }

                    val isOnboarded = deps.userProfileManager.isOnboarded
                    var showIntro by remember { mutableStateOf(false) }
                    var showOnboardingOverlay by remember { mutableStateOf(!isOnboarded) }
                    var onboardedName by remember { mutableStateOf(deps.userProfileManager.displayName) }

                    val playbackState by playerViewModel.playbackState.collectAsState()
                    val hasActiveTrack = playbackState.currentTrack != null
                    var isPlayerExpanded by remember { mutableStateOf(false) }
                    LaunchedEffect(openPlayerRequested) {
                        if (openPlayerRequested) isPlayerExpanded = true
                    }

                    // Back navigation â€” DISARMED while the intro or the mandatory onboarding
                    // dialog owns the screen: an always-armed activity callback
                    // colliding with a dismissOnBackPress=false dialog window is
                    // a predictive-back crash vector on Android 13+. Collapses
                    // innermost overlay first; does nothing on bare home.
                    if (!showIntro && !showOnboardingOverlay) {
                        BackHandler(enabled = true) {
                            when {
                                showOwnerPortal -> showOwnerPortal = false
                                showCreditsScreen -> showCreditsScreen = false
                                showSettingsOverlay -> showSettingsOverlay = false
                                showAiChat -> showAiChat = false
                                isPlayerExpanded -> isPlayerExpanded = false
                                showArtistPortal -> showArtistPortal = false
                                openPlaylistId != null -> openPlaylistId = null
                                openMoodKey != null -> openMoodKey = null
                                currentTab != MainTab.Home -> currentTab = MainTab.Home
                            }
                        }
                    }

                    val onTrackClick: (Track) -> Unit = { track ->
                        playerViewModel.playTrackWithTasteRadio(track)
                        isPlayerExpanded = true
                    }
                    val onTrackListClick: (List<Track>, Int) -> Unit = { tracks, index ->
                        playerViewModel.playQueue(tracks, index)
                        isPlayerExpanded = true
                    }

                    var animateIn by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        animateIn = true
                    }

                    // â”€â”€ CyroSonic opening animation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    // Plays on EVERY fresh process start (every app launch),
                    // skipped only on rotation. Onboarding is separate: it asks
                    // name/age ONLY the first time ever; every later launch
                    // replays the intro but never re-asks. The onboarding dialog
                    // is gated behind !showIntro so the intro is never hidden
                    // behind it on first launch.
                    if (showIntro) {
                        // Fade the whole intro in from black. animateFloatAsState only
                        // animates when its target CHANGES, so we start these flags false
                        // and flip them true from a LaunchedEffect â€” a constant
                        // targetValue = 1f snaps to 1 instantly and never animates.
var introVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        HunterApplication.introShownThisProcess = true
                        introVisible = true
                    }
                        val introAlpha by animateFloatAsState(
                            targetValue = if (introVisible) 1f else 0f,
                            animationSpec = tween(350),
                            label = "introAlphaIn"
                        )
                        var introExiting by remember { mutableStateOf(false) }
                        val exitAlpha by animateFloatAsState(
                            targetValue = if (introExiting) 0f else 1f,
                            animationSpec = tween(420),
                            finishedListener = { if (it == 0f) showIntro = false },
                            label = "introAlphaOut"
                        )
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(2150)
                            introExiting = true
                        }

                        // â”€â”€ CRYO IGNITION â€” framer-motion grammar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        // 1) draw-on powerline (pathLength 0â†’1, easeOutExpo)
                        // 2) core bloom + radial particle burst (staggered
                        //    children off one timeline driver)
                        // 3) CYROSONIC letters flip in on rotationX with spring
                        //    stagger + blur-to-sharp (whileInView style)
                        // 4) one-time sheen sweeps the wordmark
                        // 5) tagline rises late; zoom-through dissolve into app
                        val density = androidx.compose.ui.platform.LocalDensity.current

                        // Timeline driver #1: ignition (line + bloom + burst)
                        val ignite = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(60)
                            ignite.animateTo(
                                1f,
                                tween(950, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
                            )
                        }

                        // Wordmark gate: flips true once letters begin cascading
                        var lettersGo by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(560)
                            lettersGo = true
                        }

                        // Sheen sweep â€” one-shot across the wordmark
                        val sheen = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1250)
                            sheen.animateTo(
                                1f,
                                tween(750, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
                            )
                        }

                        // Zoom-through exit scale
                        val exitScale by animateFloatAsState(
                            targetValue = if (introExiting) 1.055f else 1f,
                            animationSpec = tween(420, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                            label = "exitZoom"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = introAlpha * exitAlpha
                                    scaleX = exitScale
                                    scaleY = exitScale
                                }
                                .background(Color(0xFF000000))
                        ) {
                            val igniteV = ignite.value

                            // â”€â”€ Core bloom (behind everything) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                            Box(
                                modifier = Modifier
                                    .size(300.dp)
                                    .align(Alignment.Center)
                                    .graphicsLayer {
                                        val appear = ((igniteV - 0.22f) / 0.78f).coerceIn(0f, 1f)
                                        alpha = 0.85f * appear * exitAlpha
                                        scaleX = 0.4f + 0.6f * appear
                                        scaleY = 0.4f + 0.6f * appear
                                    }
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color(0xFFA5F3FC).copy(alpha = 0.42f),
                                                Color(0xFF7DD3FC).copy(alpha = 0.10f),
                                                Color.Transparent
                                            )
                                        ),
                                        CircleShape
                                    )
                            )

                            // â”€â”€ Radial particle burst (Canvas, seeded) â”€â”€â”€â”€
                            androidx.compose.foundation.Canvas(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = exitAlpha }
                            ) {
                                if (igniteV <= 0.15f) return@Canvas
                                val progress = ((igniteV - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                val eased = 1f - (1f - progress) * (1f - progress) // easeOutQuad
                                val seed = java.util.Random(7)
                                val count = 26
                                val maxDist = size.minDimension * 0.42f
                                repeat(count) { i ->
                                    val angle = seed.nextFloat() * 2f * Math.PI.toFloat()
                                    val dist = (0.25f + seed.nextFloat() * 0.75f) * maxDist * eased
                                    val r = (1.2f + seed.nextFloat() * 2.4f) * density.density
                                    val fade = (1f - eased).coerceIn(0f, 1f)
                                    drawCircle(
                                        color = Color(0xFFA5F3FC).copy(alpha = 0.65f * fade),
                                        radius = r,
                                        center = androidx.compose.ui.geometry.Offset(
                                            center.x + dist * kotlin.math.cos(angle),
                                            center.y + dist * kotlin.math.sin(angle)
                                        )
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // â”€â”€ App mark: pop + gentle breathing â”€â”€â”€â”€â”€â”€
                                val markPop = remember { androidx.compose.animation.core.Animatable(0f) }
                                LaunchedEffect(Unit) {
                                    kotlinx.coroutines.delay(240)
                                    markPop.animateTo(
                                        1f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(128.dp)
                                        .graphicsLayer {
                                            scaleX = markPop.value
                                            scaleY = markPop.value
                                            alpha = markPop.value.coerceIn(0f, 1f) * exitAlpha
                                        }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // â”€â”€ Wordmark: rotationX flip cascade + blur-in â”€â”€
                                Box {
                                    Row {
                                        "CYROSONIC".forEachIndexed { ci, ch ->
                                            var letterGo by remember { mutableStateOf(false) }
                                            LaunchedEffect(Unit) {
                                                kotlinx.coroutines.delay(560L + ci * 55L)
                                                letterGo = true
                                            }
                                            val letterAnim = remember { androidx.compose.animation.core.Animatable(0f) }
                                            LaunchedEffect(letterGo) {
                                                if (letterGo && letterAnim.value == 0f) {
                                                    letterAnim.animateTo(
                                                        1f,
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                }
                                            }
                                            val blurPx = remember { androidx.compose.animation.core.Animatable(14f) }
                                            LaunchedEffect(letterGo) {
                                                if (letterGo) {
                                                    blurPx.animateTo(0f, tween(340))
                                                }
                                            }
                                            val t = letterAnim.value
                                            Text(
                                                text = ch.toString(),
                                                color = Color.White,
                                                fontSize = 34.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = com.example.hunterxmusic.theme.CryoDisplay,
                                                modifier = Modifier
                                                    .graphicsLayer {
                                                        alpha = t.coerceIn(0f, 1f) * exitAlpha
                                                        translationY = (1f - t) * 18f
                                                        // framer-style rotateX flip
                                                        rotationX = -80f * (1f - t)
                                                        cameraDistance = 12f * density.density
                                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                                            pivotFractionX = 0.5f,
                                                            pivotFractionY = 1f
                                                        )
                                                    }
                                                    .blur(blurPx.value.dp)
                                                    .padding(end = 6.dp)
                                            )
                                        }
                                    }

                                    // â”€â”€ One-shot sheen sweeping the wordmark â”€â”€
                                    if (sheen.value > 0f && sheen.value < 1f) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .graphicsLayer {
                                                    val travel = 320.dp.toPx()
                                                    translationX = -travel + sheen.value * 2f * travel
                                                    alpha = exitAlpha
                                                }
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            Color.White.copy(alpha = 0.28f),
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // â”€â”€ Tagline: late rise â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                                var tagGo by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    kotlinx.coroutines.delay(1180)
                                    tagGo = true
                                }
                                val tagT by animateFloatAsState(
                                    targetValue = if (tagGo) 1f else 0f,
                                    animationSpec = tween(520, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
                                    label = "tagRise"
                                )
                                Text(
                                    text = "cosmic chill Â· pure sound",
                                    color = Color(0xFF71717A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 2.5.sp,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = tagT * exitAlpha
                                        translationY = (1f - tagT) * 12f
                                    }
                                )

                                // â”€â”€ Draw-on powerline under the lockup â”€â”€â”€â”€
                                Box(
                                    modifier = Modifier
                                        .padding(top = 14.dp)
                                        .width(160.dp)
                                        .height(1.dp)
                                        .graphicsLayer {
                                            alpha = exitAlpha
                                        }
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color(0xFFA5F3FC).copy(alpha = 0.9f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                        .graphicsLayer {
                                            scaleX = igniteV.coerceIn(0f, 1f)
                                        }
                                )
                            }
                        }
                    }

                    // First-launch onboarding: living intro asks name (+ optional age).
                    // Rendered after the intro block, but gated on !showIntro so
                    // the opening animation is fully visible on first launch
                    // before the name/age dialog appears. On every later launch
                    // showOnboardingOverlay is false (isOnboarded), so only the
                    // intro replays â€” exactly "always like first time" without
                    // re-asking.
                    if (showOnboardingOverlay) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { },
                            properties = androidx.compose.ui.window.DialogProperties(
                                usePlatformDefaultWidth = false,
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false
                            )
                        ) {
                            com.example.hunterxmusic.presentation.onboarding.OnboardingScreen(
                                userProfile = deps.userProfileManager,
                                onFinished = {
                                    onboardedName = deps.userProfileManager.displayName
                                    showOnboardingOverlay = false
                                    val chosenCountry = deps.userProfileManager.country.ifBlank { "India" }
                                    deps.musicRepository.setPreferredLanguage(chosenCountry)
                                    val matched = com.example.hunterxmusic.presentation.home.SUPPORTED_COUNTRIES.firstOrNull {
                                        it.name.equals(chosenCountry, ignoreCase = true) || it.id.equals(chosenCountry, ignoreCase = true)
                                    }
                                    if (matched != null) {
                                        homeViewModel.onCountrySelected(matched)
                                    }
                                }
                            )
                        }
                    }

                    val scale by animateFloatAsState(
                        targetValue = if (animateIn) 1f else 0.94f,
                        animationSpec = tween(550, easing = LinearOutSlowInEasing),
                        label = "EntranceScale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (animateIn) 1f else 0f,
                        animationSpec = tween(450, easing = LinearOutSlowInEasing),
                        label = "EntranceAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                alpha = alpha
                            )
                    ) {
                        Scaffold(
                            bottomBar = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Sleek AMOLED MiniPlayer Capsule â€” now with fluid shared-element handoff to full player
                                    AnimatedVisibility(
                                        visible = hasActiveTrack && !isPlayerExpanded,
                                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                    ) {
                                        MiniPlayerBar(
                                            playbackState = playbackState,
                                            onTap = { isPlayerExpanded = true },
                                            onPlayPause = { playerViewModel.togglePlayPause() },
                                            onNext = { playerViewModel.skipToNext() },
                                            onPrevious = { playerViewModel.skipToPrevious() },
                                            onDoubleClickLike = { playbackState.currentTrack?.let { playerViewModel.toggleLike(it) } },
                                            modifier = Modifier.fillMaxWidth(),
                                            sharedTransitionScope = sharedScope,
                                            animatedVisibilityScope = this
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = !isPlayerExpanded,
                                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                    ) {
                                        AnimatedBottomNavBar(
                                            currentTab = currentTab,
                                            onTabSelect = { currentTab = it }
                                        )
                                    }
                                }
                            },
                            containerColor = HunterBackground,
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(HunterBackground)
                                    .padding(innerPadding)
                            ) {
                                AnimatedContent(
                                    targetState = currentTab,
                                    transitionSpec = {
                                        val direction = if (targetState.ordinal > initialState.ordinal) {
                                            AnimatedContentTransitionScope.SlideDirection.Left
                                        } else {
                                            AnimatedContentTransitionScope.SlideDirection.Right
                                        }
                                        (fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) + slideIntoContainer(
                                            towards = direction,
                                            animationSpec = spring(
                                                dampingRatio = NocturneMotion.SMOOTH_DAMPING,
                                                stiffness = NocturneMotion.SMOOTH_STIFFNESS
                                            )
                                        )).togetherWith(
                                            fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) + slideOutOfContainer(
                                                towards = direction,
                                                animationSpec = spring(
                                                    dampingRatio = NocturneMotion.SMOOTH_DAMPING,
                                                    stiffness = NocturneMotion.SMOOTH_STIFFNESS
                                                )
                                            )
                                        )
                                    },
                                    label = "TabTransition"
                                ) { targetTab ->
                                    LaunchedEffect(targetTab) {
                                        val focus = when (targetTab) {
                                            MainTab.Home -> com.example.hunterxmusic.core.network.ScreenFocus.HOME
                                            MainTab.Search, MainTab.Explore -> com.example.hunterxmusic.core.network.ScreenFocus.SEARCH
                                            MainTab.Library -> com.example.hunterxmusic.core.network.ScreenFocus.LIBRARY
                                            else -> com.example.hunterxmusic.core.network.ScreenFocus.HOME
                                        }
                                        com.example.hunterxmusic.core.network.NetworkFocusManager.setScreenFocus(focus)
                                    }

                                    when (targetTab) {
                                        MainTab.Home -> {
                                            HomeScreen(
                                                viewModel = homeViewModel,
                                                onTrackClick = onTrackClick,
                                                onTrackListClick = onTrackListClick,
                                                onMoodClick = { moodKey ->
                                                    // Vibe tiles open curated mood folders, not raw search dumps
                                                    openMoodKey = moodKey
                                                },
                                                onSearchClick = { currentTab = MainTab.Search },
                                                onArtistClick = { artist ->
                                                    selectedArtistName = artist
                                                    showArtistPortal = true
                                                },
                                                onRecentlyPlayedClick = { track ->
                                                    onTrackClick(track)
                                                },
                                                onOwnerTrigger = { showOwnerPasscodeDialog = true },
                                                nowPlayingArtUrl = playbackState.currentTrack?.albumArtUrl,
                                                greetingName = onboardedName,
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                        }
                                        MainTab.Search -> {
                                            SearchScreen(
                                                viewModel = searchViewModel,
                                                onTrackClick = { track ->
                                                    playerViewModel.playTrackWithTasteRadio(track)
                                                    isPlayerExpanded = true
                                                },
                                                onTrackListClick = onTrackListClick,
                                                onMoodFolderOpen = { moodKey -> openMoodKey = moodKey },
                                                // Was never passed, so the
                                                // "tap for the full hub" artist
                                                // card in search results fired
                                                // an empty lambda and looked
                                                // broken.
                                                onArtistClick = { artist ->
                                                    selectedArtistName = artist
                                                    showArtistPortal = true
                                                },
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                        }
                                        MainTab.Explore -> {
                                            com.example.hunterxmusic.presentation.explore.ExploreScreen(
                                                viewModel = exploreViewModel,
                                                onTrackListClick = onTrackListClick,
                                                onMoodFolderOpen = { moodKey -> openMoodKey = moodKey },
                                                onBrowseQuery = { query ->
                                                    currentTab = MainTab.Search
                                                    searchViewModel.onQueryChanged(query)
                                                    searchViewModel.search()
                                                },
                                                onArtistClick = { artist ->
                                                    selectedArtistName = artist
                                                    showArtistPortal = true
                                                },
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@AnimatedContent,
                                                onPlayTrackWithTasteRadio = { track ->
                                                    playerViewModel.playTrackWithTasteRadio(track)
                                                }
                                            )
                                        }
                                        MainTab.Library -> {
                                            LibraryScreen(
                                                viewModel = libraryViewModel,
                                                onSettingsClick = { showSettingsOverlay = true },
                                                onTrackListClick = onTrackListClick,
                                                onDeleteTrack = { track -> libraryViewModel.deleteTrack(track) },
                                                onEnqueueTrack = { track -> playerViewModel.enqueueTrack(track) },
                                                onOpenPlaylist = { playlistId, _ ->
                                                    openPlaylistId = playlistId
                                                },
                                                localLibrary = deps.localLibraryManager,
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Full-screen Player Overlay (Slides up seamlessly when tapping track or miniplayer)
                        AnimatedVisibility(
                            visible = isPlayerExpanded,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(280, easing = FastOutSlowInEasing)
                            ) + fadeOut(),
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                )
                        ) {
                            PlayerScreen(
                                playerViewModel = playerViewModel,
                                onBack = { isPlayerExpanded = false },
                                onArtistClick = { artistName ->
                                    selectedArtistName = artistName
                                    showArtistPortal = true
                                    isPlayerExpanded = false
                                },
                                onAddToPlaylist = { track -> addToPlaylistTrack = track },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // AI Chat overlay
                        AnimatedVisibility(
                            visible = showAiChat,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            AiChatView(
                                viewModel = aiChatViewModel,
                                onBack = { showAiChat = false }
                            )
                        }

                        // Artist Portal overlay
                        AnimatedVisibility(
                            visible = showArtistPortal,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            ArtistPortalView(
                                artistName = selectedArtistName,
                                onArtistClick = { artist ->
                                    selectedArtistName = artist
                                },
                                musicRepository = dependencies.musicRepository,
                                onTrackListClick = onTrackListClick,
                                onBack = { showArtistPortal = false },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this
                            )
                        }

                        // Owner Admin Portal overlay
                        AnimatedVisibility(
                            visible = showOwnerPortal,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            com.example.hunterxmusic.presentation.owner.OwnerAdminPortalView(
                                onBack = { showOwnerPortal = false }
                            )
                        }

                        // Mood Folder overlay (curated vibe collections)
                        val activeMood = openMoodKey?.let { com.example.hunterxmusic.presentation.mood.findMoodFolder(it) }
                        AnimatedVisibility(
                            visible = activeMood != null,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            activeMood?.let { mood ->
                                com.example.hunterxmusic.presentation.mood.MoodFolderScreen(
                                    folder = mood,
                                    musicRepository = deps.musicRepository,
                                    onBack = { openMoodKey = null },
                                    onTrackListClick = onTrackListClick,
                                    onAddToPlaylist = { track -> addToPlaylistTrack = track }
                                )
                            }
                        }

                        // Playlist detail overlay
                        AnimatedVisibility(
                            visible = openPlaylistId != null,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            openPlaylistId?.let { playlistId ->
                                com.example.hunterxmusic.presentation.playlist.PlaylistDetailScreen(
                                    playlistId = playlistId,
                                    playlistName = "",
                                    localLibrary = deps.localLibraryManager,
                                    onBack = { openPlaylistId = null },
                                    onTrackListClick = { tracks, idx ->
                                        onTrackListClick(tracks, idx)
                                        isPlayerExpanded = true
                                    }
                                )
                            }
                        }

                        // Add-to-playlist bottom sheet
                        addToPlaylistTrack?.let { track ->
                            com.example.hunterxmusic.presentation.playlist.AddToPlaylistSheet(
                                track = track,
                                localLibrary = deps.localLibraryManager,
                                onDismiss = { addToPlaylistTrack = null }
                            )
                        }

                        // Ubiquitous Persistent MiniPlayer for all sub-screens (Playlists, Moods, Artists, AI Chat, etc.)
                        val isSubScreenOpen = openPlaylistId != null || openMoodKey != null || showArtistPortal || showAiChat || showOwnerPortal
                        AnimatedVisibility(
                            visible = isSubScreenOpen && hasActiveTrack && !isPlayerExpanded && !showSettingsOverlay,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(bottom = 6.dp)
                        ) {
                            MiniPlayerBar(
                                playbackState = playbackState,
                                onTap = { isPlayerExpanded = true },
                                onPlayPause = { playerViewModel.togglePlayPause() },
                                onNext = { playerViewModel.skipToNext() },
                                onPrevious = { playerViewModel.skipToPrevious() },
                                onDoubleClickLike = { playbackState.currentTrack?.let { playerViewModel.toggleLike(it) } },
                                modifier = Modifier.fillMaxWidth(),
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this
                            )
                        }

                        // Maintenance Mode Lock Screen Overlay (Active for all non-owner sessions)
                        if (cloudConfig.isMaintenanceMode && !showOwnerPortal) {
                            com.example.hunterxmusic.presentation.maintenance.MaintenanceLockOverlay(
                                config = cloudConfig,
                                onOwnerBypassTrigger = { showOwnerPasscodeDialog = true }
                            )
                        } else if (com.example.hunterxmusic.data.remote.AeroChaseCloudSyncManager.isUpdateBarrierActive(cloudConfig) && !showOwnerPortal) {
                            // Version-Gated Force Update Lock Screen Overlay
                            com.example.hunterxmusic.presentation.maintenance.ForceUpdateLockOverlay(
                                config = cloudConfig,
                                onOwnerBypassTrigger = { showOwnerPasscodeDialog = true }
                            )
                        }
                        
                        if (showSettingsOverlay) {
                            // Full-screen IN-WINDOW overlay instead of a
                            // Dialog: Compose Dialogs spin up a secondary
                            // window whose scroll/back plumbing collides with
                            // the OnBackInvokedCallback path on targetSdk 36 â€”
                            // the reported "scroll in Settings = crash".
                            // Same pattern as AI chat / artist portals, which
                            // scroll fine. Bonus: overlays can no longer open
                            // "behind" a dead dialog window.
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(HunterBackground)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "Settings",
                                            color = HunterTextPrimary,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = { showSettingsOverlay = false },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(HunterSurface)
                                                .size(38.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Close Settings",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    MoreScreen(
                                        // Settings must close FIRST. It renders
                                        // in its own window, so leaving it open
                                        // meant the AI and Credits screens
                                        // opened *behind* it and looked dead.
                                        onAiAssistantClick = {
                                            showSettingsOverlay = false
                                            showAiChat = true
                                        },
                                        onLanguageClick = {
                                            showSettingsOverlay = false
                                            currentTab = MainTab.Home
                                            homeViewModel.openCountryPicker()
                                        },
                                        onStorageClick = {
                                            val offlineCount = libraryViewModel.state.value.downloadedTracks.size
                                            Toast.makeText(this@MainActivity, "Storage: $offlineCount songs downloaded offline.", Toast.LENGTH_LONG).show()
                                        },
                                        isSponsorBlockEnabled = isSponsorBlockEnabled,
                                        onSponsorBlockToggle = { enabled ->
                                            deps.musicRepository.setSponsorBlockEnabled(enabled)
                                            isSponsorBlockEnabled = enabled
                                        },
                                        onCreditsClick = {
                                            showSettingsOverlay = false
                                            showCreditsScreen = true
                                        },
                                        onTimeMachineClick = {
                                            timeMachineStats = deps.musicRepository.getListeningStats()
                                            showTimeMachineDialog = true
                                        },
                                        onPlaylistImported = { playlistId ->
                                            showSettingsOverlay = false
                                            openPlaylistId = playlistId
                                        },
                                        localLibraryManager = deps.localLibraryManager,
                                        musicRepository = deps.musicRepository,
                                        okHttpClient = deps.okHttpClient,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (showCreditsScreen) {
                            val creditsVersion = remember {
                                try {
                                    @Suppress("DEPRECATION")
                                    packageManager.getPackageInfo(packageName, 0).versionName
                                } catch (_: Exception) { "CyroSonic" }
                            }
                            com.example.hunterxmusic.presentation.credits.CreditsScreen(
                                versionName = creditsVersion ?: "CyroSonic",
                                onBack = { showCreditsScreen = false }
                            )
                        }
                        if (showTimeMachineDialog) {
                            TimeMachineDialog(
                                stats = timeMachineStats,
                                onDismiss = { showTimeMachineDialog = false }
                            )
                        }

                        if (showOwnerPasscodeDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    showOwnerPasscodeDialog = false
                                    ownerPasscode = ""
                                },
                                containerColor = Color(0xFF141416),
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Owner Access Authorization",
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                text = {
                                    Column {
                                        Text(
                                            text = "Enter secret owner passcode to access the Broadcast & Notification Console:",
                                            color = HunterTextSecondary,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = ownerPasscode,
                                            onValueChange = { ownerPasscode = it },
                                            placeholder = { Text("Enter Passcode...", color = HunterTextHint) },
                                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color.White,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                                focusedContainerColor = HunterSurface,
                                                unfocusedContainerColor = HunterSurface
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val now = System.currentTimeMillis()
                                            if (now < ownerLockedUntil) {
                                                val secs = ((ownerLockedUntil - now) / 1000L).coerceAtLeast(1)
                                                Toast.makeText(this@MainActivity, "Too many attempts â€” wait $secs s.", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            // Constant-time digest comparison â€” the old
                                            // string == string check leaked timing.
                                            val candidate = sha256(OWNER_PASS_SALT + ownerPasscode)
                                            val equal = java.security.MessageDigest.isEqual(
                                                candidate.toByteArray(Charsets.US_ASCII),
                                                OWNER_PASSCODE_DIGEST.toByteArray(Charsets.US_ASCII)
                                            )
                                            if (equal) {
                                                ownerFailCount = 0
                                                showOwnerPasscodeDialog = false
                                                ownerPasscode = ""
                                                showOwnerPortal = true
                                                Toast.makeText(this@MainActivity, "👑 Owner Authorized.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                ownerFailCount++
                                                if (ownerFailCount >= 5) {
                                                    ownerLockedUntil = System.currentTimeMillis() + 60_000L
                                                    ownerFailCount = 0
                                                    Toast.makeText(this@MainActivity, "Too many attempts — locked for 60 seconds.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this@MainActivity, "Incorrect passcode.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Unlock", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showOwnerPasscodeDialog = false
                                        ownerPasscode = ""
                                    }) {
                                        Text("Cancel", color = HunterTextSecondary)
                                    }
                                }
                            )
                        }

                        // In-App Over-The-Air (OTA) APK Update Modal
                        val otaState by deps.otaUpdateManager.state.collectAsState()
                        if (otaState is OtaState.UpdateAvailable || otaState is OtaState.Downloading || otaState is OtaState.ReadyToInstall) {
                            OtaUpdateDialog(
                                otaState = otaState,
                                onUpdateClick = { info -> deps.otaUpdateManager.startDownloadAndInstall(info) },
                                onInstallClick = { file -> deps.otaUpdateManager.launchPackageInstaller(file) },
                                onDismiss = { deps.otaUpdateManager.dismiss() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleIncomingDeepLink(incomingIntent: Intent?) {
        val uri = incomingIntent?.data ?: return
        if ((uri.scheme == "https" && uri.host?.contains("cyrosonic.com") == true) || uri.scheme == "cyrosonic") {
            val pathSegments = uri.pathSegments
            val trackId = when {
                pathSegments.size >= 2 && pathSegments[0] in listOf("track", "song") -> pathSegments[1]
                uri.host == "track" -> pathSegments.firstOrNull()
                else -> null
            }
            if (!trackId.isNullOrBlank()) {
                lifecycleScope.launch {
                    try {
                        val tracks = dependencies.musicRepository.searchTracks(trackId)
                        val target = tracks.firstOrNull { it.id == trackId } ?: tracks.firstOrNull()
                        if (target != null) {
                            dependencies.musicPlayerManager.playTrack(target)
                            openPlayerRequested = true
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}

@Composable
private fun OtaUpdateDialog(
    otaState: OtaState,
    onUpdateClick: (OtaUpdateInfo) -> Unit,
    onInstallClick: (java.io.File) -> Unit,
    onDismiss: () -> Unit
) {
    val info = when (otaState) {
        is OtaState.UpdateAvailable -> otaState.info
        is OtaState.ReadyToInstall -> otaState.info
        else -> null
    }

    AlertDialog(
        onDismissRequest = { if (info?.forceUpdate != true) onDismiss() },
        containerColor = Color(0xFF10141E),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF06B6D4).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Update Available",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (info != null) {
                    Text(
                        text = "CyroSonic v${info.versionName}",
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (otaState is OtaState.Downloading) {
                    Text(
                        text = "Downloading CyroSonic update… ${otaState.progressPercent}%",
                        color = HunterTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { otaState.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF38BDF8),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                } else {
                    Text(
                        text = "What's new in this release:",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = info?.changelog ?: "• Performance & audio stability improvements",
                        color = HunterTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            when (otaState) {
                is OtaState.UpdateAvailable -> {
                    Button(
                        onClick = { onUpdateClick(otaState.info) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Download & Install", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                is OtaState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstallClick(otaState.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Install Now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (info?.forceUpdate != true && otaState !is OtaState.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text("Later", color = HunterTextSecondary)
                }
            }
        }
    )
}

private fun sha256(input: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private const val OWNER_PASS_SALT = "CrYoS0n!c-2026"
private const val OWNER_PASSCODE_DIGEST =
    "34cfdf9139c990768c464d1a9eff935bd5792b304e5fe17b3cb68abc5390120b"

@Composable
fun AnimatedBottomNavBar(
    currentTab: MainTab,
    onTabSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = MainTab.values()
    val selectedIndex = tabs.indexOf(currentTab)
    
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "TabPill"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HunterSurface)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tabWidth = maxWidth / tabs.size

            // Sliding background sleek capsule indicator (luxury dark cyan glow, no white box)
            Box(
                modifier = Modifier
                    .offset(x = tabWidth * animatedIndex)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = Color(0xFF7DD3FC).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF7DD3FC).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }

            // Tab items
            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "IconScale"
                    )

                    val context = androidx.compose.ui.platform.LocalContext.current

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                try {
                                    val view = (context as? android.app.Activity)?.window?.decorView
                                    view?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                } catch (e: Exception) {}
                                onTabSelect(tab)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val icon = when (tab) {
                            MainTab.Home -> if (isSelected) Icons.Default.Home else Icons.Outlined.Home
                            MainTab.Search -> if (isSelected) Icons.Default.Search else Icons.Outlined.Search
                            MainTab.Explore -> if (isSelected) Icons.Default.Explore else Icons.Outlined.Explore
                            MainTab.Library -> if (isSelected) Icons.Default.LibraryMusic else Icons.Outlined.LibraryMusic
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = tab.name,
                            tint = if (isSelected) Color.White else HunterTextHint,
                            modifier = Modifier
                                .size(19.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = tab.name,
                            color = if (isSelected) Color.White else HunterTextHint,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeMachineDialog(
    stats: com.example.hunterxmusic.domain.repository.ListeningStats,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.95f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(HunterBrand, HunterAccent)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Time Machine",
                            color = HunterTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Your Listening Journey Recap",
                            color = HunterAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = HunterTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Scrollable content for stats
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Total Songs Stats Card
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(110.dp)
                            .background(
                                Brush.sweepGradient(listOf(HunterBrand, HunterAccent, HunterBrand)),
                                shape = CircleShape
                            )
                            .padding(3.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black, CircleShape)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RollingCount(
                                    value = stats.totalSongs,
                                    color = HunterTextPrimary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Plays",
                                    color = HunterTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Top Artists Section
                    if (stats.topArtists.isNotEmpty()) {
                        Text(
                            text = "Top Artists",
                            color = HunterTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(bottom = 8.dp)
                        )
                        val maxArtistPlays = stats.topArtists.firstOrNull()?.second ?: 1
                        stats.topArtists.forEach { (artist, count) ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = artist,
                                        color = HunterTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$count plays",
                                        color = HunterAccent,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                AnimatedBar(
                                    fraction = count.toFloat() / maxArtistPlays,
                                    color = HunterAccent
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Top Tracks Section
                    if (stats.topSongs.isNotEmpty()) {
                        Text(
                            text = "Top Tracks",
                            color = HunterTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(bottom = 8.dp)
                        )
                        val maxSongPlays = stats.topSongs.firstOrNull()?.second ?: 1
                        stats.topSongs.forEach { (songEntry, count) ->
                            val cleanTitle = songEntry.substringAfter(" - ").trim()
                            val cleanArtist = songEntry.substringBefore(" - ").trim()
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cleanTitle,
                                            color = HunterTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (cleanArtist != songEntry) cleanArtist else "Unknown Artist",
                                            color = HunterTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$count plays",
                                        color = HunterBrand,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                AnimatedBar(
                                    fraction = count.toFloat() / maxSongPlays,
                                    color = HunterBrand
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
