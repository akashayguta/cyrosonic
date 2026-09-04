package com.example.hunterxmusic.presentation.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.theme.*

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
        // Luxury AMOLED Search Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onQueryChanged(it) },
                placeholder = {
                    Text("Search songs, artists, albums, lyrics...", color = HunterTextHint, fontSize = 14.5.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.query.isNotBlank()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Close, "Clear", tint = HunterTextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak song or artist...")
                            }
                            try { voiceLauncher.launch(intent) } catch (_: Exception) { }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF7DD3FC).copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    cursorColor = Color(0xFF7DD3FC),
                    focusedContainerColor = HunterSurface,
                    unfocusedContainerColor = HunterSurface
                ),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    dismissKeyboard()
                    viewModel.search()
                }),
                modifier = Modifier.fillMaxWidth()
            )

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
                            .background(if (isSelected) Color(0xFF7DD3FC) else HunterSurface)
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.onCategorySelected(category) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                    repeat(6) {
                        SearchSkeletonRow()
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
                        Icon(Icons.Default.SearchOff, null, tint = HunterTextHint, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No matches found", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Try searching with another keyword, artist name, or title",
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
                    // Top Result Spotlight Hero
                    val topMatch = state.results.firstOrNull()
                    if (topMatch != null) {
                        item(key = "top_match_spotlight") {
                            TopResultSpotlightCard(
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
                    items(state.results.drop(1)) { track ->
                        SearchResultItem(
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
            // Empty State: Recent Searches + Discovery Mood Folders
            LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Recent Searches
                if (state.recentQueries.isNotEmpty()) {
                    item(key = "recent_queries_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Recent Searches",
                                color = Color.White,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Clear",
                                color = Color(0xFF7DD3FC),
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

                // Discovery Mood Folders Section
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
                    val moodCategories = listOf(
                        MoodFolderItem("Lofi Chill", "🌙", "lofi", listOf(Color(0xFF312E81), Color(0xFF1E1B4B))),
                        MoodFolderItem("Romantic", "💖", "romantic", listOf(Color(0xFF831843), Color(0xFF500724))),
                        MoodFolderItem("Energy", "⚡", "energy", listOf(Color(0xFF854D0E), Color(0xFF451A03))),
                        MoodFolderItem("Party Hits", "🎉", "party", listOf(Color(0xFF9F1239), Color(0xFF4C0519))),
                        MoodFolderItem("Focus & Study", "🎧", "focus", listOf(Color(0xFF065F46), Color(0xFF064E3B))),
                        MoodFolderItem("Synthwave", "🌌", "synthwave", listOf(Color(0xFF4C1D95), Color(0xFF2E1065))),
                        MoodFolderItem("Bollywood Hits", "🇮🇳", "trending hindi", listOf(Color(0xFFB45309), Color(0xFF78350F))),
                        MoodFolderItem("Acoustic Vibes", "🎸", "acoustic", listOf(Color(0xFF374151), Color(0xFF1F2937)))
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        moodCategories.chunked(2).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { item ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(72.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Brush.linearGradient(item.gradient))
                                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                            .clickable { onMoodFolderOpen(item.key) }
                                            .padding(12.dp)
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
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(text = item.icon, fontSize = 22.sp)
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
 * Top Result Spotlight Hero Card.
 */
@Composable
private fun TopResultSpotlightCard(
    track: Track,
    onClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF13131A))
            .border(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF7DD3FC).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TOP MATCH",
                        color = Color(0xFF7DD3FC),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
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

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7DD3FC))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Clean search result item row with duration and bitrate badge.
 */
@Composable
private fun SearchResultItem(
    track: Track,
    onClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E293B))
        ) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = Color(0xFFA1A1AA),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onArtistClick() }
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "HQ",
                color = Color(0xFF7DD3FC),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchSkeletonRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1F2937))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1F2937))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1F2937))
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
