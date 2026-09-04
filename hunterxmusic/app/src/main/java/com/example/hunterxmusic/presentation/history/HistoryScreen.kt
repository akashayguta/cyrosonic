package com.example.hunterxmusic.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hunterxmusic.data.repository.LocalLibraryManager
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.theme.*
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(
    localLibrary: LocalLibraryManager,
    onTrackListClick: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val history by localLibrary.fullHistory.collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HunterBackground)
            .nocturneAurora()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            StaggerIn {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(
                            text = "History",
                            color = HunterTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (history.isEmpty()) "Your listening trail"
                            else "${history.size} distinct ${if (history.size == 1) "song" else "songs"} played",
                            color = HunterTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = history.isNotEmpty(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HunterSurface)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = if (history.isEmpty()) HunterTextHint else HunterTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            when {
                history.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            tint = HunterTextHint,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Nothing here yet",
                            color = HunterTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Play something and it'll land here — each song once, newest first.",
                            color = HunterTextSecondary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                else -> {
                    val tracks = history.map { it.track }

                    // Play all / Shuffle actions
                    StaggerIn(delayMs = NocturneMotion.STAGGER_STEP) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            HistoryActionChip(
                                icon = { Icons.Default.PlayArrow },
                                label = "Play all",
                                emphasized = true,
                                modifier = Modifier.weight(1f),
                                onClick = { if (tracks.isNotEmpty()) onTrackListClick(tracks, 0) }
                            )
                            HistoryActionChip(
                                icon = { Icons.Default.Shuffle },
                                label = "Shuffle",
                                emphasized = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (tracks.isNotEmpty()) {
                                        onTrackListClick(tracks.shuffled(), 0)
                                    }
                                }
                            )
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val grouped = groupByDay(history)
                        grouped.forEach { (dayLabel, entries) ->
                            item(key = "header_$dayLabel") {
                                StaggerIn {
                                    Text(
                                        text = dayLabel,
                                        color = HunterTextHint,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(
                                            start = 22.dp, end = 22.dp,
                                            top = 14.dp, bottom = 6.dp
                                        )
                                    )
                                }
                            }
                            itemsIndexed(
                                entries,
                                key = { _, item -> "${item.track.id}_${item.playedAt}" }
                            ) { indexInDay, item ->
                                val globalIndex = tracks.indexOf(item.track)
                                HistoryRow(
                                    track = item.track,
                                    playedAt = item.playedAt,
                                    onClick = { onTrackListClick(tracks, globalIndex.coerceAtLeast(0)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                containerColor = HunterSurface,
                title = { Text("Clear history?", color = HunterTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "This wipes your listening trail and your Recently Played shelf. " +
                            "Downloads, playlists and liked songs stay untouched.",
                        color = HunterTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        localLibrary.clearHistory()
                        showClearDialog = false
                    }) { Text("Clear", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Keep it", color = HunterTextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun HistoryActionChip(
    icon: () -> androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (emphasized) Color.White else HunterSurface)
            .border(
                1.dp,
                if (emphasized) Color.White else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp)
            )
            .nClick(pressedScale = 0.95f) { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon(),
            contentDescription = null,
            tint = if (emphasized) HunterBackground else HunterTextPrimary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (emphasized) HunterBackground else HunterTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HistoryRow(
    track: Track,
    playedAt: Long,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .nClick(pressedScale = 0.98f) { onClick() }
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        AsyncImage(
            model = track.albumArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HunterSurfaceVariant)
        )
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
            Text(
                text = track.artist,
                color = HunterTextSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = relativeTime(playedAt),
            color = HunterTextHint,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun groupByDay(
    items: List<LocalLibraryManager.HistoryItem>
): List<Pair<String, List<LocalLibraryManager.HistoryItem>>> {
    val now = System.currentTimeMillis()
    val dayStart = { epochMs: Long ->
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val today = dayStart(now)
    val yesterday = today - TimeUnit.DAYS.toMillis(1)
    val weekAgo = today - TimeUnit.DAYS.toMillis(7)

    val buckets = LinkedHashMap<String, MutableList<LocalLibraryManager.HistoryItem>>()
    items.forEach { item ->
        val label = when {
            item.playedAt >= today -> "Today"
            item.playedAt >= yesterday -> "Yesterday"
            item.playedAt >= weekAgo -> "This week"
            else -> "Earlier"
        }
        buckets.getOrPut(label) { mutableListOf() }.add(item)
    }
    return buckets.map { it.key to it.value.toList() }
}

private fun relativeTime(playedAt: Long): String {
    val diff = System.currentTimeMillis() - playedAt
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = playedAt }
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH) + 1}"
        }
    }
}
