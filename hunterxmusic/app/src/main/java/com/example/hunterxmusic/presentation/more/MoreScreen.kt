package com.example.hunterxmusic.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.hunterxmusic.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MoreScreen(
    onAiAssistantClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onStorageClick: () -> Unit,
    isSponsorBlockEnabled: Boolean,
    onSponsorBlockToggle: (Boolean) -> Unit,
    onTimeMachineClick: () -> Unit,
    onCreditsClick: () -> Unit = {},
    onPlaylistImported: (Long) -> Unit = {},
    localLibraryManager: com.example.hunterxmusic.data.repository.LocalLibraryManager? = null,
    musicRepository: com.example.hunterxmusic.domain.repository.MusicRepository? = null,
    okHttpClient: okhttp3.OkHttpClient? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePrefs = remember { com.example.hunterxmusic.data.local.ThemePrefs(context) }
    val qualityPrefs = remember { context.getSharedPreferences("stream_quality", android.content.Context.MODE_PRIVATE) }
    val translationPrefs = remember { com.example.hunterxmusic.data.local.LyricsTranslationPrefs(context) }

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheSizeText by remember { mutableStateOf("calculating…") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        cacheSizeText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val bytes = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                String.format("%.1f MB", bytes / (1024f * 1024f))
            } catch (_: Exception) { "—" }
        }
    }
    val versionName = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) { "CyroSonic" }
    }
    var showImportDialog by remember { mutableStateOf(false) }

    var quality by remember {
        mutableStateOf(
            runCatching { qualityPrefs.getString("q", "medium") ?: "medium" }
                .getOrDefault("medium")
        )
    }
    var lang1State by remember { mutableStateOf(runCatching { translationPrefs.lang1 }.getOrDefault("en")) }
    var lang2State by remember { mutableStateOf(runCatching { translationPrefs.lang2 }.getOrDefault("hi")) }
    var wordSyncState by remember { mutableStateOf(runCatching { translationPrefs.wordSyncEnabled }.getOrDefault(true)) }
    var showAiServerDialog by remember { mutableStateOf(false) }

    if (showAiServerDialog) {
        AiServerConfigDialog(
            onDismiss = { showAiServerDialog = false }
        )
    }

    if (showImportDialog) {
        ImportPlaylistDialog(
            localLibraryManager = localLibraryManager,
            musicRepository = musicRepository,
            okHttpClient = okHttpClient,
            onDone = { playlistId ->
                showImportDialog = false
                if (playlistId != null) onPlaylistImported(playlistId)
            },
            onDismiss = { showImportDialog = false }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Temporary Cache", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will clear temporary stream caches and image thumbnails to free up memory on your device. Your downloaded offline songs will NOT be affected.",
                    color = HunterTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { context.cacheDir.deleteRecursively() }
                                Toast.makeText(context, "Temporary cache cleared successfully!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cache cleared.", Toast.LENGTH_SHORT).show()
                            }
                            cacheSizeText = withContext(Dispatchers.IO) {
                                try {
                                    val bytes = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                                    String.format("%.1f MB", bytes / (1024f * 1024f))
                                } catch (_: Exception) { "—" }
                            }
                            showClearCacheDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Clear Cache", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", color = HunterTextHint)
                }
            },
            containerColor = HunterSurface
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HunterBackground)
            .nocturneAurora(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
    ) {
        // 1. Time Machine Recap (Featured Highlight)
        item(key = "time_machine") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HunterSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .nClick(pressedScale = 0.98f) { onTimeMachineClick() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(18.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Time Machine Recap",
                            color = HunterTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "View listening stats, most played songs & favorite artists",
                            color = HunterTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = HunterTextHint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Section: Tools & Audio
        item(key = "audio_experiences") {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "AUDIO & EXPERIENCES",
                color = HunterTextHint,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HunterSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column {
                    MoreSettingRow(
                        icon = Icons.Default.SmartToy,
                        title = "CyroSonic AI Assistant",
                        subtitle = "Ask music trivia, recommend songs, or chat freely",
                        onClick = onAiAssistantClick
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = 16.dp))
                    MoreSettingRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Import Playlist",
                        subtitle = "Import a YouTube or Spotify playlist link",
                        onClick = { showImportDialog = true }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = 16.dp))
                    MoreSettingRow(
                        icon = Icons.Default.Dns,
                        title = "Companion AI Server",
                        subtitle = "Configure LAN / Cloud endpoint for physical devices",
                        onClick = { showAiServerDialog = true }
                    )
                }
            }
        }

        // Section: Streaming Quality (user-selectable, default Medium)
        item(key = "streaming_quality") {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "STREAMING QUALITY",
                color = HunterTextHint,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HunterSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "JioSaavn streams up to 320kbps MP3/AAC; YouTube Music streams Opus 160kbps (High/Medium) or 96kbps (Data Saver).",
                        color = HunterTextSecondary,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "saver" to "Data Saver",
                            "medium" to "Medium",
                            "high" to "High"
                        ).forEach { (key, label) ->
                            val selected = quality == key
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.06f))
                                    .nClick(pressedScale = 0.95f) {
                                        if (quality != key) {
                                            quality = key
                                            scope.launch(Dispatchers.IO) {
                                                qualityPrefs.edit().putString("q", key).apply()
                                            }
                                        }
                                    }
                                    .padding(vertical = 9.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.Black else HunterTextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Lyrics Translation (2 languages for the lyrics tab pill)
        item(key = "lyrics_translation") {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "LYRICS TRANSLATION",
                color = HunterTextHint,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HunterSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Word-by-Word Karaoke",
                                color = HunterTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Highlight each word in real-time as it is sung",
                                color = HunterTextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        Switch(
                            checked = wordSyncState,
                            onCheckedChange = { checked ->
                                if (wordSyncState != checked) {
                                    wordSyncState = checked
                                    translationPrefs.wordSyncEnabled = checked
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF7DD3FC)
                            )
                        )
                    }

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Text(
                        text = "Pick two languages. In the lyrics tab, the small translate pill toggles between them — tap it to translate the whole song line by line. Translations are cached on your device.",
                        color = HunterTextSecondary,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LanguagePickerRow(
                        label = "1st language",
                        currentCode = lang1State,
                        onSelect = {
                            translationPrefs.lang1 = it
                            lang1State = it
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LanguagePickerRow(
                        label = "2nd language",
                        currentCode = lang2State,
                        onSelect = {
                            translationPrefs.lang2 = it
                            lang2State = it
                        }
                    )
                }
            }
        }



        // Section: Storage & Maintenance
        item(key = "storage_cache") {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "STORAGE & CACHE",
                color = HunterTextHint,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HunterSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column {
                    MoreSettingRow(
                        icon = Icons.Default.Storage,
                        title = "Offline Storage",
                        subtitle = "Check downloaded offline tracks & storage usage",
                        onClick = onStorageClick
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = 16.dp))
                    MoreSettingRow(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear Stream Cache",
                        subtitle = "Cached streams & art — now $cacheSizeText. Downloads stay safe.",
                        onClick = { showClearCacheDialog = true }
                    )
                }
            }
        }

        // Section: About & Credits
        item(key = "about_credits") {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "ABOUT & CREDITS",
                color = HunterTextHint,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = HunterSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                    .nClick(pressedScale = 0.98f) { onCreditsClick() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "CyroSonic",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Version $versionName • CyroSonic Edition",
                                color = HunterTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Creator credit — visible here without having to open the
                    // full Credits screen.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(Color(0xFF7DD3FC), Color(0xFFA5B4FC))
                                    )
                                )
                        ) {
                            Text(
                                text = "SP",
                                color = Color(0xFF04060C),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sandeep Patel",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Creator · Design & Engineering",
                                color = Color(0xFF7DD3FC),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Core Audio Engine",
                            color = HunterTextSecondary,
                            fontSize = 12.5.sp
                        )
                        Text(
                            text = "Opus 160k / 320kbps • AES-GCM",
                            color = Color(0xFFA1A1AA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Build Architecture",
                            color = HunterTextSecondary,
                            fontSize = 12.5.sp
                        )
                        Text(
                            text = "Jetpack Compose • ExoPlayer",
                            color = Color(0xFFA1A1AA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
fun MoreSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .nClick(pressedScale = 0.98f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HunterTextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = HunterTextSecondary, fontSize = 11.5.sp)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = HunterTextHint,
            modifier = Modifier.size(20.dp)
        )
    }
}

private val LYRIC_TRANSLATION_LANGUAGES = listOf(
    "en" to "English", "hi" to "Hindi", "es" to "Spanish", "fr" to "French",
    "de" to "German", "pt" to "Portuguese", "ru" to "Russian", "ar" to "Arabic",
    "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "it" to "Italian",
    "tr" to "Turkish", "ur" to "Urdu", "bn" to "Bengali", "ta" to "Tamil",
    "te" to "Telugu", "ml" to "Malayalam", "pa" to "Punjabi", "gu" to "Gujarati",
    "mr" to "Marathi", "kn" to "Kannada", "id" to "Indonesian", "th" to "Thai",
    "vi" to "Vietnamese", "pl" to "Polish", "nl" to "Dutch", "sv" to "Swedish",
    "el" to "Greek", "he" to "Hebrew", "fa" to "Persian", "sw" to "Swahili",
    "tl" to "Filipino"
)

@Composable
private fun LanguagePickerRow(
    label: String,
    currentCode: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = LYRIC_TRANSLATION_LANGUAGES
        .firstOrNull { it.first == currentCode }?.second ?: currentCode.uppercase()
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .nClick(pressedScale = 0.96f) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(label, color = HunterTextSecondary, fontSize = 12.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentName,
                    color = Color(0xFF7DD3FC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = HunterTextHint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF1B1B1E)
        ) {
            LYRIC_TRANSLATION_LANGUAGES.forEach { (code, name) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = name,
                                color = if (code == currentCode) Color(0xFF7DD3FC) else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (code == currentCode) FontWeight.Bold else FontWeight.Medium
                            )
                            if (code == currentCode) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF7DD3FC),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    }
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// REAL PLAYLIST IMPORT
// YouTube playlists come straight from InnerTube (direct video IDs);
// Spotify links are read via the public embed page (no API key), then
// each track is resolved locally through our own search engines.
// ──────────────────────────────────────────────────────────

@Composable
fun ImportPlaylistDialog(
    localLibraryManager: com.example.hunterxmusic.data.repository.LocalLibraryManager?,
    musicRepository: com.example.hunterxmusic.domain.repository.MusicRepository?,
    okHttpClient: okhttp3.OkHttpClient?,
    onDone: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var playlistName by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun parseYouTubePlaylistId(input: String): String? {
        val trimmed = input.trim()
        // A bare playlist ID pasted on its own — the old regexes both needed a
        // full URL, so this silently failed.
        if (Regex("^(PL|OLAK5uy_|RDCLAK5uy_|VL)[a-zA-Z0-9_-]{10,}$").matches(trimmed)) {
            return trimmed.removePrefix("VL")
        }
        val listParam = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(trimmed)?.groupValues?.get(1)
        if (listParam != null && !listParam.startsWith("WL", ignoreCase = true)) return listParam
        return Regex("youtube[.]com/playlist[?]list=([a-zA-Z0-9_-]+)").find(trimmed)?.groupValues?.get(1)
    }

    fun parseSpotifyPlaylistId(input: String): String? {
        return Regex("spotify[^/]*[/]+playlist[/]+([a-zA-Z0-9]+)").find(input)?.groupValues?.get(1)
    }

    // Runs on the IO dispatcher. This used to execute a blocking OkHttp call
    // straight from the composition scope (main thread), which threw
    // NetworkOnMainThreadException every single time — swallowed by the catch,
    // so Spotify import always reported "couldn't read that playlist".
    suspend fun fetchSpotifyTrackQueries(playlistId: String): List<Pair<String, String>>? =
        withContext(Dispatchers.IO) {
            val client = okHttpClient ?: return@withContext null
            try {
                val request = okhttp3.Request.Builder()
                    .url("https://open.spotify.com/embed/playlist/$playlistId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val html = response.body?.string() ?: return@withContext null
                    val nextData = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
                        .find(html)?.groupValues?.get(1) ?: return@withContext null
                    val root = org.json.JSONObject(nextData)
                    val trackList = root.optJSONObject("props")
                        ?.optJSONObject("pageProps")
                        ?.optJSONObject("state")
                        ?.optJSONObject("data")
                        ?.optJSONObject("entity")
                        ?.optJSONArray("trackList") ?: return@withContext null
                    (0 until trackList.length()).mapNotNull { i ->
                        val item = trackList.optJSONObject(i) ?: return@mapNotNull null
                        // Spotify changed the embed shape: items now carry
                        // `title` + `subtitle` (comma-separated artists) instead
                        // of `name` + `artists[]`. The old fields come back
                        // empty, which silently dropped every track.
                        val name = item.optString("title").ifBlank { item.optString("name") }
                        val artist = item.optString("subtitle")
                            .ifBlank { item.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty() }
                            .split(Regex("[,•·&]"))
                            .firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (name.isNotBlank()) name to artist else null
                    }
                }
            } catch (_: Exception) { null }
        }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = { Text("Import Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        containerColor = HunterSurface,
        text = {
            Column {
                Text(
                    "Paste a YouTube or Spotify playlist link — it becomes a real local playlist you can shuffle, edit and play offline-style.",
                    color = HunterTextSecondary,
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("Playlist link...", fontSize = 12.sp, color = HunterTextHint) },
                    singleLine = true,
                    enabled = !isImporting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = HunterSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Playlist name (optional)", fontSize = 12.sp, color = HunterTextHint) },
                    singleLine = true,
                    enabled = !isImporting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = HunterSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isImporting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = progressText.ifBlank { "Importing..." },
                            color = HunterTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isImporting && url.isNotBlank() && localLibraryManager != null,
                onClick = {
                    isImporting = true
                    scope.launch {
                        try {
                            val ytId = parseYouTubePlaylistId(url)
                            val spId = parseSpotifyPlaylistId(url)
                            val name = playlistName.trim().ifBlank {
                                if (ytId != null) "YouTube Import" else "Spotify Import"
                            }
                            val collected = mutableListOf<com.example.hunterxmusic.domain.model.Track>()
                            var skipped = 0

                            if (ytId != null && okHttpClient != null) {
                                progressText = "Reading YouTube playlist…"
                                collected += com.example.hunterxmusic.data.remote.YouTubeInnerTube
                                    .getPlaylistTracks(ytId, okHttpClient)
                            } else if (spId != null) {
                                progressText = "Reading Spotify playlist…"
                                val queries = fetchSpotifyTrackQueries(spId).orEmpty()
                                // Spotify gives titles only, so each one has to
                                // be matched against our own catalog.
                                val capped = queries.take(200)
                                capped.forEachIndexed { i, pair ->
                                    val (trackName, artistName) = pair
                                    progressText = "Matching ${i + 1} of ${capped.size} — $trackName"
                                    try {
                                        val results = musicRepository?.searchTracks("$trackName $artistName").orEmpty()
                                        // Pick the hit that actually covers the
                                        // name — a blind firstOrNull() could
                                        // match a wrong song with a similar name.
                                        val tokens = "$trackName $artistName".lowercase()
                                            .split(Regex("[^a-z0-9\\p{L}]+"))
                                            .filter { it.length > 1 }
                                        val required = kotlin.math.ceil(tokens.size * 0.6).toInt().coerceAtLeast(1)
                                        val match = results.firstOrNull { r ->
                                            val title = r.title.lowercase()
                                            val artist = r.artist.lowercase()
                                            tokens.count { title.contains(it) || artist.contains(it) } >= required
                                        }
                                        if (match != null) collected.add(match) else skipped++
                                    } catch (_: Exception) { skipped++ }
                                }
                                if (queries.size > capped.size) {
                                    skipped += queries.size - capped.size
                                }
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "That doesn't look like a YouTube or Spotify playlist link.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                onDone(null)
                                return@launch
                            }

                            if (collected.isEmpty()) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Couldn't read that playlist. Make sure it's public, then try again.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                onDone(null)
                            } else {
                                progressText = "Saving ${collected.size} songs…"
                                // Null-safe instead of !!: the confirm button is
                                // disabled without a manager, so this branch is
                                // unreachable in practice — but a KotlinNPE here
                                // would take the whole dialog down with it.
                                val manager = localLibraryManager
                                if (manager == null) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Import failed. Check your connection and try again.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onDone(null)
                                    return@launch
                                }
                                val playlistId = manager.createPlaylist(name)
                                manager.addTracksToPlaylist(playlistId, collected)
                                // Report skips instead of silently truncating.
                                val message = if (skipped > 0) {
                                    "Imported ${collected.size} songs into \"$name\" — $skipped couldn't be matched"
                                } else {
                                    "Imported ${collected.size} songs into \"$name\""
                                }
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                onDone(playlistId)
                            }
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "Import failed. Check your connection and try again.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onDone(null)
                        } finally {
                            isImporting = false
                        }
                    }
                }
            ) {
                Text(
                    text = if (isImporting) "Importing..." else "Import",
                    color = if (url.isNotBlank()) HunterBrand else HunterTextHint,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!isImporting) onDismiss() },
                enabled = !isImporting
            ) { Text("Cancel", color = HunterTextSecondary) }
        }
    )
}

@Composable
fun AiServerConfigDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ai_server_prefs", android.content.Context.MODE_PRIVATE) }
    var currentUrl by remember { mutableStateOf(prefs.getString("host_url", "") ?: "") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Companion AI Server", color = Color.White, fontWeight = FontWeight.Bold) },
        containerColor = HunterSurface,
        text = {
            Column {
                Text(
                    "Set your local LAN or cloud companion server endpoint (e.g. http://192.168.1.50:5000/ or https://your-server.com/).",
                    color = HunterTextSecondary,
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = { currentUrl = it },
                    placeholder = { Text("http://192.168.1.x:5000/", fontSize = 12.sp, color = HunterTextHint) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF7DD3FC),
                        unfocusedBorderColor = HunterSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = currentUrl.trim()
                    if (trimmed.isBlank()) {
                        prefs.edit().remove("host_url").apply()
                        Toast.makeText(context, "AI host reset to default", Toast.LENGTH_SHORT).show()
                    } else {
                        val formatted = if (!trimmed.endsWith("/")) "$trimmed/" else trimmed
                        prefs.edit().putString("host_url", formatted).apply()
                        Toast.makeText(context, "AI host saved: $formatted", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7DD3FC))
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HunterTextHint)
            }
        }
    )
}
