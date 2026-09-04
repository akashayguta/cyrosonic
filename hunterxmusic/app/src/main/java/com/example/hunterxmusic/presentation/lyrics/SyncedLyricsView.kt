package com.example.hunterxmusic.presentation.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hunterxmusic.data.local.LyricsTranslationPrefs
import com.example.hunterxmusic.domain.model.LyricLine
import com.example.hunterxmusic.theme.nClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
/**
 * Apple Music / Spotify Style Synced Lyrics Engine:
 * - Whole-line karaoke glow fill — the ENTIRE line lights up as it's sung
 *   (no word-by-word sweep; the full line is "selected")
 * - Script-aware reading in every language
 * - Honest reading mode for unsynced lyrics — never fakes timestamps
 * - User-scroll aware auto-scroll (pauses while you browse, resumes after idle)
 * - Instrumental prelude ♪ indicator for intro beats
 * - Compact corner pill: sync status + timing calibration nudge
 * - Line translation: two languages from Settings, tap the pill to toggle,
 *   every line translates with an on-device cache
 */
@Composable
fun SyncedLyricsView(
    lyrics: List<LyricLine>,
    playbackPositionMsProvider: () -> Long,
    onLyricClick: (timestampMs: Long) -> Unit,
    acousticSyncOffsetMs: Long = 0L,
    initialSyncOffsetMs: Long = 0L,
    onSyncOffsetChanged: (Long) -> Unit = {},
    onActiveLineChanged: (String) -> Unit = {},
    translationPrefs: LyricsTranslationPrefs? = null,
    songKey: String = "",
    targetLang: String? = null,
    onTargetLangChanged: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val translations = remember { mutableStateMapOf<Int, String>() }
    val lyricScope = rememberCoroutineScope()

    // Active translation target: null = off, else one of the language codes
    var internalTargetLang by remember { mutableStateOf<String?>(targetLang) }
    val activeTargetLang = targetLang ?: internalTargetLang
    val setTargetLang: (String?) -> Unit = { newLang ->
        internalTargetLang = newLang
        onTargetLangChanged?.invoke(newLang)
    }

    // Seed with the per-track remembered calibration, if any
    var syncOffsetMs by remember { mutableLongStateOf(initialSyncOffsetMs) }
    var showSyncNudge by remember { mutableStateOf(false) }

    // Real synced cues vs estimated-fit vs plain sheet
    val isSyncedLyrics = remember(lyrics) {
        lyrics.size > 1 && lyrics.any { it.timestampMs > 0L }
    }
    val isEstimatedSync = remember(lyrics) {
        lyrics.isNotEmpty() && lyrics.any { it.isEstimated }
    }

    // Playback position wrapped in State: updates smoothly at 60fps via provider
    // to keep CPU/GPU utilization low and prevent parent recompositions.
    val positionState = remember { mutableLongStateOf(0L) }
    LaunchedEffect(playbackPositionMsProvider) {
        while (true) {
            positionState.longValue = playbackPositionMsProvider()
            kotlinx.coroutines.delay(16)
        }
    }

    // Total offset: 1:1 synchronized with seekbar clock (00:00:00)
    val totalOffset = syncOffsetMs + acousticSyncOffsetMs

    // Active line index — computed via derivedStateOf so SyncedLyricsView only
    // recomposes when the song crosses a line boundary, instead of 60fps recomposition.
    val activeIndex by remember(lyrics, totalOffset) {
        derivedStateOf {
            if (!isSyncedLyrics) {
                -1
            } else {
                val effectivePos = positionState.longValue + totalOffset
                val firstTimestamp = lyrics.firstOrNull { it.timestampMs > 0 }?.timestampMs ?: 0L
                if (effectivePos < firstTimestamp) -1
                else lyrics.indexOfLast { effectivePos >= it.timestampMs }
            }
        }
    }

    // Hoist the active line so the player can build share cards from it
    LaunchedEffect(activeIndex, lyrics) {
        onActiveLineChanged(lyrics.getOrNull(activeIndex)?.words.orEmpty())
    }

    // ── Whole-song translation ─────────────────────────────────────────
    // Toggled via the corner pill. Translates every line into the active
    // target language, sequentially and spaced (free API rate limits),
    // pulling from the on-device cache first so repeat plays cost nothing.
    val translator = com.example.hunterxmusic.HunterApplication.dependencies.lyricsTranslator
    LaunchedEffect(activeTargetLang, songKey, lyrics) {
        translations.clear()
        val lang = activeTargetLang ?: return@LaunchedEffect
        val prefs = translationPrefs
        val keyBase = songKey.ifBlank { lyrics.firstOrNull()?.words.orEmpty().take(48) }
        for (index in lyrics.indices) {
            val line = lyrics[index]
            if (line.isInstrumental || line.words.isBlank()) continue
            val cached = prefs?.cachedTranslation(lang, keyBase, index)
            if (cached != null) {
                translations[index] = cached
                continue
            }
            val result = try {
                translator.translate(line.words, lang)
            } catch (_: Exception) { null }
            if (result != null) {
                translations[index] = result
                prefs?.saveTranslation(lang, keyBase, index, result)
            }
            delay(220) // stay inside the free tier's per-second budget
        }
    }

    // ── User-scroll aware auto-scroll ─────────────────────────────
    // When the user drags the list, auto-follow pauses for 3.5s after the
    // last touch, then snaps back to the active line. No more scroll fights.
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var lastUserInteractionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastUserInteractionMs) {
        if (lastUserInteractionMs > 0L) {
            autoScrollEnabled = false
            delay(3500)
            // Only resume if no fresh touch happened while we waited
            if (System.currentTimeMillis() - lastUserInteractionMs >= 3500) {
                autoScrollEnabled = true
            }
        }
    }

    LaunchedEffect(activeIndex, autoScrollEnabled, isSyncedLyrics) {
        if (!autoScrollEnabled || lyrics.isEmpty() || !isSyncedLyrics) return@LaunchedEffect
        // +1 accounts for item 0 (Instrumental ♪ indicator)
        val targetIndex = if (activeIndex >= 0) activeIndex + 1 else 0
        try {
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = 0
            )
        } catch (_: Exception) {
            listState.scrollToItem(targetIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF06060A))
    ) {
        val containerHeight = maxHeight

        // Dynamic Background Stardust Layer
        com.example.hunterxmusic.theme.LumeaParticleField(
            modifier = Modifier.fillMaxSize(),
            particleCount = 20,
            accentColor = Color(0xFF38BDF8),
            isPlaying = true
        )

        if (lyrics.isEmpty()) {
            LyricsLoadingState()
            return@BoxWithConstraints
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = containerHeight / 2 - 40.dp,
                        bottom = containerHeight / 2 - 40.dp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Observe touches in the Initial pass without consuming —
                        // the LazyColumn keeps handling its own scrolling.
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                lastUserInteractionMs = System.currentTimeMillis()
                            }
                        }
                ) {
                    // 1. Instrumental Prelude ♪ Indicator (synced mode only)
                    if (isSyncedLyrics) {
                        item(key = "intro_instrumental") {
                            val isIntroActive = activeIndex == -1
                            val notePulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                                initialValue = if (isIntroActive) 0.88f else 1.0f,
                                targetValue = if (isIntroActive) 1.16f else 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "noteScale"
                            )

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .graphicsLayer(scaleX = notePulse, scaleY = notePulse)
                                        .clip(CircleShape)
                                        .background(
                                            if (isIntroActive) Color.White.copy(alpha = 0.15f) else Color.Transparent
                                        )
                                        .border(
                                            width = if (isIntroActive) 1.dp else 0.dp,
                                            color = if (isIntroActive) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                                            shape = CircleShape
                                        )
                                ) {
                                    Text(
                                        text = "♪",
                                        color = if (isIntroActive) Color.White else Color(0xFF52525B),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    // 2. Lyrics lines — long-press any line to translate just
                    // that one (uses the active target language, or the 1st
                    // Settings language when translation is off).
                    fun translateLine(index: Int, text: String) {
                        if (translations.containsKey(index) || text.isBlank()) return
                        val lang = activeTargetLang ?: translationPrefs?.lang1 ?: "en"
                        val keyBase = songKey.ifBlank { lyrics.firstOrNull()?.words.orEmpty().take(48) }
                        val cached = translationPrefs?.cachedTranslation(lang, keyBase, index)
                        if (cached != null) {
                            translations[index] = cached
                            return
                        }
                        lyricScope.launch(Dispatchers.IO) {
                            val result = try {
                                translator.translate(text, lang)
                            } catch (_: Exception) { null }
                            if (result != null) {
                                translations[index] = result
                                translationPrefs?.saveTranslation(lang, keyBase, index, result)
                            }
                        }
                    }

                    itemsIndexed(lyrics, key = { index, line -> "$index:${line.timestampMs}" }) { index, lyricLine ->
                        if (lyricLine.isInstrumental) {
                            // A stretch of music with no vocals. LRCLIB marks
                            // these as timestamped blank lines and long gaps
                            // between sung lines are the same thing — either way
                            // the screen shows a pulsing ♪ instead of a frozen
                            // lyric.
                            InstrumentalRow(
                                isActive = index == activeIndex,
                                onClick = {
                                    onLyricClick((lyricLine.timestampMs - totalOffset).coerceAtLeast(0L))
                                }
                            )
                        } else if (isSyncedLyrics) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { translateLine(index, lyricLine.words) }
                                    )
                            ) {
                            SyncedLyricRow(
                                lyricLine = lyricLine,
                                index = index,
                                lyrics = lyrics,
                                isActive = index == activeIndex,
                                activeIndex = activeIndex,
                                positionState = positionState,
                                totalOffset = totalOffset,
                                onLyricClick = onLyricClick,
                                translation = translations[index]
                            )
                            }
                        } else {
                            PlainLyricRow(lyricLine = lyricLine, translation = translations[index])
                        }
                    }
                }

                // Soft cinematic fade over the list edges (non-interactive)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )

                // Floating "Back to current lyric" button (when user manually scrolls away from active line)
                val firstVisible = listState.firstVisibleItemIndex
                val targetIndex = if (activeIndex >= 0) activeIndex + 1 else 0
                val isScrolledAway = !autoScrollEnabled && isSyncedLyrics && activeIndex >= 0 && abs(targetIndex - firstVisible) > 1

                androidx.compose.animation.AnimatedVisibility(
                    visible = isScrolledAway,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    val scrollDirectionIcon = if (firstVisible < targetIndex) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.95f))
                            .border(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                            .nClick(pressedScale = 0.92f) {
                                autoScrollEnabled = true
                                lastUserInteractionMs = 0L
                                lyricScope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = scrollDirectionIcon,
                            contentDescription = "Sync Position",
                            tint = Color(0xFF7DD3FC),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Back to current lyric",
                            color = Color(0xFF7DD3FC),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Interactive Translation Pill (Top-Left)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (activeTargetLang != null) Color(0xFF1E1B4B).copy(alpha = 0.9f)
                            else Color(0xFF18181B).copy(alpha = 0.75f)
                        )
                        .border(
                            1.dp,
                            if (activeTargetLang != null) Color(0xFF818CF8).copy(alpha = 0.7f)
                            else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            val nextLang = when (activeTargetLang) {
                                null -> "hi"
                                "hi" -> "en"
                                "en" -> "es"
                                else -> null
                            }
                            setTargetLang(nextLang)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Translate lyrics",
                        tint = if (activeTargetLang != null) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when (activeTargetLang) {
                            "hi" -> "Hindi"
                            "en" -> "English"
                            "es" -> "Spanish"
                            null -> "Translate"
                            else -> activeTargetLang.uppercase()
                        },
                        color = if (activeTargetLang != null) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.75f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * One synced line: 3D tilt + scale + karaoke word fill. Reads playback
 * position from State ONLY when active, so inactive lines never recompose
 * on the playback tick.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SyncedLyricRow(
    lyricLine: LyricLine,
    index: Int,
    lyrics: List<LyricLine>,
    isActive: Boolean,
    activeIndex: Int,
    positionState: State<Long>,
    totalOffset: Long,
    onLyricClick: (Long) -> Unit,
    translation: String? = null
) {
    val relativeOffset = remember(index, activeIndex) {
        if (activeIndex == -1) 0f
        else ((index - activeIndex).toFloat() / 3.0f).coerceIn(-1.0f, 1.0f)
    }

    val sizeMultiplier by animateFloatAsState(
        targetValue = if (isActive) 1.12f else 0.92f,
        animationSpec = tween(durationMillis = 300),
        label = "lineSize"
    )

    val lyricAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else if (activeIndex == -1) 0.55f else (1.0f - abs(relativeOffset) * 0.70f).coerceIn(0.15f, 1.0f),
        animationSpec = tween(durationMillis = 200),
        label = "lineAlpha"
    )

    val rotationAngle = 0f
    val scale = (1.0f - abs(relativeOffset) * 0.05f) * sizeMultiplier

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = lyricAlpha
            }
            .clickable(
                onClick = {
                    val targetSeekMs = (lyricLine.timestampMs - totalOffset).coerceAtLeast(0L)
                    onLyricClick(targetSeekMs)
                },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        // Lyric content + translation stacked in a Column — the old
        // BottomCenter-aligned overlay painted the translation directly ON
        // TOP of wrapped lyric rows.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isActive) {
                val currentPos = positionState.value + totalOffset
                val nextTimestamp = lyrics.getOrNull(index + 1)?.timestampMs
                val lineDurationMs = if (nextTimestamp != null && nextTimestamp > lyricLine.timestampMs) {
                    (nextTimestamp - lyricLine.timestampMs).coerceIn(400L, 20_000L)
                } else {
                    3_500L
                }

                val elapsed = (currentPos - lyricLine.timestampMs).coerceAtLeast(0L)
                val lineProgress = (elapsed.toFloat() / lineDurationMs.toFloat()).coerceIn(0f, 1f)

                val cues = remember(lyricLine, lineDurationMs) {
                    if (!lyricLine.wordCues.isNullOrEmpty()) {
                        lyricLine.wordCues
                    } else {
                        // Fallback for plain lines: Synthesize realistic syllable-weighted cues
                        val rawWords = lyricLine.words.split(Regex("\\s+")).filter { it.isNotBlank() }
                        if (rawWords.isEmpty()) emptyList()
                        else {
                            val totalWeight = rawWords.sumOf { it.length.coerceAtLeast(2) }.toDouble()
                            // Natural singing duration: characters * 105ms + 400ms, capped so instrumental gaps at end of line remain quiet!
                            val maxVocalDuration = (rawWords.sumOf { it.length } * 105L + 400L).coerceIn(1000L, 6500L)
                            val effectiveVocalDuration = lineDurationMs.coerceAtMost(maxVocalDuration)

                            var currentWordStart = lyricLine.timestampMs
                            rawWords.mapIndexed { idx, word ->
                                val wordFraction = (word.length.coerceAtLeast(2) / totalWeight)
                                val wordDur = (effectiveVocalDuration * wordFraction).toLong().coerceAtLeast(180L)
                                val cue = com.example.hunterxmusic.domain.model.WordCue(
                                    text = word,
                                    startMs = currentWordStart,
                                    endMs = currentWordStart + wordDur
                                )
                                currentWordStart += wordDur
                                cue
                            }
                        }
                    }
                }

                // ── LUXURY WORD-BY-WORD SYNCHRONIZED KARAOKE ───────────────
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    cues.forEachIndexed { wi, cue ->
                        val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + (cue.text.length * 90L + 200L).coerceIn(200L, 1000L))
                        // Word timestamps are the SOLE authority:
                        val isCurrent = currentPos >= cue.startMs && currentPos < cueEnd
                        val isSung = currentPos >= cueEnd

                        val wordScale by animateFloatAsState(
                            targetValue = if (isCurrent) 1.14f else 1f,
                            animationSpec = spring(dampingRatio = 0.58f, stiffness = 450f),
                            label = "wordScale$wi"
                        )

                        val activeTextColor by androidx.compose.animation.animateColorAsState(
                            targetValue = when {
                                isCurrent -> Color(0xFF38BDF8)
                                isSung -> Color.White
                                else -> Color.White.copy(alpha = 0.36f)
                            },
                            animationSpec = tween(140),
                            label = "wordColor$wi"
                        )

                        Text(
                            text = cue.text + " ",
                            style = TextStyle(
                                fontSize = if (cues.size > 22) 15.sp else if (cues.size > 14) 17.5.sp else if (cues.size > 8) 19.5.sp else 22.sp,
                                fontWeight = when {
                                    isCurrent -> FontWeight.Black
                                    isSung -> FontWeight.ExtraBold
                                    else -> FontWeight.SemiBold
                                },
                                fontFamily = FontFamily.SansSerif,
                                lineHeight = if (cues.size > 14) 26.sp else 32.sp,
                                textAlign = TextAlign.Center,
                                color = activeTextColor,
                                shadow = when {
                                    isCurrent -> androidx.compose.ui.graphics.Shadow(
                                        color = Color(0xFF38BDF8).copy(alpha = 0.92f),
                                        offset = androidx.compose.ui.geometry.Offset.Zero,
                                        blurRadius = 28f
                                    )
                                    isSung -> androidx.compose.ui.graphics.Shadow(
                                        color = Color.White.copy(alpha = 0.25f),
                                        offset = androidx.compose.ui.geometry.Offset.Zero,
                                        blurRadius = 8f
                                    )
                                    else -> null
                                }
                            ),
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = wordScale
                                    scaleY = wordScale
                                }
                                .clickable(
                                    onClick = {
                                        val targetSeekMs = (cue.startMs - totalOffset).coerceAtLeast(0L)
                                        onLyricClick(targetSeekMs)
                                    },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                        )
                    }
                }
            } else {
                Text(
                    text = lyricLine.words,
                    style = TextStyle(
                        fontSize = if (lyricLine.words.length > 56) 15.sp else if (lyricLine.words.length > 40) 17.5.sp else 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF71717A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            if (!translation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = translation,
                    style = TextStyle(
                        fontSize = if (isActive) 15.sp else 13.5.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        color = if (isActive) Color(0xFFC7D2FE).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.42f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/**
 * Instrumental break — three ♪ notes that breathe while the music plays.
 *
 * When this stretch is the active one the notes brighten and pulse in a
 * staggered wave, so the screen keeps moving through a 13-second gap instead of
 * holding the last sung line frozen. Inactive breaks sit back as dim glyphs.
 */
@Composable
private fun InstrumentalRow(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "instrumentalPulse")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "instrumentalPhase"
    )

    val rowAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.30f,
        animationSpec = tween(250),
        label = "instrumentalAlpha"
    )
    val rowScale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 0.9f,
        animationSpec = tween(300),
        label = "instrumentalScale"
    )

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .graphicsLayer {
                alpha = rowAlpha
                scaleX = rowScale
                scaleY = rowScale
            }
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        repeat(3) { i ->
            // Each note leads the next by a third of the cycle.
            val noteWave = if (isActive) {
                val p = (phase + i * 0.33f) % 1f
                kotlin.math.sin(p * Math.PI.toFloat())
            } else 0f

            Text(
                text = "♪",
                style = TextStyle(
                    fontSize = (22 + noteWave * 8f).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (isActive) {
                        Color.White.copy(alpha = 0.55f + noteWave * 0.45f)
                    } else {
                        Color(0xFF71717A)
                    }
                ),
                modifier = Modifier
                    .padding(horizontal = 7.dp)
                    .graphicsLayer { translationY = -noteWave * 5f }
            )
        }
    }
}

/**
 * One unsynced line — elegant reading-sheet styling with a bright, premium
 * look even without timestamps.
 */
@Composable
private fun PlainLyricRow(lyricLine: LyricLine, translation: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 7.dp)
    ) {
        Text(
            text = lyricLine.words,
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 32.sp,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.86f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (!translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = translation,
                style = TextStyle(
                    fontSize = 14.5.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFC7D2FE).copy(alpha = 0.75f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Animated loading state while lyric providers are queried.
 */
@Composable
private fun LyricsLoadingState() {
    val transitionPhase by rememberInfiniteTransition(label = "lyricsLoading").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.Center) {
                repeat(3) { dot ->
                    val phase = (transitionPhase + dot * 0.33f) % 1f
                    val scale = 0.6f + 0.4f * (1f - abs(phase - 0.5f) * 2f)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 5.dp)
                            .size(9.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f + 0.5f * scale))
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Synchronized Lyrics",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Searching global lyric databases...",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
