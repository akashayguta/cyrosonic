package com.example.hunterxmusic.presentation.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material.icons.outlined.*
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
import com.example.hunterxmusic.presentation.common.SwipeableTrackRow
import com.example.hunterxmusic.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSettingsClick: () -> Unit = {},
    onTrackListClick: (List<Track>, Int) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onEnqueueTrack: (Track) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit = { _, _ -> },
    localLibrary: com.example.hunterxmusic.data.repository.LocalLibraryManager? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state
    var playlists by remember { mutableStateOf<List<com.example.hunterxmusic.data.local.db.PlaylistEntity>>(emptyList()) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(localLibrary) {
        localLibrary?.playlists?.collect { playlists = it }
    }

    if (showCreatePlaylist) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("New Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = HunterSurface,
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Playlist name...", color = HunterTextHint) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = HunterSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty() && localLibrary != null) {
                        scope.launch {
                            val id = localLibrary.createPlaylist(name)
                            onOpenPlaylist(id, name)
                        }
                    }
                    showCreatePlaylist = false
                }) { Text("Create", color = HunterBrand) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel", color = HunterTextSecondary) }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HunterBackground)
    ) {
        // Sticky Header (Shifted ~1.73cm higher)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HunterBackground)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "My Library",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.downloadedTracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${state.downloadedTracks.size} OFFLINE",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Instant in-library Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search songs, artists, albums in library...", fontSize = 12.sp, color = HunterTextHint) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = HunterTextHint, modifier = Modifier.size(17.dp))
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF7DD3FC).copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = HunterSurface,
                    unfocusedContainerColor = HunterSurface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Quick Access Bento Grid (real data only â€” dead shortcut cards removed)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BentoLibraryCard(
                            icon = Icons.Default.Favorite,
                            iconTint = AeroPink,
                            title = "Liked Tracks",
                            count = state.likedTracks.size,
                            gradientColors = listOf(Color(0xFF2A111E), Color(0xFF1B0C15)),
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onFilterSelected("Liked") }
                        )
                        BentoLibraryCard(
                            icon = Icons.Default.CloudDownload,
                            iconTint = AeroCyan,
                            title = "Downloads",
                            count = state.downloadedTracks.size,
                            gradientColors = listOf(Color(0xFF0F2033), Color(0xFF0B1522)),
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onFilterSelected("Downloaded") }
                        )
                    }
                }
            }

            // On this device â€” songs, ringtones, alarms already in storage
            item {
                val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    android.Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    @Suppress("DEPRECATION")
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                }
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) viewModel.scanDevice() else viewModel.onDevicePermissionDenied()
                }

                StaggerIn {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "On this device",
                                color = HunterTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    state.isScanningDevice -> "Scanning your storage…"
                                    state.devicePermissionDenied -> "Storage access needed to find your files"
                                    state.deviceError != null -> state.deviceError!!
                                    state.deviceTracks.isNotEmpty() ->
                                        "${state.deviceTracks.size} files — music, ringtones and alarms"
                                    state.hasScannedDevice -> "No audio files found in your storage"
                                    else -> "Music, ringtones and downloads on your phone"
                                },
                                color = if (state.deviceError != null) Color(0xFFF87171) else HunterTextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                                .nClick(pressedScale = 0.94f) { launcher.launch(permission) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (state.isScanningDevice) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 1.8.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.LibraryAdd,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (state.deviceTracks.isEmpty()) "Scan" else "Rescan",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Permission was refused â€” say what to do about it.
                if (state.devicePermissionDenied) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2A1A1A))
                            .nClick(pressedScale = 0.98f) {
                                try {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.fromParts("package", ctx.packageName, null)
                                        )
                                    )
                                } catch (_: Exception) { }
                            }
                            .padding(14.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFF87171), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "CyroSonic can't read your storage yet. Tap to open Settings and allow music and audio.",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Category chips â€” Music / Ringtones / Alarms / Notifications
                val categories = viewModel.deviceCategoriesPresent()
                if (categories.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        item {
                            DeviceCategoryChip(
                                label = "All (${state.deviceTracks.size})",
                                selected = state.deviceCategoryFilter == null,
                                onClick = { viewModel.setDeviceCategoryFilter(null) }
                            )
                        }
                        items(categories) { cat ->
                            val count = state.deviceTracks.count { it.category == cat }
                            DeviceCategoryChip(
                                label = "${cat.label} ($count)",
                                selected = state.deviceCategoryFilter == cat,
                                onClick = { viewModel.setDeviceCategoryFilter(cat) }
                            )
                        }
                    }
                }
            }

            // Device rows as real list items so they recycle. They used to be a
            // forEach inside one item, capped at 50 with no indication.
            val visibleDevice = viewModel.visibleDeviceTracks()
            itemsIndexed(visibleDevice, key = { _, dt -> "dev_${dt.mediaId}" }) { index, dTrack ->
                DeviceTrackRow(
                    deviceTrack = dTrack,
                    onClick = {
                        onTrackListClick(visibleDevice.map { it.track }, index)
                    },
                    onDeleted = { viewModel.removeDeviceTrackLocally(dTrack) },
                    buildDeleteRequest = { viewModel.buildDeviceDeleteRequest(dTrack) },
                    deleteDirectly = { viewModel.deleteDeviceTrackDirectly(dTrack) }
                )
            }

            // Recently Played removed from Library â€” now lives as
            // "Continue Listening" on Home for a cleaner Library.

            // Playlists section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Playlists",
                            color = HunterTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF7DD3FC).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${playlists.size}",
                                color = Color(0xFF7DD3FC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                            .nClick(pressedScale = 0.94f) { showCreatePlaylist = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (playlists.isEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(HunterSurface.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                            .nClick(pressedScale = 0.98f) { showCreatePlaylist = true }
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        Icon(Icons.Default.QueueMusic, null, tint = Color(0xFF7DD3FC), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Create your first playlist", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text("Curate custom mixes or import Spotify/YouTube playlists", color = HunterTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                items(playlists.size, key = { playlists[it].id }) { index ->
                    val playlist = playlists[index]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .nClick(pressedScale = 0.98f) { onOpenPlaylist(playlist.id, playlist.name) }
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF14101F))
                            .border(1.dp, Color(0xFFC084FC).copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF7C3AED), Color(0xFFC026D3), Color(0xFFF59E0B))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        ) {
                            Icon(Icons.Default.QueueMusic, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "CyroSonic Curated Playlist",
                                color = Color(0xFFA594BD),
                                fontSize = 11.5.sp
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFC084FC).copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Show Active Filter Indicator
            if (state.selectedFilter != "All") {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Filter: ${state.selectedFilter}",
                            color = AeroCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Reset)",
                            color = HunterTextHint,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { viewModel.onFilterSelected("All") }
                        )
                    }
                }
            }

            // Liked Tracks List
            val showLiked = state.selectedFilter == "All" || state.selectedFilter == "Songs" || state.selectedFilter == "Liked"
            if (showLiked && state.likedTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Liked Songs (${state.likedTracks.size})",
                        color = HunterTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
                items(state.likedTracks) { track ->
                    SwipeableTrackRow(
                        track = track,
                        onSwipeLeft = { onDeleteTrack(track) },
                        onSwipeRight = { onEnqueueTrack(track) }
                    ) {
                        LibraryTrackItem(
                            track = track,
                            onClick = { onTrackListClick(state.likedTracks, state.likedTracks.indexOf(track)) },
                            onDeleteClick = { onDeleteTrack(track) },
                            isLikedRow = true,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }

            // Downloaded Tracks List
            val showDownloaded = state.selectedFilter == "All" || state.selectedFilter == "Songs" || state.selectedFilter == "Downloaded"
            if (showDownloaded && state.downloadedTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Offline Downloads (${state.downloadedTracks.size})",
                        color = HunterTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
                items(state.downloadedTracks) { track ->
                    SwipeableTrackRow(
                        track = track,
                        onSwipeLeft = { onDeleteTrack(track) },
                        onSwipeRight = { onEnqueueTrack(track) }
                    ) {
                        LibraryTrackItem(
                            track = track,
                            onClick = { onTrackListClick(state.downloadedTracks, state.downloadedTracks.indexOf(track)) },
                            onDeleteClick = { onDeleteTrack(track) },
                            isLikedRow = false,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }

            // Empty State
            val isEmpty = (showLiked && state.likedTracks.isEmpty()) && (showDownloaded && state.downloadedTracks.isEmpty())
            if (isEmpty) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(60.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = HunterTextHint,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No songs in this folder", color = HunterTextSecondary, fontSize = 14.sp)
                            Text(
                                "Download or like songs to view them offline anytime",
                                color = HunterTextHint,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoLibraryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    count: Int?,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(1.dp, iconTint.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .nClick(pressedScale = 0.97f) { onClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f))
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = HunterTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (count != null) {
                    Text("$count songs", color = HunterTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryTrackItem(
    track: Track,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit = {},
    isLikedRow: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .nClick(pressedScale = 0.98f) { onClick() }
            .padding(horizontal = 18.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HunterSurface.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        AsyncImage(
            model = track.albumArtUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HunterSurfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = HunterTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = HunterTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isLikedRow) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Liked",
                tint = AeroPink,
                modifier = Modifier.size(18.dp)
            )
        } else {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = AeroCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// ON-DEVICE STORAGE ROWS
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun DeviceCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.07f))
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .nClick(pressedScale = 0.95f) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else HunterTextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * One audio file from device storage. Shows real album art (the old row was a
 * hardcoded note icon) and offers delete, which needs the system's permission
 * prompt on Android 11+ because the app doesn't own these files.
 */
@Composable
private fun DeviceTrackRow(
    deviceTrack: com.example.hunterxmusic.data.repository.DeviceTrack,
    onClick: () -> Unit,
    onDeleted: () -> Unit,
    buildDeleteRequest: () -> android.content.IntentSender?,
    deleteDirectly: () -> Unit
) {
    val track = deviceTrack.track
    var showConfirm by remember { mutableStateOf(false) }

    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) onDeleted()
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = HunterSurface,
            title = { Text("Delete from device?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${track.title}\" will be permanently removed from your phone's storage. This can't be undone.",
                    color = HunterTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        val sender = buildDeleteRequest()
                        if (sender != null) {
                            deleteLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                            )
                        } else {
                            // Pre-Android-11: no system confirmation needed.
                            deleteDirectly()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = HunterTextSecondary)
                }
            }
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .nClick(pressedScale = 0.98f) { onClick() }
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HunterSurfaceVariant)
        ) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (track.albumArtUrl == null) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = HunterTextHint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = HunterTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (deviceTrack.category != com.example.hunterxmusic.data.repository.DeviceAudioCategory.MUSIC) {
                    Text(
                        text = deviceTrack.category.label.uppercase(),
                        color = Color(0xFF7DD3FC),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = track.artist,
                    color = HunterTextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = com.example.hunterxmusic.data.repository.formatDeviceDuration(track.durationMs),
            color = HunterTextHint,
            fontSize = 11.sp
        )
        IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Delete from device",
                tint = HunterTextHint,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
