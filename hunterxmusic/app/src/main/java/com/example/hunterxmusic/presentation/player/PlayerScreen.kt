package com.example.hunterxmusic.presentation.player

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import com.example.hunterxmusic.data.player.PlaybackState
import androidx.media3.common.Player
import com.example.hunterxmusic.presentation.lyrics.SyncedLyricsView
import com.example.hunterxmusic.presentation.onboarding.CosmicParticleField
import com.example.hunterxmusic.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    onAddToPlaylist: (com.example.hunterxmusic.domain.model.Track) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val playbackState by playerViewModel.playbackState.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val sleepTimerText by playerViewModel.sleepTimerText.collectAsState()
    val activeDownloads by playerViewModel.activeDownloads.collectAsState()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
    val vocalMode by playerViewModel.vocalMode.collectAsState()
    val acousticSyncOffsetMs by playerViewModel.acousticSyncOffsetMs.collectAsState()
    val track = playbackState.currentTrack
    val activeDownload = track?.let { activeDownloads[it.id] }
    val context = LocalContext.current

    var isLyricsExpanded by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }

    // Per-track remembered lyric calibration â€” the nudge you set once keeps
    // working every time this song plays.
    val lyricPrefs = remember { context.getSharedPreferences("lyric_offsets", android.content.Context.MODE_PRIVATE) }
    var activeLyricLine by remember { mutableStateOf("") }

    // Feed the home-screen widget with the current track + its lyric cues
    LaunchedEffect(lyrics, track?.id) {
        com.example.hunterxmusic.widget.HuntrWidget.setNowPlaying(
            title = track?.title.orEmpty(),
            artist = track?.artist.orEmpty(),
            cues = lyrics.map { it.timestampMs to it.words }
        )
        com.example.hunterxmusic.widget.HuntrWidget.pushUpdate(context)
    }
    var savedLyricOffsetMs by remember { mutableLongStateOf(0L) }
    var activeLyricsLang by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(track?.id) {
        savedLyricOffsetMs = track?.id?.let { lyricPrefs.getLong(it, 0L) } ?: 0L
    }

    // Dynamic color theming extracted from active artwork
    var vibrantColor by remember { mutableStateOf(Color(0xFF00FFCC)) }
    var dominantColor by remember { mutableStateOf(Color(0xFF7B2CFF)) }
    var darkMutedColor by remember { mutableStateOf(Color(0xFF0F0B18)) }

    // Animated dynamic theming â€” smooth color shifts when song changes
    val animatedVibrant by animateColorAsState(targetValue = vibrantColor, animationSpec = tween(650), label = "vibrantAnim")
    val animatedDominant by animateColorAsState(targetValue = dominantColor, animationSpec = tween(650), label = "domAnim")
    val animatedDarkMuted by animateColorAsState(targetValue = darkMutedColor, animationSpec = tween(650), label = "darkMutedAnim")

    LaunchedEffect(track?.id) {
        vibrantColor = Color(0xFF00FFCC)
        dominantColor = Color(0xFF7B2CFF)
        darkMutedColor = Color(0xFF0F0B18)
        val artUrl = track?.albumArtUrl
        if (!artUrl.isNullOrBlank()) {
            try {
                val loader = coil.ImageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(artUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is coil.request.SuccessResult) {
                    val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        // Palette extraction is CPU-heavy â€” keep it off the main thread
                        val palette = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            androidx.palette.graphics.Palette.from(bitmap).generate()
                        }
                        palette.getVibrantColor(0xFF00FFCC.toInt()).let { vibrantColor = Color(it) }
                        palette.getDominantColor(0xFF7B2CFF.toInt()).let { dominantColor = Color(it) }
                        palette.getDarkMutedColor(0xFF0F0B18.toInt()).let { darkMutedColor = Color(it) }
                    }
                }
            } catch (e: Exception) {
                // Keep default neon coloring on error
            }
        }
    }

    // Double-tap popping heart states
    var showHeartAnimation by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (showHeartAnimation) 1.6f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        finishedListener = { if (it > 0f) showHeartAnimation = false },
        label = "HeartScale"
    )
    val heartAlpha by animateFloatAsState(
        targetValue = if (showHeartAnimation) 0.9f else 0f,
        animationSpec = tween(350),
        label = "HeartAlpha"
    )

    // Pinch-zoom distraction free states
    var isDistractionFree by remember { mutableStateOf(false) }
    var scaleFactor by remember { mutableStateOf(1f) }

    // Glow pulse intensity
    val glowPulse by rememberInfiniteTransition(label = "ArtGlow").animateFloat(
        initialValue = 0.6f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08080C))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {} // Complete wall: intercepts all clicks so background screen is fully insulated
            )
    ) {
        // Layer 1: Extracted Dynamic Ambient Aurora
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedDominant.copy(alpha = 0.32f),
                            animatedDarkMuted.copy(alpha = 0.55f),
                            Color(0xFF08080C)
                        )
                    )
                )
        )

        // Layer 2: Lumea Volumetric Aura Backdrop
        com.example.hunterxmusic.theme.LumeaAuraBackdrop(
            modifier = Modifier.fillMaxSize(),
            primaryColor = animatedDominant,
            secondaryColor = animatedDarkMuted,
            intensity = 0.45f
        )

        // Layer 3: Lumea Cosmic Ambient Stardust
        com.example.hunterxmusic.theme.LumeaParticleField(
            modifier = Modifier.fillMaxSize(),
            particleCount = 32,
            accentColor = animatedDominant,
            isPlaying = playbackState.isPlaying
        )

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp
        val isCompact = screenHeight < 720

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isCompact) 16.dp else 22.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar (Hidden in Distraction-Free mode)
            AnimatedVisibility(
                visible = !isDistractionFree,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, "Close", tint = HunterTextPrimary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Now Playing", color = HunterTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (sleepTimerText != null) {
                            // Live sleep-timer countdown chip (was previously computed but never shown)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    tint = vibrantColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = sleepTimerText!!,
                                    color = vibrantColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (track != null) {
                            Text(track.album.ifBlank { "Single" }, color = HunterTextHint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Options", tint = HunterTextSecondary)
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            containerColor = HunterSurfaceVariant
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sleep Timer", color = HunterTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Bedtime, null, tint = HunterTextSecondary) },
                                onClick = {
                                    showOptionsMenu = false
                                    showSleepTimerDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Studio Equalizer & 3D", color = HunterTextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Tune, null, tint = vibrantColor) },
                                onClick = {
                                    showOptionsMenu = false
                                    showEqualizerSheet = true
                                }
                            )
                            if (track != null) {
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist", color = HunterTextPrimary) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = HunterTextSecondary) },
                                    onClick = {
                                        showOptionsMenu = false
                                        onAddToPlaylist(track)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Start Similar Mix", color = HunterTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = HunterTextSecondary) },
                                    onClick = {
                                        showOptionsMenu = false
                                        playerViewModel.startSimilarMix(track.title, track.artist)
                                        Toast.makeText(context, "Hunting similar sounds…", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Track", color = HunterTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Share, null, tint = HunterTextSecondary) },
                                    onClick = {
                                        showOptionsMenu = false
                                        try {
                                            val link = "https://cyrosonic.com/track/${track.id}"
                                            val shareText = buildString {
                                                append("🎵 Listen to \"${track.title}\" by ${track.artist} on CyroSonic:\n")
                                                append(link)
                                                append("\n\nGet CyroSonic: https://cyrosonic.com")
                                            }
                                            val shareIntent = Intent(Intent.ACTION_SEND)
                                                .setType("text/plain")
                                                .putExtra(Intent.EXTRA_TEXT, shareText)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(Intent.createChooser(shareIntent, "Share via CyroSonic").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Could not share", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isDistractionFree) 40.dp else 8.dp))

            // 2-Sided Center Deck: Page 0 = Photo Artwork | Page 1 = Live Synced Lyrics (Swipe to flip)
            val centerPagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                // Dynamic glow behind the active card â€” now animates with album palette
                if (!isDistractionFree && track != null) {
                    Box(
                        modifier = Modifier
                            .size(310.dp)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                scaleX = 1.15f
                                scaleY = 1.15f
                                alpha = glowPulse * 0.7f
                            }
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(animatedVibrant.copy(alpha = 0.5f), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    )
                }

                // Square deck â€” full width, 1:1, centered vertically so the
                // artwork reads as a big square cover instead of a stretched
                // letterbox on tall screens.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                HorizontalPager(
                    state = centerPagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                ) { page ->
                    if (page == 0) {
                        // SIDE 1: Flagship Animated Album Canvas (Spotify / Apple Music Style)
                        if (track != null) {
                            com.example.hunterxmusic.theme.AnimatedAlbumCanvas(
                                track = track,
                                isPlaying = playbackState.isPlaying,
                                vibrantColor = animatedVibrant,
                                dominantColor = animatedDominant,
                                onDoubleTapLike = {
                                    if (!track.isLiked) {
                                        playerViewModel.toggleLike(track)
                                    }
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (!isLyricsExpanded) {
                        // SIDE 2: Live Synced Lyrics (occupying exact same square spot).
                        // Gated while the fullscreen overlay is open â€” two live
                        // engines used to run at once (double position pollers,
                        // auto-scrolls AND doubled translation API traffic).
                        Box(modifier = Modifier.fillMaxSize()) {
                            SyncedLyricsView(
                                lyrics = lyrics,
                                playbackPositionMsProvider = { playbackState.currentPositionMs },
                                onLyricClick = { playerViewModel.seekTo(it) },
                                acousticSyncOffsetMs = acousticSyncOffsetMs,
                                initialSyncOffsetMs = savedLyricOffsetMs,
                                onSyncOffsetChanged = { offset ->
                                    savedLyricOffsetMs = offset
                                    track?.id?.let { id -> lyricPrefs.edit().putLong(id, offset).apply() }
                                },
                                onActiveLineChanged = { activeLyricLine = it },
                                translationPrefs = com.example.hunterxmusic.HunterApplication.dependencies.lyricsTranslationPrefs,
                                songKey = track?.id ?: "${track?.title}|${track?.artist}",
                                targetLang = activeLyricsLang,
                                onTargetLangChanged = { activeLyricsLang = it },
                                modifier = Modifier.fillMaxSize()
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                            ) {
                                // Fullscreen expand (top-right corner only â€” lyrics stay clean)
                                IconButton(
                                    onClick = { isLyricsExpanded = true },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Expand",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Share this line as a story card
                                IconButton(
                                    onClick = {
                                        val t = track ?: return@IconButton
                                        if (activeLyricLine.isBlank()) {
                                            Toast.makeText(context, "Wait for a line to light up", Toast.LENGTH_SHORT).show()
                                        } else {
                                            scope.launch {
                                                val ok = com.example.hunterxmusic.presentation.lyrics.shareLyricsCard(
                                                    context = context,
                                                    title = t.title,
                                                    artist = t.artist,
                                                    line = activeLyricLine,
                                                    artUrl = t.albumArtUrl
                                                )
                                                if (!ok) Toast.makeText(context, "Couldn't build the card", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share this line",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }

            // 2-Dot Page Indicator (Side 1: Photo | Side 2: Lyrics)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (centerPagerState.currentPage == 0) Color.White else Color.White.copy(alpha = 0.25f))
                        .size(width = if (centerPagerState.currentPage == 0) 18.dp else 6.dp, height = 6.dp)
                        .clickable { scope.launch { centerPagerState.animateScrollToPage(0) } }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (centerPagerState.currentPage == 1) Color.White else Color.White.copy(alpha = 0.25f))
                        .size(width = if (centerPagerState.currentPage == 1) 18.dp else 6.dp, height = 6.dp)
                        .clickable { scope.launch { centerPagerState.animateScrollToPage(1) } }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Track info + actions â€” swipe left/right to skip, double-tap artwork to like
            if (track != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(track.id) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                onDragEnd = {
                                    when {
                                        totalDrag < -80f -> playerViewModel.skipToNext()
                                        totalDrag > 80f -> playerViewModel.skipToPrevious()
                                    }
                                    totalDrag = 0f
                                }
                            )
                        }
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = HunterTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            color = HunterTextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onArtistClick(track.artist) }
                        )
                        if (activeDownload != null) {
                            val downloadedMb = activeDownload.downloadedBytes / (1024f * 1024f)
                            val totalMb = activeDownload.totalBytes / (1024f * 1024f)
                            val progressPercent = (activeDownload.progress * 100).toInt()
                            
                            Text(
                                text = if (totalMb > 0f) {
                                    "Downloading: ${"%.1f".format(downloadedMb)} MB / ${"%.1f".format(totalMb)} MB ($progressPercent%)"
                                } else {
                                    "Downloading..."
                                },
                                color = HunterBrand,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    if (!isDistractionFree) {
                        if (activeDownload != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(48.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { activeDownload.progress },
                                    color = HunterBrand,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                if (track.isDownloaded) {
                                    playerViewModel.deleteDownloadedTrack(track)
                                    Toast.makeText(context, "Removed from offline downloads", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()
                                    playerViewModel.downloadTrack(track)
                                }
                            }) {
                                Icon(
                                    imageVector = if (track.isDownloaded) Icons.Default.DeleteOutline else Icons.Default.Download,
                                    contentDescription = if (track.isDownloaded) "Delete Download" else "Download",
                                    tint = if (track.isDownloaded) Color(0xFFFF5252) else HunterTextSecondary
                                )
                            }
                        }
                        // Real-time Lyrics Translation Button
                        IconButton(onClick = {
                            scope.launch {
                                centerPagerState.animateScrollToPage(1)
                            }
                            val nextLang = when (activeLyricsLang) {
                                null -> "hi"
                                "hi" -> "en"
                                "en" -> "es"
                                else -> null
                            }
                            activeLyricsLang = nextLang
                            val label = when (nextLang) {
                                "hi" -> "Hindi"
                                "en" -> "English"
                                "es" -> "Spanish"
                                null -> "Off"
                                else -> nextLang.uppercase()
                            }
                            Toast.makeText(context, "Lyrics translation: $label", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Translate Lyrics",
                                tint = if (activeLyricsLang != null) vibrantColor else HunterTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = {
                            playerViewModel.toggleLike(track)
                        }) {
                            AnimatedHeartIcon(
                                isLiked = track.isLiked,
                                size = 24.dp
                            )
                        }
                        IconButton(onClick = {
                            try {
                                val link = "https://cyrosonic.com/track/${track.id}"
                                val shareText = buildString {
                                    append("🎵 Listen to \"${track.title}\" by ${track.artist} on CyroSonic:\n")
                                    append(link)
                                    append("\n\nGet CyroSonic: https://cyrosonic.com")
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, shareText)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(Intent.createChooser(shareIntent, "Share via CyroSonic").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not share", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = HunterTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Slack collector — controls stay docked to the bottom edge
            Spacer(modifier = Modifier.weight(1f))

            // Controls, seekbar (Hidden in distraction-free)
            AnimatedVisibility(
                visible = !isDistractionFree,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Up next — fills the space the taller waveform used to take
                    // and answers the question people actually have here.
                    val nextTrack = playbackState.queue.getOrNull(playbackState.queueIndex + 1)
                    if (nextTrack != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = HunterTextHint,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "UP NEXT",
                                color = HunterTextHint,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = "${nextTrack.title} · ${nextTrack.artist}",
                                color = HunterTextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Interactive Artist Discovery Tag (Apple / Spotify Style)
                    if (track != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, vibrantColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                    .clickable { onArtistClick(track.artist) }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = vibrantColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "DISCOVER ${track.artist.uppercase()}",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.7.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    // Seek bar — slim, classy line
                    SleekSeekBar(
                        currentMs = playbackState.currentPositionMs,
                        durationMs = playbackState.durationMs,
                        isBuffering = playbackState.isBuffering,
                        activeColor = vibrantColor,
                        onSeek = { playerViewModel.seekTo(it) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Main playback controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Shuffle
                        IconButton(onClick = { playerViewModel.toggleShuffle() }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Default.Shuffle,
                                "Shuffle",
                                tint = if (playbackState.shuffleEnabled) vibrantColor else HunterTextHint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Previous
                        IconButton(onClick = { playerViewModel.skipToPrevious() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Previous", tint = HunterTextPrimary, modifier = Modifier.size(38.dp))
                        }

                        // Play/Pause (large circle with morphing spring icon)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(HunterTextPrimary)
                                .nClick(pressedScale = 0.92f, onClick = { playerViewModel.togglePlayPause() })
                        ) {
                            AnimatedPlayPauseIcon(
                                isPlaying = playbackState.isPlaying,
                                tint = HunterBackground,
                                size = 36.dp
                            )
                        }

                        // Next
                        IconButton(onClick = { playerViewModel.skipToNext() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.SkipNext, "Next", tint = HunterTextPrimary, modifier = Modifier.size(38.dp))
                        }

                        // Repeat
                        IconButton(onClick = { playerViewModel.cycleRepeatMode() }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = when (playbackState.repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = when (playbackState.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> HunterTextHint
                                    else -> vibrantColor
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom action row (Queue, Speed, Karaoke, Lyrics Side Flip)
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // 1. Queue Sheet
                        IconButton(onClick = { showQueueSheet = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Queue",
                                tint = HunterTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 3. Playback Speed Selector
                        IconButton(onClick = { showSpeedDialog = true }) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = if (playbackSpeed != 1.0f) vibrantColor else HunterTextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                if (playbackSpeed != 1.0f) {
                                    Text(
                                        text = formatPlaybackSpeed(playbackSpeed),
                                        color = vibrantColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.offset(y = 12.dp)
                                    )
                                }
                            }
                        }

                        // 4. Karaoke toggle â€” plain two-state: full mix or vocals muted
                        IconButton(onClick = {
                            val newMode = playerViewModel.cycleVocalMode()
                            val msg = if (newMode == com.example.hunterxmusic.core.audio.AudioVocalMode.INSTRUMENTAL_ONLY) {
                                "🎸 Karaoke — vocals muted"
                            } else {
                                "🎵 Full mix restored"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            Box(contentAlignment = Alignment.Center) {
                                val karaokeOn = vocalMode == com.example.hunterxmusic.core.audio.AudioVocalMode.INSTRUMENTAL_ONLY
                                Icon(
                                    imageVector = if (karaokeOn) Icons.Default.MusicNote else Icons.Default.Mic,
                                    contentDescription = "Karaoke Mode",
                                    tint = if (karaokeOn) Color(0xFF00E5FF) else HunterTextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                if (karaokeOn) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E5FF))
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                        }

                        // 5. Synced Lyrics Flip (Slides center card to Lyrics page)
                        IconButton(onClick = {
                            scope.launch {
                                centerPagerState.animateScrollToPage(if (centerPagerState.currentPage == 0) 1 else 0)
                            }
                        }) {
                            Icon(
                                Icons.Default.Lyrics,
                                contentDescription = "Lyrics Side",
                                tint = if (centerPagerState.currentPage == 1) Color.White else HunterTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Full-screen expanded lyrics overlay (fullscreen button inside the lyrics page)
        AnimatedVisibility(
            visible = isLyricsExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            FullScreenLyricsView(
                track = track,
                lyrics = lyrics,
                playbackPositionMsProvider = { playbackState.currentPositionMs },
                isPlaying = playbackState.isPlaying,
                playbackDurationMs = playbackState.durationMs,
                isBuffering = playbackState.isBuffering,
                onLyricClick = { playerViewModel.seekTo(it) },
                acousticSyncOffsetMs = acousticSyncOffsetMs,
                initialSyncOffsetMs = savedLyricOffsetMs,
                onSyncOffsetChanged = { offset ->
                    savedLyricOffsetMs = offset
                    track?.id?.let { id -> lyricPrefs.edit().putLong(id, offset).apply() }
                },
                onPlayPause = { playerViewModel.togglePlayPause() },
                onSeek = { playerViewModel.seekTo(it) },
                onCollapse = { isLyricsExpanded = false },
                vibrantColor = vibrantColor,
                darkMutedColor = darkMutedColor,
                activeLyricsLang = activeLyricsLang,
                onTargetLangChanged = { activeLyricsLang = it },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Modal Queue Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = HunterSurface,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Play Queue",
                    color = HunterTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    itemsIndexed(playbackState.queue, key = { idx, qTrack -> "${qTrack.id}_$idx" }) { idx, qTrack ->
                        val isCurrent = idx == playbackState.queueIndex
                        com.example.hunterxmusic.presentation.common.SwipeableTrackRow(
                            track = qTrack,
                            onSwipeLeft = { playerViewModel.removeQueueItem(idx) },
                            onSwipeRight = { playerViewModel.enqueueTrack(qTrack) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerViewModel.playQueue(playbackState.queue, idx)
                                        showQueueSheet = false
                                    }
                                    .background(if (isCurrent) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = (idx + 1).toString(),
                                    color = if (isCurrent) vibrantColor else HunterTextHint,
                                    modifier = Modifier.width(30.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qTrack.title,
                                        color = if (isCurrent) vibrantColor else HunterTextPrimary,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = qTrack.artist,
                                        color = HunterTextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isCurrent) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = vibrantColor, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // In-App Studio Equalizer & 3D Spatial Audio Sheet
    if (showEqualizerSheet) {
        EqualizerSheet(
            audioFxManager = com.example.hunterxmusic.HunterApplication.dependencies.audioFxManager,
            onDismiss = { showEqualizerSheet = false }
        )
    }

    // Sleep Timer Choice Dialog
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Set Sleep Timer", color = Color.White) },
            containerColor = HunterSurface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        "Off" to 0,
                        "5 minutes" to 5,
                        "15 minutes" to 15,
                        "30 minutes" to 30,
                        "60 minutes" to 60
                    )
                    options.forEach { (label, value) ->
                        TextButton(
                            onClick = {
                                playerViewModel.startSleepTimer(value)
                                showSleepTimerDialog = false
                                if (value > 0) {
                                    Toast.makeText(context, "Timer set for $label", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, color = vibrantColor, fontSize = 16.sp, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Cancel", color = HunterTextSecondary)
                }
            }
        )
    }

    // Playback Speed Selector Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", color = Color.White) },
            containerColor = HunterSurface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                    options.forEach { speedVal ->
                        TextButton(
                            onClick = {
                                playerViewModel.setPlaybackSpeed(speedVal)
                                showSpeedDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (speedVal == 1.0f) "Normal (1x)" else formatPlaybackSpeed(speedVal),
                                color = if (playbackSpeed == speedVal) vibrantColor else HunterTextPrimary,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Cancel", color = HunterTextSecondary)
                }
            }
        )
    }
}

/**
 * Formats a playback-speed multiplier for the compact speed badge:
 * 2.0f -> "2x", 1.5f -> "1.5x", 0.75f -> "0.75x". Avoids the raw "1.0x"
 * float rendering and keeps the badge short so it can't clip the icon.
 */
private fun formatPlaybackSpeed(speed: Float): String {
    val value = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "${value}x"
}

/**
 * Seek bar with current position / duration time labels.
 */
@Composable
fun SeekBar(
    currentMs: Long,
    durationMs: Long,
    isBuffering: Boolean,
    activeColor: Color = HunterTextPrimary,
    onSeek: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val progress = if (durationMs > 0) {
        if (isDragging) dragPosition else (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { value ->
                isDragging = true
                dragPosition = value
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek((dragPosition * durationMs).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = HunterSeekInactive
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            val displayMs = if (isDragging) (dragPosition * durationMs).toLong() else currentMs
            Text(formatTime(displayMs), color = HunterTextHint, fontSize = 12.sp)
            if (isBuffering) {
                Text("Buffering...", color = activeColor, fontSize = 12.sp)
            }
            Text(formatTime(durationMs), color = HunterTextHint, fontSize = 12.sp)
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centiseconds = (ms % 1000) / 10
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}:${centiseconds.toString().padStart(2, '0')}"
}

/**
 * Slim, classy seek bar: a 3dp track that swells to 5dp while dragging, a small
 * dot at the playhead, and the times sitting quietly underneath. Replaces the
 * generated waveform, which was visual noise that never matched the real audio.
 */
@Composable
fun SleekSeekBar(
    currentMs: Long,
    durationMs: Long,
    isBuffering: Boolean,
    activeColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (durationMs > 0L) {
        if (isDragging) dragFraction
        else (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 5.dp else 3.dp,
        animationSpec = tween(160),
        label = "seekHeight"
    )
    val knobSize by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 9.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "seekKnob"
    )

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        BoxWithConstraints(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                // Generous touch target while the visible bar stays thin.
                .height(26.dp)
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (durationMs > 0L) onSeek((dragFraction * durationMs).toLong())
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    )
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0L) {
                            onSeek(((offset.x / size.width.toFloat()).coerceIn(0f, 1f) * durationMs).toLong())
                        }
                    }
                }
        ) {
            val barWidth = maxWidth

            // Inactive track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
            )

            // Elapsed
            Box(
                modifier = Modifier
                    .width(barWidth * fraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(if (isBuffering) Color.White.copy(alpha = 0.4f) else activeColor)
            )

            // Playhead
            Box(
                modifier = Modifier
                    .offset(x = (barWidth * fraction) - knobSize / 2)
                    .size(knobSize)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val shown = if (isDragging && durationMs > 0L) (dragFraction * durationMs).toLong() else currentMs
            Text(
                text = formatTime(shown),
                color = if (isDragging) Color.White else Color(0xFFD4D4D8),
                fontSize = 12.sp,
                fontWeight = if (isDragging) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = formatTime(durationMs),
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

/**
 * A stunning, premium full-screen layout for synced lyrics, featuring large scrolling text
 * and dedicated mini audio controls so the listener has instant control.
 */
@Composable
fun FullScreenLyricsView(
    track: com.example.hunterxmusic.domain.model.Track?,
    lyrics: List<com.example.hunterxmusic.domain.model.LyricLine>,
    playbackPositionMsProvider: () -> Long,
    isPlaying: Boolean,
    playbackDurationMs: Long,
    isBuffering: Boolean,
    onLyricClick: (Long) -> Unit,
    acousticSyncOffsetMs: Long = 0L,
    initialSyncOffsetMs: Long = 0L,
    onSyncOffsetChanged: (Long) -> Unit = {},
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit,
    vibrantColor: Color,
    darkMutedColor: Color,
    activeLyricsLang: String? = null,
    onTargetLangChanged: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        darkMutedColor,
                        darkMutedColor.copy(alpha = 0.95f),
                        HunterBackground
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                if (track != null) {
                    if (!track.albumArtUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.albumArtUrl,
                            contentDescription = null,
                            error = rememberVectorPainter(Icons.Default.MusicNote),
                            fallback = rememberVectorPainter(Icons.Default.MusicNote),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HunterSurfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HunterSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = HunterTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            color = vibrantColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Collapse Lyrics",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Synced Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SyncedLyricsView(
                    lyrics = lyrics,
                    playbackPositionMsProvider = playbackPositionMsProvider,
                    onLyricClick = onLyricClick,
                    acousticSyncOffsetMs = acousticSyncOffsetMs,
                    initialSyncOffsetMs = initialSyncOffsetMs,
                    onSyncOffsetChanged = onSyncOffsetChanged,
                    translationPrefs = com.example.hunterxmusic.HunterApplication.dependencies.lyricsTranslationPrefs,
                    songKey = track?.id ?: "${track?.title}|${track?.artist}",
                    targetLang = activeLyricsLang,
                    onTargetLangChanged = onTargetLangChanged,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mini controller at the bottom
            if (track != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    SeekBar(
                        currentMs = playbackPositionMsProvider(),
                        durationMs = playbackDurationMs,
                        isBuffering = isBuffering,
                        activeColor = vibrantColor,
                        onSeek = onSeek
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onPlayPause,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = HunterBackground,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bumps thumbnail URLs to their highest reliable resolution: gstatic art is
 * server-scaled (w800 always exists), ytimg covers step up hqdefault (480p)
 * to sddefault (640p+). maxresdefault is skipped â€” it 404s too often.
 */
internal fun hiResArtUrl(url: String?): String? {
    if (url.isNullOrBlank()) return url
    return when {
        url.contains("googleusercontent.com") -> url.replace(Regex("=w[0-9]+-h[0-9]+.*"), "=w800-h800")
        url.contains("i.ytimg.com/vi/") -> url.replace("hqdefault", "sddefault")
        else -> url
    }
}
