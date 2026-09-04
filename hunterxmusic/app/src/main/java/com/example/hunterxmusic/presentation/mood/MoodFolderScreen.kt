package com.example.hunterxmusic.presentation.mood

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * A curated vibe folder — like a system playlist. Multiple hand-picked
 * queries are merged and deduped so tapping "Late Night" opens a real,
 * browsable collection instead of one raw search dump.
 */
data class MoodFolder(
    val key: String,
    val title: String,
    val emoji: String,
    val tagline: String,
    val gradient: List<Color>,
    val queries: List<String>
)

val MOOD_FOLDERS = listOf(
    MoodFolder(
        "lofi", "Late Night & Lo-Fi", "🌙", "Slow beats for after midnight",
        listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A)),
        listOf("late night lofi beats", "lofi chill hip hop mix", "midnight relaxing music", "chillhop study beats")
    ),
    MoodFolder(
        "romantic", "Romantic", "💖", "Love songs in every language",
        listOf(Color(0xFF2A111E), Color(0xFF140A10)),
        listOf("romantic hindi songs", "love songs english hits", "romantic punjabi", "best love ballads")
    ),
    MoodFolder(
        "energy", "Workout Energy", "⚡", "Push one more rep",
        listOf(Color(0xFF2A200F), Color(0xFF14100A)),
        listOf("gym workout motivation songs", "hard bass trap workout", "edm energy boost mix", "hype hip hop gym")
    ),
    MoodFolder(
        "party", "Party & Club", "🎉", "Turn it up",
        listOf(Color(0xFF0F2033), Color(0xFF0A1420)),
        listOf("punjabi party dance hits", "club dance hits mix", "bollywood party mashup", "party anthems")
    ),
    MoodFolder(
        "sad", "Sad & Soul", "🌧️", "For the raining-inside days",
        listOf(Color(0xFF14141A), Color(0xFF0B0B10)),
        listOf("sad heartbreak songs", "sad hindi songs emotional", "acoustic sad covers", "melancholy indie")
    ),
    MoodFolder(
        "focus", "Focus & Study", "🧘", "Deep work soundtrack",
        listOf(Color(0xFF12211C), Color(0xFF0A1410)),
        listOf("deep focus study music", "instrumental concentration", "ambient work music", "peaceful piano focus")
    ),
    MoodFolder(
        "trending", "Trending Now", "🔥", "What everyone's playing",
        listOf(Color(0xFF2A1414), Color(0xFF140A0A)),
        listOf("trending songs", "viral hits right now", "global top hits", "new music this week")
    ),
    MoodFolder(
        "devotional", "Devotional & Calm", "🕉️", "Peace for the soul",
        listOf(Color(0xFF1F1A10), Color(0xFF100E08)),
        listOf("devotional songs bhajan", "peaceful mantra meditation", "sufi calm songs", "spiritual instrumental")
    )
)

fun findMoodFolder(key: String): MoodFolder? = MOOD_FOLDERS.firstOrNull { it.key == key }

/**
 * Full mood folder screen: gradient hero, play/shuffle, ranked track list
 * assembled from every curated query.
 */
@Composable
fun MoodFolderScreen(
    folder: MoodFolder,
    musicRepository: MusicRepository,
    onBack: () -> Unit,
    onTrackListClick: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(folder.key) {
        isLoading = true
        try {
            // Merge every curated query in parallel, dedupe — a real folder,
            // not one search's first page
            tracks = coroutineScope {
                folder.queries.map { query ->
                    async {
                        try { musicRepository.searchTracks(query) } catch (_: Exception) { emptyList<Track>() }
                    }
                }.awaitAll().flatten()
            }.distinctBy { it.id }.take(45)
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
            // Gradient hero
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(225.dp)
                        .background(Brush.verticalGradient(folder.gradient))
                ) {
                    val heroArt = tracks.firstOrNull { !it.albumArtUrl.isNullOrBlank() }?.albumArtUrl
                    if (heroArt != null) {
                        AsyncImage(
                            model = heroArt,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 0.35f }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.75f))))
                        )
                    } else if (!isLoading) {
                        Text(
                            text = folder.emoji,
                            fontSize = 54.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "${folder.emoji} ${folder.title}",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = folder.tagline,
                            color = HunterTextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            // Actions
            if (tracks.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = { onTrackListClick(tracks, 0) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = { onTrackListClick(tracks.shuffled(), 0) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HunterTextPrimary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, null, tint = HunterTextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", color = HunterTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${tracks.size} songs",
                            color = HunterTextHint,
                            fontSize = 12.sp
                        )
                    }
                }

                itemsIndexed(tracks, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .nClick(pressedScale = 0.98f) { onTrackListClick(tracks, index) }
                            .padding(horizontal = 20.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (index < 3) Color.White else HunterTextHint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(30.dp)
                        )
                        AsyncImage(
                            model = track.albumArtUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
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
                                fontWeight = FontWeight.SemiBold,
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
                        IconButton(onClick = { onAddToPlaylist(track) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add to playlist",
                                tint = HunterTextHint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else if (isLoading) {
                items(4) {
                    MoodShimmerRow()
                }
            } else {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudOff, null, tint = HunterTextHint, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Couldn't load this folder", color = HunterTextSecondary, fontSize = 14.sp)
                            Text("Check your connection and try again", color = HunterTextHint, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .size(38.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MoodShimmerRow() {
    val shimmerAlpha by rememberInfiniteTransition(label = "moodShimmer").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "moodShimmerAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HunterSurfaceVariant.copy(alpha = shimmerAlpha))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HunterSurfaceVariant.copy(alpha = shimmerAlpha))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HunterSurfaceVariant.copy(alpha = shimmerAlpha * 0.7f))
            )
        }
    }
}
