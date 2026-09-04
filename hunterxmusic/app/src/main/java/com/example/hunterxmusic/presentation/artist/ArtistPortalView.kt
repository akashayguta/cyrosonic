package com.example.hunterxmusic.presentation.artist

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.domain.repository.MusicRepository
import com.example.hunterxmusic.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Simple 4-tuple for parallel catalog fetches. */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ArtistPortalView(
    artistName: String,
    musicRepository: MusicRepository,
    onTrackListClick: (List<Track>, Int) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var popularTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var latestTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Pair<String, List<Track>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artistName) {
        isLoading = true
        try {
            // Parallel multi-query catalog sweep — a single "top hits" search
            // only ever surfaced ~25 tracks; four queries capture the real
            // discography like a proper artist page.
            val (topHits, latest, allSongs, albumSongs) = coroutineScope {
                val a = async { musicRepository.searchTracks("$artistName top hits songs") }
                val b = async { musicRepository.searchTracks("$artistName latest songs new release") }
                val c = async { musicRepository.searchTracks("$artistName all songs") }
                val d = async { musicRepository.searchTracks("$artistName album songs") }
                Quad(a.await(), b.await(), c.await(), d.await())
            }

            // Fame ranking: a song that surfaces in MORE queries (and earlier
            // within the "top hits" query) is objectively more famous — hits
            // appear across top-hits/all-songs/album lists, deep cuts don't.
            val queryResults = listOf(topHits, allSongs, albumSongs, latest)
            val fame = HashMap<String, Float>()
            queryResults.forEach { result ->
                result.forEachIndexed { position, track ->
                    val bonus = (result.size - position).coerceAtLeast(0) / 100f
                    fame[track.id] = (fame[track.id] ?: 0f) + 1f + bonus
                }
            }

            val combined = (topHits + allSongs + albumSongs + latest)
                .distinctBy { it.id }

            val artistFiltered = combined.filter {
                it.artist.contains(artistName, ignoreCase = true) ||
                it.title.contains(artistName, ignoreCase = true) ||
                artistName.contains(it.artist, ignoreCase = true)
            }.ifEmpty { combined }
                .sortedByDescending { fame[it.id] ?: 0f }

            tracks = artistFiltered
            popularTracks = artistFiltered.take(10)
            latestTracks = artistFiltered.drop(10).take(30)

            // Group by album metadata (YouTube search fills `album` for music
            // results) — Spotify-style album shelves on the artist page.
            albums = artistFiltered
                .filter { it.album.isNotBlank() && it.album != "Top Charts" }
                .groupBy { it.album }
                .filterValues { group -> group.size >= 2 }
                .map { (albumName, group) -> albumName to group }
                .sortedByDescending { (_, group) -> group.size }
                .take(6)
        } catch (_: Exception) {
            tracks = emptyList()
        }
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HunterBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Hero Artist Banner
            item {
                val heroImage = tracks.firstOrNull { !it.albumArtUrl.isNullOrBlank() }?.albumArtUrl
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    if (heroImage != null) {
                        AsyncImage(
                            model = heroImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Multi-stop cinematic gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        HunterBackground.copy(alpha = 0.6f),
                                        HunterBackground
                                    )
                                )
                            )
                    )

                    // Hero Content
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00F2FE).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF00F2FE).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = Color(0xFF00F2FE),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VERIFIED ARTIST",
                                color = Color(0xFF00F2FE),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = artistName,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "Curated Top Hits & Essential Discography",
                            color = HunterTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Action Buttons (Play All, Shuffle)
            if (tracks.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onTrackListClick(tracks, 0) },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00F2FE)
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { 
                                    val shuffled = tracks.shuffled()
                                    onTrackListClick(shuffled, 0)
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = HunterTextPrimary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.1f)))
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, null, tint = HunterTextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle", color = HunterTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Text(
                            text = "${tracks.size} Songs",
                            color = HunterTextHint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Loading state
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00F2FE), strokeWidth = 2.5.dp)
                    }
                }
            } else if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No songs found for $artistName", color = HunterTextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                // Section: Top Hits
                item {
                    Text(
                        text = "Popular Hits",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }

                // Tracks with Ranking (#1, #2, ...)
                itemsIndexed(popularTracks) { index, track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackListClick(tracks, index) }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        // Rank Number
                        Text(
                            text = "${index + 1}",
                            color = if (index < 3) Color(0xFF00F2FE) else HunterTextHint,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(26.dp)
                        )

                        // Album Art
                        AsyncImage(
                            model = track.albumArtUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HunterSurfaceVariant),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        // Title & Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = HunterTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Play icon indicator
                        IconButton(onClick = { onTrackListClick(tracks, index) }) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = "Play",
                                tint = Color(0xFF00F2FE).copy(alpha = 0.8f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Section: Albums (grouped from catalog metadata)
                if (albums.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Albums & Collections",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }

                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp)
                        ) {
                            items(albums) { (albumName, albumTracks) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(128.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(HunterSurface)
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                                        .clickable { onTrackListClick(albumTracks, 0) }
                                        .padding(8.dp)
                                ) {
                                    AsyncImage(
                                        model = albumTracks.firstOrNull { !it.albumArtUrl.isNullOrBlank() }?.albumArtUrl,
                                        contentDescription = albumName,
                                        modifier = Modifier
                                            .size(112.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(HunterSurfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = albumName,
                                        color = HunterTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "${albumTracks.size} songs",
                                        color = HunterTextSecondary,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
                }

                // Section: More Releases & Discography

                if (latestTracks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "More Releases & Features",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }

                    itemsIndexed(latestTracks) { relIndex, track ->
                        val actualIndex = relIndex + popularTracks.size
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackListClick(tracks, actualIndex) }
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            AsyncImage(
                                model = track.albumArtUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HunterSurfaceVariant),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = HunterTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = HunterTextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Close Floating Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .size(38.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
