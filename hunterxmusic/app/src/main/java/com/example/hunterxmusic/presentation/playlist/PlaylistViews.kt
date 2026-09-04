package com.example.hunterxmusic.presentation.playlist

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hunterxmusic.data.local.db.PlaylistEntity
import com.example.hunterxmusic.data.repository.LocalLibraryManager
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.presentation.common.SwipeableTrackRow
import com.example.hunterxmusic.theme.*
import kotlinx.coroutines.launch

/**
 * Full playlist detail screen: hero header, play/shuffle, swipe-to-remove rows.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String,
    localLibrary: LocalLibraryManager,
    onBack: () -> Unit,
    onTrackListClick: (List<Track>, Int) -> Unit,
    onAddTracks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var currentName by remember { mutableStateOf(playlistName) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(playlistId) {
        localLibrary.playlistTracks(playlistId).collect { tracks = it }
    }
    // Resolve the real playlist name from the DB — MainActivity passes ""
    // here, which left every playlist's hero header nameless.
    LaunchedEffect(playlistId) {
        localLibrary.playlists.collect { all ->
            if (currentName.isBlank()) {
                all.firstOrNull { it.id == playlistId }?.let { currentName = it.name }
            }
        }
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
            // Hero header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    val heroArt = tracks.firstOrNull { !it.albumArtUrl.isNullOrBlank() }?.albumArtUrl
                    if (heroArt != null) {
                        AsyncImage(
                            model = heroArt,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1A1A2E), HunterBackground)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, HunterBackground.copy(alpha = 0.7f), HunterBackground)
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF7DD3FC).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "PLAYLIST",
                                    color = Color(0xFF7DD3FC),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${tracks.size} songs",
                                color = HunterTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentName,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { showRenameDialog = true }
                        )
                    }
                }
            }

            // Action row
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
                            onClick = { if (tracks.isNotEmpty()) onTrackListClick(tracks, 0) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { if (tracks.isNotEmpty()) onTrackListClick(tracks.shuffled(), 0) },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HunterTextPrimary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, null, tint = HunterTextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", color = HunterTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (tracks.isEmpty()) return@IconButton
                                val unDownloaded = tracks.filter { !it.isDownloaded }
                                if (unDownloaded.isEmpty()) {
                                    android.widget.Toast.makeText(context, "All ${tracks.size} songs are already saved offline!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Downloading ${unDownloaded.size} songs offline...", android.widget.Toast.LENGTH_SHORT).show()
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        unDownloaded.forEach { track ->
                                            try {
                                                com.example.hunterxmusic.HunterApplication.dependencies.musicRepository.downloadTrack(track).collect { }
                                            } catch (_: Exception) { }
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download all songs", tint = HunterTextPrimary)
                        }
                        IconButton(onClick = onAddTracks) {
                            Icon(Icons.Default.Add, contentDescription = "Add songs", tint = HunterTextPrimary)
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = HunterTextHint,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("This playlist is empty", color = HunterTextSecondary, fontSize = 14.sp)
                            Text(
                                "Tap + to search and add songs",
                                color = HunterTextHint,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(tracks, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackListClick(tracks, index) }
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
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Track options",
                                    tint = HunterTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(HunterSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Play Next", color = HunterTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.SkipNext, null, tint = HunterTextPrimary, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        com.example.hunterxmusic.HunterApplication.dependencies.musicPlayerManager.playNext(track)
                                        android.widget.Toast.makeText(context, "Playing next: ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Queue", color = HunterTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.QueueMusic, null, tint = HunterTextPrimary, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        com.example.hunterxmusic.HunterApplication.dependencies.musicPlayerManager.enqueueTrack(track)
                                        android.widget.Toast.makeText(context, "Added to queue: ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (track.isDownloaded) "Delete Download" else "Download Offline",
                                            color = if (track.isDownloaded) Color(0xFFFF5252) else HunterTextPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (track.isDownloaded) Icons.Default.DeleteOutline else Icons.Default.Download,
                                            null,
                                            tint = if (track.isDownloaded) Color(0xFFFF5252) else HunterTextPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            if (track.isDownloaded) {
                                                com.example.hunterxmusic.HunterApplication.dependencies.musicRepository.deleteTrack(track)
                                            } else {
                                                com.example.hunterxmusic.HunterApplication.dependencies.musicRepository.downloadTrack(track).collect { }
                                            }
                                        }
                                        android.widget.Toast.makeText(
                                            context,
                                            if (track.isDownloaded) "Removed from downloads" else "Downloading: ${track.title}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                DropdownMenuItem(
                                    text = { Text("Remove from Playlist", color = Color(0xFFFF5252)) },
                                    leadingIcon = { Icon(Icons.Default.Close, null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch { localLibrary.removeTrackFromPlaylist(playlistId, track.id) }
                                        android.widget.Toast.makeText(context, "Removed from playlist", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Back + delete
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = {
                    scope.launch {
                        localLibrary.deletePlaylist(playlistId)
                        onBack()
                    }
                },
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete playlist", tint = Color(0xFFFF6B6B), modifier = Modifier.size(19.dp))
            }
        }

        if (showRenameDialog) {
            var newName by remember { mutableStateOf(currentName) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
                containerColor = HunterSurface,
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
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
                        scope.launch { localLibrary.renamePlaylist(playlistId, newName) }
                        currentName = newName.trim().ifBlank { currentName }
                        showRenameDialog = false
                    }) { Text("Save", color = HunterBrand) }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = HunterTextSecondary) }
                }
            )
        }
    }
}

/**
 * Bottom sheet: pick (or create) a playlist for a track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: Track,
    localLibrary: LocalLibraryManager,
    onDismiss: () -> Unit
) {
    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        localLibrary.playlists.collect { playlists = it }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = HunterSurface,
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
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
                    val name = newPlaylistName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            val id = localLibrary.createPlaylist(name)
                            localLibrary.addTrackToPlaylist(id, track)
                        }
                        android.widget.Toast.makeText(context, "Added to \"$name\"", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    showCreate = false
                    onDismiss()
                }) { Text("Create", color = HunterBrand) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel", color = HunterTextSecondary) }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HunterSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Add to Playlist",
                color = HunterTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "\"${track.title}\" — ${track.artist}",
                color = HunterTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Create new
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { showCreate = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text("New Playlist", color = HunterTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                scope.launch { localLibrary.addTrackToPlaylist(playlist.id, track) }
                                android.widget.Toast.makeText(context, "Added to \"${playlist.name}\"", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.QueueMusic, null, tint = HunterTextSecondary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = playlist.name,
                            color = HunterTextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
