package com.example.hunterxmusic.presentation.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.theme.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onTrackClick: (Track) -> Unit,
    onTrackListClick: (List<Track>, Int) -> Unit,
    onMoodClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onRecentlyPlayedClick: (Track) -> Unit = {},
    onOwnerTrigger: () -> Unit = {},
    nowPlayingArtUrl: String? = null,
    greetingName: String? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state

    if (state.showCountryPicker) {
        androidx.activity.compose.BackHandler { viewModel.dismissCountryPicker() }
        CountryPickerScreen(
            currentCountry = state.selectedCountry,
            onCountrySelected = { viewModel.onCountrySelected(it) },
            onDismiss = { viewModel.dismissCountryPicker() }
        )
        return
    }

    val lazyListState = rememberLazyListState()
    val artGlowColor = rememberTrackGlowColor(nowPlayingArtUrl)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HunterBackground)
            .nocturneAurora()
            .dynamicArtGlow(artGlowColor)
    ) {
        // Sticky Header: Profile greeting, Region Pill & Search Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var brandTapCount by remember { mutableIntStateOf(0) }
                    var lastBrandTapTime by remember { mutableLongStateOf(0L) }

                    val profileContext = androidx.compose.ui.platform.LocalContext.current
                    val userName = greetingName
                        ?: remember {
                            profileContext.getSharedPreferences("user_profile", android.content.Context.MODE_PRIVATE)
                                .getString("first_name", "").orEmpty()
                        }
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    val salutation = when {
                        hour < 5 -> "Late night"
                        hour < 12 -> "Good morning"
                        hour < 17 -> "Good afternoon"
                        hour < 21 -> "Good evening"
                        else -> "Good night"
                    }
                    val greeting = if (userName.isBlank()) "CyroSonic" else "$salutation, $userName"

                    Text(
                        text = greeting,
                        fontSize = if (userName.isBlank()) 25.sp else 21.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.4.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            brush = Brush.horizontalGradient(
                                listOf(Color.White, Color(0xFF7DD3FC), Color(0xFFA5B4FC))
                            )
                        ),
                        modifier = Modifier.clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            val now = System.currentTimeMillis()
                            if (now - lastBrandTapTime < 800) {
                                brandTapCount++
                                if (brandTapCount >= 15) {
                                    brandTapCount = 0
                                    onOwnerTrigger()
                                }
                            } else {
                                brandTapCount = 1
                            }
                            lastBrandTapTime = now
                        }
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Country Selection Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(HunterSurface)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                            .clickable { viewModel.openCountryPicker() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.selectedCountry.flag,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = state.selectedCountry.name,
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Change Region",
                                tint = HunterTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar Pod
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HunterSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .nClick(pressedScale = 0.98f) { onSearchClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Search songs, artists, albums, lyrics...",
                        color = HunterTextHint,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Scrollable Content
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
            ) {
                // 1. HERO SPOTLIGHT / NOW SPINNING CARD (3D Depth)
                val spotlightTrack = state.spotlightTrack ?: state.quickPicks.firstOrNull() ?: state.trending.firstOrNull()
                if (spotlightTrack != null) {
                    item(key = "hero_spotlight") {
                        StaggerIn(delayMs = 100) {
                            HeroSpotlightCard(
                                track = spotlightTrack,
                                onPlay = {
                                    val list = listOf(spotlightTrack) + state.trending
                                    onTrackListClick(list, 0)
                                }
                            )
                        }
                    }
                }

                // 2. YOUTUBE MUSIC 3-PHASE SPEED DIAL (3 PAGES x 9 SONGS)
                item(key = "ytm_speed_dial_3phase") {
                    StaggerIn(delayMs = 150) {
                        YouTubeMusicThreePhaseSpeedDial(
                            state = state,
                            onTrackClick = onTrackClick
                        )
                    }
                }

                // 3. JUMP BACK IN / CONTINUE LISTENING
                if (state.recentTracks.isNotEmpty()) {
                    item(key = "continue_listening_header") {
                        StaggerIn(delayMs = 200) {
                            DarkSectionHeader(
                                title = "Jump Back In",
                                badge = "RESUME"
                            )
                        }
                    }
                    item(key = "continue_listening_row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 18.dp)
                        ) {
                            items(state.recentTracks) { track ->
                                DarkTrackCard(
                                    track = track,
                                    onClick = { onTrackListClick(state.recentTracks, state.recentTracks.indexOf(track)) },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                    }
                }

                // 4. MADE FOR YOU (Personalized Scored Mix)
                if (state.forYouRecommendations.isNotEmpty()) {
                    item(key = "made_for_you_header") {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Made For You",
                                badge = "SMART TASTE"
                            )
                        }
                    }
                    item(key = "made_for_you_row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 18.dp)
                        ) {
                            items(state.forYouRecommendations) { track ->
                                DarkTrackCard(
                                    track = track,
                                    onClick = { onTrackListClick(state.forYouRecommendations, state.forYouRecommendations.indexOf(track)) },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                    }
                }

                // 5. VIBE TILES QUICK SELECTOR
                item(key = "vibe_tiles") {
                    StaggerIn(delayMs = 250) {
                        DarkVibeGrid(onMoodClick = onMoodClick)
                    }
                }

                // 7. REGIONAL SPECIALS
                if (state.countrySongs.isNotEmpty()) {
                    item(key = "country_header") {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "${state.selectedCountry.flag} Top in ${state.selectedCountry.name}",
                                badge = "DISCOVERY"
                            )
                        }
                    }
                    item(key = "country_row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 18.dp)
                        ) {
                            items(state.countrySongs) { track ->
                                DarkTrackCard(
                                    track = track,
                                    onClick = { onTrackListClick(state.countrySongs, state.countrySongs.indexOf(track)) },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                    }
                }

                // 8. TRENDING CHARTS
                if (state.trending.isNotEmpty()) {
                    item(key = "trending_header") {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Top Charts & Trending",
                                badge = "VIRAL"
                            )
                        }
                    }
                    itemsIndexed(state.trending.take(20)) { index, track ->
                        DarkTrackListItem(
                            track = track,
                            rankNumber = index + 1,
                            onClick = { onTrackListClick(state.trending, index) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3D Spotlight Hero Card with radiant artwork aura and tactile glass play button.
 */
@Composable
private fun HeroSpotlightCard(
    track: Track,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .wanderlustCardTilt(maxTiltDeg = 5f, pressScale = 0.97f, onClick = onPlay)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1E1B4B).copy(alpha = 0.85f), Color(0xFF0F172A).copy(alpha = 0.95f))
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(listOf(Color(0xFF7DD3FC).copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))),
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1F2937))
            ) {
                AsyncImage(
                    model = track.albumArtUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF7DD3FC).copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SPOTLIGHT",
                        color = Color(0xFF7DD3FC),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = track.artist,
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7DD3FC))
                    .nClick(pressedScale = 0.92f) { onPlay() }
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * YouTube Music 3-Phase Speed Dial:
 * - 3 Swipeable Phases / Pages
 * - Phase 1: Most Listened (9 tracks)
 * - Phase 2: Recommended & Fresh Picks (9 tracks)
 * - Phase 3: Related to Favorite Artist / Track (9 tracks)
 * - Strictly excludes recently played tracks
 * - Shows dynamic theme badge on refresh
 */
private data class SpeedDialPhase(
    val title: String,
    val badge: String,
    val tracks: List<Track>
)

@Composable
private fun YouTubeMusicThreePhaseSpeedDial(
    state: HomeState,
    onTrackClick: (Track) -> Unit
) {
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val theme = state.currentHomeTheme

    val phases = listOf(
        SpeedDialPhase("🔥 Most Listened", "YOUR TOP PICKS", state.speedDialPhase1),
        SpeedDialPhase("⚡ Recommended", "FRESH DISCOVERY", state.speedDialPhase2),
        SpeedDialPhase("✨ " + state.speedDialPhase3Title, "TASTE ALGORITHM", state.speedDialPhase3)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Section Header with Theme Badge & Indicator Dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = theme.icon,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = "Speed dial",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Dynamic theme badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.primaryAccent.copy(alpha = 0.15f))
                        .border(1.dp, theme.primaryAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = theme.name,
                        color = theme.primaryAccent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // 3 Indicator Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (page in 0 until 3) {
                    val isSelected = pagerState.currentPage == page
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (isSelected) 18.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.2f)
                            )
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(page) }
                            }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Phase Tab Selector Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            phases.forEachIndexed { index, phase ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) theme.primaryAccent.copy(alpha = 0.22f)
                            else Color(0xFF131722)
                        )
                        .border(
                            1.dp,
                            if (isSelected) theme.primaryAccent.copy(alpha = 0.6f)
                            else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (index) {
                            0 -> "🔥 Most Listened"
                            1 -> "⚡ Recommended"
                            else -> "✨ Related"
                        },
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3-Phase Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val phaseTracks = phases.getOrNull(page)?.tracks.orEmpty()
            if (phaseTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D111A))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading speed dial picks…",
                        color = HunterTextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                // 3x3 Grid: 3 rows, each containing 3 tracks (total 9 tracks per page)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = phaseTracks.take(9).chunked(3)
                    for (rowTracks in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (track in rowTracks) {
                                SpeedDialGridItem(
                                    track = track,
                                    theme = theme,
                                    onTrackClick = onTrackClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowTracks.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedDialGridItem(
    track: Track,
    theme: HomeTheme,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1420))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .nClick(pressedScale = 0.95f) { onTrackClick(track) }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Play overlay badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = theme.primaryAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = track.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            color = HunterTextSecondary,
            fontSize = 9.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Circular artist avatar bubble with glowing border.
 */
@Composable
private fun ArtistAvatarBubble(
    artistName: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .nClick(pressedScale = 0.94f) { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF7DD3FC).copy(alpha = 0.4f), CircleShape)
        ) {
            Text(
                text = artistName.take(1).uppercase(),
                color = Color(0xFF7DD3FC),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artistName,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DarkSectionHeader(
    title: String,
    badge: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    color = Color(0xFF7DD3FC),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DarkTrackCard(
    track: Track,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .nClick(pressedScale = 0.95f) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF18181B))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
        ) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.artist,
            color = Color(0xFFA1A1AA),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DarkTrackListItem(
    track: Track,
    rankNumber: Int,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val rankBadgeColor = when (rankNumber) {
        1 -> Color(0xFFFFD700) // Heavenly Gold
        2 -> Color(0xFFE2E8F0) // Platinum Silver
        3 -> Color(0xFFF59E0B) // Radiant Bronze
        else -> Color(0xFF38BDF8) // Celestial Cyan
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0C101A))
            .border(1.dp, Color.White.copy(alpha = if (rankNumber <= 3) 0.12f else 0.04f), RoundedCornerShape(16.dp))
            .nClick(pressedScale = 0.97f) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Rank Indicator with Crown for #1
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(rankBadgeColor.copy(alpha = 0.15f))
                .border(1.dp, rankBadgeColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (rankNumber == 1) "👑" else rankNumber.toString(),
                color = rankBadgeColor,
                fontSize = if (rankNumber == 1) 12.sp else 12.5.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Tactile Play Arrow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play track",
                tint = rankBadgeColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

data class CelestialVibe(
    val title: String,
    val subtitle: String,
    val icon: String,
    val query: String,
    val gradientColors: List<Color>
)

@Composable
fun DarkVibeGrid(onMoodClick: (String) -> Unit) {
    val vibes = listOf(
        CelestialVibe("Cosmic Euphoria", "Late night neon & bass", "🌌", "lofi synthwave hits", listOf(Color(0xFF4338CA), Color(0xFF6D28D9))),
        CelestialVibe("Heavenly Peace", "Pure acoustic calm", "🕊️", "peaceful acoustic chill", listOf(Color(0xFF065F46), Color(0xFF0D9488))),
        CelestialVibe("High Voltage", "Workout adrenaline rush", "⚡", "workout hype phonk party", listOf(Color(0xFF991B1B), Color(0xFFD97706))),
        CelestialVibe("Starlight Drift", "Deep focus & study", "✨", "deep focus chill lofi", listOf(Color(0xFF1E293B), Color(0xFF334155)))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val rows = vibes.chunked(2)
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (vibe in row) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(vibe.gradientColors.map { it.copy(alpha = 0.55f) }))
                            .border(1.dp, vibe.gradientColors.first().copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .nClick(pressedScale = 0.95f) { onMoodClick(vibe.query) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = vibe.icon, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = vibe.title,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = vibe.subtitle,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CountryPickerScreen(
    currentCountry: CountryInfo,
    onCountrySelected: (CountryInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080C))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Select Music Region",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(SUPPORTED_COUNTRIES) { country ->
                val isSelected = country.id == currentCountry.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF111116))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onCountrySelected(country) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(text = country.flag, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = country.name,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = country.defaultSearchQuery,
                            color = Color(0xFFA1A1AA),
                            fontSize = 11.5.sp,
                            maxLines = 1
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color(0xFF7DD3FC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
