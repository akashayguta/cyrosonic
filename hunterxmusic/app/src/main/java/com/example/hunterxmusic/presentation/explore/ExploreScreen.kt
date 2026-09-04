package com.example.hunterxmusic.presentation.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.presentation.home.DarkSectionHeader
import com.example.hunterxmusic.presentation.home.DarkTrackCard
import com.example.hunterxmusic.theme.*

/**
 * Explore - The high-octane discovery universe of HunterXMusic.
 * Featuring Spotlight Sonic Radar, Vibe Matrix Bento Grids,
 * Decades Time Machine, World Sound Radar, and Ranked Global Charts.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onTrackListClick: (List<Track>, Int) -> Unit,
    onMoodFolderOpen: (String) -> Unit,
    onBrowseQuery: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    onPlayTrackWithTasteRadio: ((Track) -> Unit)? = null
) {
    val state by viewModel.state

    LaunchedEffect(Unit) { viewModel.load() }

    val playTrack: (Track, List<Track>) -> Unit = { track, list ->
        if (onPlayTrackWithTasteRadio != null) {
            onPlayTrackWithTasteRadio(track)
        } else {
            val idx = list.indexOf(track).coerceAtLeast(0)
            onTrackListClick(if (list.isNotEmpty()) list else listOf(track), idx)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HunterBackground)
            .nocturneAurora()
    ) {
        // TOP HEADER BAR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "EXPLORE",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.horizontalGradient(
                            listOf(Color.White, Color(0xFF7DD3FC), Color(0xFFA855F7))
                        )
                    )
                )

                // Live Pulse Radar Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SONIC RADAR",
                        color = Color(0xFF7DD3FC),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Text(
                text = "Live charts, underground waves, decades & atmosphere portals",
                color = HunterTextSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // FILTER TABS
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
        ) {
            items(EXPLORE_FILTERS) { filter ->
                val isSelected = state.activeFilter == filter.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF9333EA)))
                            } else {
                                Brush.horizontalGradient(listOf(HunterSurface, HunterSurface))
                            }
                        )
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFFA855F7).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(14.dp)
                        )
                        .nClick(pressedScale = 0.95f) { viewModel.selectFilter(filter.id) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = filter.iconEmoji, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = filter.label,
                            color = if (isSelected) Color.White else HunterTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp)
            ) {
                if (state.isLoading) {
                    items(3) { ShimmerExploreBlock() }
                }

                val exploreError = state.errorMessage
                if (!state.isLoading && exploreError != null) {
                    item { ExploreErrorState(message = exploreError, onRetry = { viewModel.refresh() }) }
                }

                // 1. HERO SPOTLIGHT SONIC RADAR BILLBOARD
                if (state.spotlightTracks.isNotEmpty() && (state.activeFilter == "ALL" || state.activeFilter == "VIRAL")) {
                    item {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Spotlight Sonic Radar",
                                badge = "TRENDING LIVE"
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            items(state.spotlightTracks) { track ->
                                SpotlightBillboardCard(
                                    track = track,
                                    onPlay = { playTrack(track, state.spotlightTracks) }
                                )
                            }
                        }
                    }
                }

                // 2. VIBE MATRIX (Sensory Atmosphere Portals)
                if (state.activeFilter == "ALL" || state.activeFilter == "VIBES" || state.activeFilter == "CYBER") {
                    item {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Vibe Matrix",
                                badge = "ATMOSPHERE"
                            )
                        }
                    }
                    item {
                        VibeMatrixGrid(
                            items = if (state.activeFilter == "CYBER") {
                                VIBE_BENTO_ITEMS.filter { it.id == "cyber_phonk" || it.id == "club_euphoria" }
                            } else {
                                VIBE_BENTO_ITEMS
                            },
                            onVibeClick = { vibe ->
                                if (vibe.moodKey != null) {
                                    onMoodFolderOpen(vibe.moodKey)
                                } else {
                                    onBrowseQuery(vibe.query)
                                }
                            },
                            onPlayQuery = { query -> onBrowseQuery(query) }
                        )
                    }
                }

                // 3. WORLD SOUND RADAR (Global Sonic Atlas)
                if (state.activeFilter == "ALL" || state.activeFilter == "WORLD") {
                    item {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "World Sound Radar",
                                badge = "GLOBAL ATLAS"
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 22.dp)
                        ) {
                            items(WORLD_RADAR_ITEMS) { item ->
                                WorldRadarCard(
                                    item = item,
                                    onClick = { onBrowseQuery(item.query) }
                                )
                            }
                        }
                    }
                }

                // 4. DECADES TIME MACHINE
                if (state.activeFilter == "ALL" || state.activeFilter == "DECADES") {
                    item {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Time Machine",
                                badge = "SONIC NOSTALGIA"
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 22.dp)
                        ) {
                            items(DECADES_TIME_MACHINE) { decade ->
                                DecadesTapeCard(
                                    item = decade,
                                    onClick = { onBrowseQuery(decade.query) }
                                )
                            }
                        }
                    }
                }

                // 5. GLOBAL TOP 50 RANKED CHARTS
                if (state.charts.isNotEmpty() && (state.activeFilter == "ALL" || state.activeFilter == "VIRAL")) {
                    item {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Global Top 50 Chart",
                                badge = "HOTTEST NOW"
                            )
                        }
                    }
                    item {
                        RankedChartsShelf(
                            tracks = state.charts.take(12),
                            onTrackClick = { idx -> onTrackListClick(state.charts, idx) }
                        )
                    }
                }

                // 6. VIRAL & UNDERGROUND RADAR
                if (state.viralRadar.isNotEmpty() && (state.activeFilter == "ALL" || state.activeFilter == "VIRAL" || state.activeFilter == "CYBER")) {
                    item {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Viral & Underground Radar",
                                badge = "BREAKOUT"
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            items(state.viralRadar) { track ->
                                DarkTrackCard(
                                    track = track,
                                    onClick = {
                                        playTrack(track, state.viralRadar)
                                    },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                    }
                }

                // 7. FRESH NEW RELEASES
                if (state.newReleases.isNotEmpty() && (state.activeFilter == "ALL" || state.activeFilter == "VIRAL")) {
                    item {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Fresh New Releases",
                                badge = "NEW DROPS"
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            items(state.newReleases) { track ->
                                DarkTrackCard(
                                    track = track,
                                    onClick = {
                                        playTrack(track, state.newReleases)
                                    },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                    }
                }

                // 8. EXPLORE BY LANGUAGE
                if (state.activeFilter == "ALL" || state.activeFilter == "WORLD") {
                    item {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Explore by Language",
                                badge = "POLYGLOT"
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 22.dp)
                        ) {
                            items(EXPLORE_LANGUAGES) { tile ->
                                BrowsePod(
                                    emoji = tile.emoji,
                                    label = tile.label,
                                    onClick = { onBrowseQuery(tile.query) }
                                )
                            }
                        }
                    }
                }

                // 9. TOP ARTISTS UNIVERSE
                if (state.topArtists.isNotEmpty() && (state.activeFilter == "ALL")) {
                    item {
                        StaggerIn {
                            DarkSectionHeader(
                                title = "Your Sonic Universe",
                                badge = "TOP ARTISTS"
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            items(state.topArtists) { (artist, plays) ->
                                ArtistAvatarPod(
                                    artist = artist,
                                    plays = plays,
                                    onClick = { onArtistClick(artist) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Panoramic Billboard card with rich glassmorphic styling and 1-tap playback. */
@Composable
private fun SpotlightBillboardCard(
    track: Track,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(310.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0C1222))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.5f),
                        Color(0xFFA855F7).copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                RoundedCornerShape(22.dp)
            )
            .nClick(pressedScale = 0.97f) { onPlay() }
    ) {
        // High-res album backdrop
        AsyncImage(
            model = track.albumArtUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark Gradient Scrim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color(0xFF090D16).copy(alpha = 0.75f),
                            Color(0xFF030712).copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Badge & Waveform
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE SPOTLIGHT",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "1-TAP STREAM",
                    color = Color(0xFFA5B4FC),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom Row: Metadata + Play Button
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Glowing Play Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFA855F7))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/** Vibe Matrix Grid with luxury Bento cards. */
@Composable
private fun VibeMatrixGrid(
    items: List<VibeBentoCard>,
    onVibeClick: (VibeBentoCard) -> Unit,
    onPlayQuery: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { vibe ->
                    Box(modifier = Modifier.weight(1f)) {
                        VibeBentoCardView(
                            vibe = vibe,
                            onClick = { onVibeClick(vibe) }
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VibeBentoCardView(
    vibe: VibeBentoCard,
    onClick: () -> Unit
) {
    val gradientColors = vibe.gradientColors.map { Color(it) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        gradientColors.first().copy(alpha = 0.45f),
                        gradientColors.last().copy(alpha = 0.25f),
                        HunterSurface
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        gradientColors.first().copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .nClick(pressedScale = 0.96f) { onClick() }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = vibe.emoji, fontSize = 24.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = vibe.tag,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column {
                Text(
                    text = vibe.title,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = vibe.subtitle,
                    color = HunterTextSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Launch Portal",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** World Radar Card for global sonic exploration. */
@Composable
private fun WorldRadarCard(
    item: WorldRadarItem,
    onClick: () -> Unit
) {
    val accent = Color(item.accentHex)
    Box(
        modifier = Modifier
            .width(170.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.2f),
                        HunterSurface
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(accent.copy(alpha = 0.4f), Color.White.copy(alpha = 0.05f))),
                RoundedCornerShape(18.dp)
            )
            .nClick(pressedScale = 0.96f) { onClick() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = item.flag, fontSize = 26.sp)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Column {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.genre,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Decades Time Machine retro cassette card. */
@Composable
private fun DecadesTapeCard(
    item: DecadesItem,
    onClick: () -> Unit
) {
    val startColor = Color(item.gradientStartHex)
    val endColor = Color(item.gradientEndHex)

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(124.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        startColor.copy(alpha = 0.35f),
                        endColor.copy(alpha = 0.15f),
                        HunterSurface
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(startColor.copy(alpha = 0.5f), endColor.copy(alpha = 0.3f))),
                RoundedCornerShape(18.dp)
            )
            .nClick(pressedScale = 0.96f) { onClick() }
            .padding(13.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.era,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(startColor.copy(alpha = 0.3f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.yearSpan,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = item.description,
                    color = HunterTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = endColor,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Rewind & Stream",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Ranked Top 50 Shelf with metallic podium badges (#1 Gold, #2 Silver, #3 Bronze). */
@Composable
private fun RankedChartsShelf(
    tracks: List<Track>,
    onTrackClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tracks.forEachIndexed { index, track ->
            val rank = index + 1
            val rankColor = when (rank) {
                1 -> Color(0xFFFFD700) // Gold
                2 -> Color(0xFFE2E8F0) // Silver
                3 -> Color(0xFFCD7F32) // Bronze
                else -> HunterTextHint
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HunterSurface.copy(alpha = 0.8f))
                    .border(
                        1.dp,
                        if (rank <= 3) rankColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.04f),
                        RoundedCornerShape(14.dp)
                    )
                    .nClick(pressedScale = 0.98f) { onTrackClick(index) }
                    .padding(horizontal = 12.dp)
            ) {
                // Rank number
                Box(
                    modifier = Modifier.width(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        color = rankColor,
                        fontSize = if (rank <= 3) 14.sp else 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Artwork
                AsyncImage(
                    model = track.albumArtUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = HunterTextSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play icon
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = if (rank <= 3) rankColor else HunterTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Rounded pill for a browse category. */
@Composable
private fun BrowsePod(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HunterSurface)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .nClick(pressedScale = 0.96f) { onClick() }
            .padding(horizontal = 14.dp)
    ) {
        Text(text = emoji, fontSize = 17.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = HunterTextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Artist circle avatar pod with glowing ring. */
@Composable
private fun ArtistAvatarPod(
    artist: String,
    plays: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(86.dp)
            .nClick(pressedScale = 0.95f) { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                    )
                )
                .border(
                    1.5.dp,
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFF38BDF8),
                            Color(0xFFA855F7),
                            Color(0xFFEC4899),
                            Color(0xFF38BDF8)
                        )
                    ),
                    CircleShape
                )
        ) {
            Text(
                text = artist.take(1).uppercase(),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist,
            color = HunterTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$plays plays",
            color = HunterTextHint,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun ExploreErrorState(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = HunterTextHint,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            color = HunterTextSecondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .nClick(pressedScale = 0.96f) { onRetry() }
                .padding(horizontal = 22.dp, vertical = 9.dp)
        ) {
            Text("Retry", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Placeholder shelf shown while Explore loads. */
@Composable
private fun ShimmerExploreBlock() {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .nShimmer()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .nShimmer()
                )
            }
        }
    }
}
