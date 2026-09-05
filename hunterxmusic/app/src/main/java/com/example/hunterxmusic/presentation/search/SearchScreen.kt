package com.example.hunterxmusic.presentation.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.theme.*

private val HOT_TRENDING_CHIPS = listOf(
    "Midnight Phonk",
    "Acoustic Chill",
    "Bollywood Hits 2026",
    "English Top Hits",
    "K-Pop Viral",
    "EDM Bass Drop",
    "Desi Hip-Hop",
    "Lofi Midnight Rain",
    "Gym Beast Mode",
    "Cyberpunk Synth"
)

private val DISCOVERY_MOOD_TILES = listOf(
    MoodFolderItem("Lofi Chill", "🌙", "lofi chill beats", listOf(Color(0xFF312E81), Color(0xFF1E1B4B))),
    MoodFolderItem("Romantic Echoes", "💖", "romantic hindi love songs", listOf(Color(0xFF831843), Color(0xFF500724))),
    MoodFolderItem("High Voltage", "⚡", "high energy workout trap", listOf(Color(0xFF854D0E), Color(0xFF451A03))),
    MoodFolderItem("Club Euphoria", "🎉", "party club dance edm hits", listOf(Color(0xFF9F1239), Color(0xFF4C0519))),
    MoodFolderItem("Focus Sanctuary", "🎧", "deep focus study beats ambient", listOf(Color(0xFF065F46), Color(0xFF064E3B))),
    MoodFolderItem("Cyberpunk 2077", "🌌", "synthwave phonk cyberpunk drift", listOf(Color(0xFF4C1D95), Color(0xFF2E1065))),
    MoodFolderItem("Desi Melodies", "🇮🇳", "trending bollywood melodies", listOf(Color(0xFFB45309), Color(0xFF78350F))),
    MoodFolderItem("Acoustic Sunset", "🎸", "mellow acoustic guitar unplugged", listOf(Color(0xFF374151), Color(0xFF1F2937)))
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onTrackClick: (Track) -> Unit,
    onTrackListClick: (List<Track>, Int) -> Unit,
    onMoodFolderOpen: (String) -> Unit,
    onArtistClick: (String) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                dismissKeyboard()
                viewModel.onQueryChanged(spokenText)
                viewModel.search()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HunterBackground)
            .nocturneAurora()
    ) {
        // Luxury AMOLED Search Header with glowing border
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(HunterSurface)
                    .border(
                        1.2.dp,
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF00F2FE).copy(alpha = 0.5f),
                                Color(0xFF8B5CF6).copy(alpha = 0.35f),
                                Color(0xFF00F2FE).copy(alpha = 0.2f)
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    placeholder = {
                        Text(
                            "Search songs, artists, albums, lyrics...",
                            color = HunterTextHint,
                            fontSize = 14.5.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF00F2FE),
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.query.isNotBlank()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Clear",
                                        tint = HunterTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak song or artist name...")
                                }
                                try { voiceLauncher.launch(intent) } catch (_: Exception) { }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Search",
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFF00F2FE),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        dismissKeyboard()
                        viewModel.search()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SearchCategory.values()) { category ->
                    val isSelected = state.selectedCategory == category
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) Brush.horizontalGradient(listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6)))
                                else Brush.linearGradient(listOf(HunterSurface, HunterSurface))
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF00F2FE).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                viewModel.onCategorySelected(category)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = category.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Live Suggestions While Typing
        if (state.suggestions.isNotEmpty() && state.query.isNotBlank()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(state.suggestions) { suggestion ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                dismissKeyboard()
                                viewModel.onSuggestionClick(suggestion)
                            }
                            .padding(horizontal = 24.dp, vertical = 13.dp)
                    ) {
                        Icon(Icons.Default.History, null, tint = HunterTextHint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(suggestion, color = HunterTextPrimary, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.NorthWest, null, tint = HunterTextHint, modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else if (state.hasSearched || state.isSearching) {
            // Search Results
            if (state.isSearching) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    repeat(7) {
                        LuxurySearchSkeletonRow()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            } else if (state.results.isEmpty() && state.error == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Color(0xFF00F2FE).copy(alpha = 0.6f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No matches found", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Try searching with another keyword, artist name, or song title",
                            color = HunterTextSecondary,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Top Result Spotlight Hero Card
                    val topMatch = state.results.firstOrNull()
                    if (topMatch != null) {
                        item(key = "top_match_spotlight") {
                            LuxuryTopResultSpotlightCard(
                                track = topMatch,
                                onClick = {
                                    dismissKeyboard()
                                    onTrackClick(topMatch)
                                },
                                onArtistClick = { onArtistClick(topMatch.artist) }
                            )
                        }
                    }

                    // Results List
                    items(state.results.drop(1), key = { it.id }) { track ->
                        LuxurySearchResultItem(
                            track = track,
                            onClick = {
                                dismissKeyboard()
                                onTrackClick(track)
                            },
                            onArtistClick = { onArtistClick(track.artist) }
                        )
                    }
                }
            }
        } else {
            // Empty State: Trending Hot Searches + Recent Searches + Discovery Mood Folders
            LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Hot Trending Searches
                item(key = "trending_searches_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Text("🔥", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TRENDING SEARCHES",
                            color = Color(0xFF00F2FE),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                item(key = "trending_searches_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        items(HOT_TRENDING_CHIPS) { term ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF131A29))
                                    .border(1.dp, Color(0xFF00F2FE).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        dismissKeyboard()
                                        viewModel.onQueryChanged(term)
                                        viewModel.search()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TrendingUp, null, tint = Color(0xFF00F2FE), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(term, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Recent Searches
                if (state.recentQueries.isNotEmpty()) {
                    item(key = "recent_queries_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Recent Searches",
                                color = Color.White,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Clear All",
                                color = Color(0xFF00F2FE),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { viewModel.clearRecentSearches() }
                            )
                        }
                    }

                    item(key = "recent_queries_chips") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            items(state.recentQueries) { query ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(HunterSurface)
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                    .clickable {
                                        dismissKeyboard()
                                        viewModel.onQueryChanged(query)
                                        viewModel.search()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.History, null, tint = HunterTextHint, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = query, color = Color.White, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Discovery Mood Folders Section (Bento Style)
                item(key = "mood_folders_header") {
                    Text(
                        text = "Explore & Discover",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }

                item(key = "mood_folders_grid") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        DISCOVERY_MOOD_TILES.chunked(2).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { item ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(76.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Brush.linearGradient(item.gradient))
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                            .clickable { onMoodFolderOpen(item.key) }
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = item.name,
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(text = item.icon, fontSize = 24.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Luxury Top Result Spotlight Hero Card.
 */
@Composable
private fun LuxuryTopResultSpotlightCard(
    track: Track,
    onClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF101522))
            .border(
                1.5.dp,
                Brush.horizontalGradient(listOf(Color(0xFF00F2FE).copy(alpha = 0.6f), Color(0xFF8B5CF6).copy(alpha = 0.4f))),
                RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B))
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00F2FE).copy(alpha = 0.15f))
                        .border(0.8.dp, Color(0xFF00F2FE).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "★ BEST MATCH",
                        color = Color(0xFF00F2FE),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
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
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onArtistClick() }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6))))
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
 * Luxury search result item row with cover shadow and bitrate badge.
 */
@Composable
private fun LuxurySearchResultItem(
    track: Track,
    onClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
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
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onArtistClick() }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = "LOSSLESS",
                color = Color(0xFF00F2FE),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun LuxurySearchSkeletonRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF161E2E))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161E2E))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161E2E))
            )
        }
    }
}

private data class MoodFolderItem(
    val name: String,
    val icon: String,
    val key: String,
    val gradient: List<Color>
)
