package com.example.hunterxmusic.data.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.domain.repository.MusicRepository
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Playback state exposed to UI layer.
 */
data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val ultraMixEnabled: Boolean = false,
    // On by default for YouTube-style behavior: after a tapped song finishes,
    // the player keeps going with related tracks instead of stopping.
    val autoplayEnabled: Boolean = true,
    val randomnessLevel: Int = 2,
    val queueMessage: String? = null
)

/**
 * Singleton manager bridging the UI layer to the Media3 ExoPlayer running inside
 * [MusicPlaybackService]. Exposes reactive [PlaybackState] and provides
 * play/pause/seek/queue controls.
 */
class MusicPlayerManager(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val vocalRemoverAudioProcessor: com.example.hunterxmusic.core.audio.VocalRemoverAudioProcessor = com.example.hunterxmusic.core.audio.VocalRemoverAudioProcessor(),
    private val localLibraryManager: com.example.hunterxmusic.data.repository.LocalLibraryManager? = null,
    private val personalizationEngine: com.example.hunterxmusic.data.analytics.PersonalizationEngine? = null
) {
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var positionTrackerJob: Job? = null
    private val _queue = mutableListOf<Track>()
    private val resolvingQueueIndices = mutableSetOf<Int>()
    private var lastRecordedTrackId: String? = null
    private val recentTrackIds = ArrayDeque<String>()
    private var autoplayJob: Job? = null

    private var activeTrackObserverJob: Job? = null
    private var playbackJob: Job? = null
    private var lastErrorTrackId: String? = null
    private var retryCount = 0

    private val _volumeBoost = MutableStateFlow(0f)
    val volumeBoost: StateFlow<Float> = _volumeBoost.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _vocalMode = MutableStateFlow(com.example.hunterxmusic.core.audio.AudioVocalMode.NORMAL)
    val vocalMode: StateFlow<com.example.hunterxmusic.core.audio.AudioVocalMode> = _vocalMode.asStateFlow()

    private val _acousticSyncOffsetMs = MutableStateFlow(0L)
    val acousticSyncOffsetMs: StateFlow<Long> = _acousticSyncOffsetMs.asStateFlow()

    // Three-state karaoke cycle: full mix -> vocals muted -> vocals only
    // (acapella) -> full mix. VOCAL_ONLY was previously dead code — the
    // processor implemented it but nothing ever selected it.
    fun cycleVocalMode(): com.example.hunterxmusic.core.audio.AudioVocalMode {
        val next = when (_vocalMode.value) {
            com.example.hunterxmusic.core.audio.AudioVocalMode.NORMAL -> com.example.hunterxmusic.core.audio.AudioVocalMode.INSTRUMENTAL_ONLY
            com.example.hunterxmusic.core.audio.AudioVocalMode.INSTRUMENTAL_ONLY -> com.example.hunterxmusic.core.audio.AudioVocalMode.VOCAL_ONLY
            com.example.hunterxmusic.core.audio.AudioVocalMode.VOCAL_ONLY -> com.example.hunterxmusic.core.audio.AudioVocalMode.NORMAL
        }
        _vocalMode.value = next
        vocalRemoverAudioProcessor.setMode(next)
        return next
    }

    fun setAcousticSyncOffset(offsetMs: Long) {
        _acousticSyncOffsetMs.value = offsetMs
    }

    private var sleepVolumeMultiplier = 1f
    private var lastAppliedVolumeMultiplier = -1f
    private var activeSkipSegments: List<Pair<Long, Long>> = emptyList()
    private var lastSponsorSkipTargetMs = -1L
    private var segmentsJob: kotlinx.coroutines.Job? = null
    private var segmentsForTrackId: String? = null

    private fun fetchSkipSegmentsForTrack(track: Track) {
        // Cancel any in-flight fetch for the PREVIOUS track: a slow response
        // landing after a rapid switch used to apply track A's segments to
        // track B → spurious mid-song jumps.
        segmentsJob?.cancel()
        activeSkipSegments = emptyList()
        lastSponsorSkipTargetMs = -1L
        if (!musicRepository.isSponsorBlockEnabled()) return
        val videoId = track.id
        if (videoId.length == 11) {
            segmentsForTrackId = videoId
            segmentsJob = scope.launch {
                try {
                    val fetched = musicRepository.getSkipSegments(videoId)
                    // Only accept if THIS track is still the one playing.
                    if (segmentsForTrackId == videoId && _playbackState.value.currentTrack?.id == videoId) {
                        activeSkipSegments = fetched
                        if (fetched.isNotEmpty()) {
                            Log.d("MusicPlayerManager", "Fetched ${fetched.size} skip segments for $videoId: $fetched")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Error fetching skip segments: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val NEXT_TRACK_PREFETCH_WINDOW_MS = 12_000L
        private const val RECENT_TRACK_MEMORY = 40
        private const val AUTOPLAY_BATCH_SIZE = 10
    }

    fun setVolumeBoost(db: Float) {
        _volumeBoost.value = db.coerceIn(0f, 15f)
    }

    fun setSleepVolumeMultiplier(multiplier: Float) {
        sleepVolumeMultiplier = multiplier.coerceIn(0f, 1f)
        updateVolume()
    }

    fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = s
        mediaController?.setPlaybackSpeed(s)
    }

    private fun updateVolume() {
        val controller = mediaController ?: return
        // Only push to the MediaSession binder when the multiplier actually changed —
        // this used to fire every 50ms position tick.
        if (lastAppliedVolumeMultiplier != sleepVolumeMultiplier) {
            lastAppliedVolumeMultiplier = sleepVolumeMultiplier
            controller.volume = sleepVolumeMultiplier
        }
    }

    private fun observeActiveTrack(trackId: String) {
        activeTrackObserverJob?.cancel()
        activeTrackObserverJob = scope.launch {
            musicRepository.getTrack(trackId).collect { dbTrack ->
                if (dbTrack != null) {
                    val currentState = _playbackState.value
                    if (currentState.currentTrack?.id == trackId) {
                        _playbackState.value = currentState.copy(
                            currentTrack = dbTrack
                        )
                        val idx = _queue.indexOfFirst { it.id == trackId }
                        if (idx != -1) {
                            _queue[idx] = dbTrack
                        }
                    }
                }
            }
        }
    }

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, "com.example.hunterxmusic.service.MusicPlaybackService")
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                lastAppliedVolumeMultiplier = -1f // force volume re-apply on the fresh controller
                updateVolume()
                setupPlayerListener()
                mediaController?.let { controller ->
                    controller.setPlaybackSpeed(_playbackSpeed.value)
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = controller.isPlaying,
                        isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                        shuffleEnabled = controller.shuffleModeEnabled,
                        repeatMode = controller.repeatMode,
                        currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                        durationMs = controller.duration.takeIf { it > 0L } ?: 0L
                    )
                    val track = _playbackState.value.currentTrack
                    if (track != null) {
                        fetchSkipSegmentsForTrack(track)
                    }
                    if (controller.isPlaying) startPositionTracker()
                }
            } catch (e: Exception) {
                // Service not available yet, retry
                scope.launch {
                    delay(2000)
                    connectToService()
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startPositionTracker() else stopPositionTracker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING
                )
                if (playbackState == Player.STATE_READY) {
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = mediaController?.currentPosition ?: 0L,
                        durationMs = mediaController?.duration?.takeIf { it > 0L }
                            ?: _playbackState.value.currentTrack?.durationMs
                            ?: 0L
                    )
                    prefetchNextFromController()
                }
                if (playbackState == Player.STATE_ENDED) {
                    // Never auto stop! Continuous endless playback until user explicitly pauses or stops
                    skipToNext()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                val track = if (!mediaId.isNullOrBlank()) {
                    _queue.find { it.id == mediaId }
                } else {
                    val idx = mediaController?.currentMediaItemIndex ?: -1
                    if (idx in _queue.indices) _queue[idx] else null
                }

                if (track != null) {
                    val idx = _queue.indexOf(track)
                    _playbackState.value = _playbackState.value.copy(
                        currentTrack = track,
                        queueIndex = if (idx != -1) idx else 0,
                        currentPositionMs = mediaController?.currentPosition ?: 0L,
                        durationMs = mediaController?.duration?.takeIf { it > 0L } ?: track.durationMs
                    )
                    observeActiveTrack(track.id)
                    recordCurrentTrack(track)
                    personalizationEngine?.recordPlay(track)
                    fetchSkipSegmentsForTrack(track)
                    // Resolve the next couple of items now that we've advanced,
                    // instead of resolving the whole queue on playQueue().
                    if (idx != -1) {
                        scope.launch { prefetchAround(idx) }
                    }
                    // The catalog's duration metadata is often 0 (notably for
                    // imported playlist tracks). ExoPlayer knows the real one
                    // once prepared — write it back so lyric duration
                    // verification actually has something to check against.
                    backfillRealDuration(track)
                    retryCount = 0
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playbackState.value = _playbackState.value.copy(shuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _playbackState.value = _playbackState.value.copy(repeatMode = repeatMode)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicPlayerManager", "ExoPlayer playback error: ${error.message}", error)
                val currentTrack = _playbackState.value.currentTrack
                val index = mediaController?.currentMediaItemIndex ?: -1
                
                if (currentTrack != null && index != -1) {
                    if (lastErrorTrackId == currentTrack.id && retryCount >= 1) {
                        Log.w("MusicPlayerManager", "Track ${currentTrack.title} failed playback repeatedly. Auto-skipping to next song.")
                        retryCount = 0
                        lastErrorTrackId = null
                        scope.launch { skipToNext() }
                        return
                    }
                    
                    lastErrorTrackId = currentTrack.id
                    retryCount++
                    
                    scope.launch {
                        _playbackState.value = _playbackState.value.copy(isBuffering = true)
                        // Force a fresh resolve from repository bypassing cache check
                        val newUrl = musicRepository.getStreamingUrl(currentTrack)
                        if (newUrl.isNotBlank() && (newUrl.startsWith("http") || newUrl.startsWith("encrypted"))) {
                            val updatedTrack = currentTrack.copy(streamingUrl = newUrl)
                            if (index in _queue.indices) {
                                _queue[index] = updatedTrack
                            }
                            _playbackState.value = _playbackState.value.copy(
                                currentTrack = updatedTrack,
                                queue = _queue.toList()
                            )
                            mediaController?.let { controller ->
                                val position = controller.currentPosition
                                controller.replaceMediaItem(index, buildMediaItem(updatedTrack, newUrl))
                                controller.prepare()
                                controller.seekTo(index, position)
                                controller.play()
                            }
                            return@launch
                        }
                        
                        // If fresh resolve also fails, don't halt: skip to next song automatically!
                        retryCount = 0
                        lastErrorTrackId = null
                        skipToNext()
                    }
                } else {
                    scope.launch { skipToNext() }
                }
            }
        })
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = scope.launch {
            while (isActive) {
                val pos = mediaController?.currentPosition ?: 0L
                val dur = mediaController?.duration ?: 0L
                
                val controller = mediaController
                if (controller != null && controller.isPlaying && controller.playbackState == Player.STATE_READY) {
                    if (musicRepository.isSponsorBlockEnabled() && activeSkipSegments.isNotEmpty()) {
                        val posSec = pos / 1000f
                        // HEAD GUARD: never auto-seek out of a segment that
                        // starts at the song's head — SponsorBlock's intro/
                        // music_offtopic segments routinely span [0..X]s and
                        // this seek is exactly why songs started mid-way.
                        // Only skip a segment we've genuinely played into.
                        val matchingSegment = activeSkipSegments.firstOrNull { segment ->
                            posSec >= segment.first && posSec < segment.second && segment.first >= 3f
                        }
                        if (matchingSegment != null) {
                            val seekTargetMs = (matchingSegment.second * 1000L).toLong() + 50L
                            // Duration clamp: a bad segment must never seek
                            // past the end (that silently skips whole tracks).
                            val clampedTarget = if (dur > 0) seekTargetMs.coerceAtMost(dur - 500) else seekTargetMs
                            if (clampedTarget > pos + 1000L && clampedTarget != lastSponsorSkipTargetMs) {
                                lastSponsorSkipTargetMs = clampedTarget
                                controller.seekTo(clampedTarget)
                                _playbackState.value = _playbackState.value.copy(currentPositionMs = clampedTarget)
                                Toast.makeText(context, "SponsorBlock: Skipped segment", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = mediaController?.currentPosition ?: pos,
                    durationMs = if (dur > 0) dur else _playbackState.value.durationMs
                )
                updateVolume()
                if (dur > 0L && dur - pos <= NEXT_TRACK_PREFETCH_WINDOW_MS) {
                    prefetchNextFromController()
                }
                delay(50)
            }
        }
    }

    private fun stopPositionTracker() {
        positionTrackerJob?.cancel()
    }

    /**
     * Play a single track immediately. Resolves streaming URL, sets MediaItem, and starts playback.
     */
    fun playTrack(track: Track) {
        playbackJob?.cancel()
        // Immediately silence and pause old audio so track switch is instantaneous
        mediaController?.pause()
        _playbackState.value = _playbackState.value.copy(
            currentTrack = track,
            isPlaying = false,
            isBuffering = true,
            currentPositionMs = 0L,
            queueMessage = null
        )
        playbackJob = scope.launch {
            val controller = awaitController()
            if (controller == null) {
                Toast.makeText(context, "Player engine not ready. Please try again.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            controller.pause()

            com.example.hunterxmusic.core.network.NetworkFocusManager.acquirePlaybackPriority()
            resolvingQueueIndices.clear()
            _queue.clear()
            _queue.add(track)
            _playbackState.value = _playbackState.value.copy(
                currentTrack = track,
                queue = _queue.toList(),
                queueIndex = 0,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 0L,
                queueMessage = null
            )
            observeActiveTrack(track.id)

            // Resolve streaming URL in background with top network priority
            val url = resolveUrl(track)
            if (url.isBlank()) {
                com.example.hunterxmusic.core.network.NetworkFocusManager.releasePlaybackPriority()
                Log.w("MusicPlayerManager", "Track ${track.title} stream unavailable. Skipping to next song.")
                scope.launch { skipToNext() }
                return@launch
            }

            if (!isActive) {
                com.example.hunterxmusic.core.network.NetworkFocusManager.releasePlaybackPriority()
                return@launch
            }

            val updatedTrack = track.copy(streamingUrl = url)
            _queue[0] = updatedTrack
            _playbackState.value = _playbackState.value.copy(
                currentTrack = updatedTrack,
                queue = _queue.toList()
            )

            val mediaItem = buildMediaItem(updatedTrack, url)
            controller.apply {
                setPlaybackSpeed(_playbackSpeed.value)
                setMediaItem(mediaItem)
                prepare()
                play()
            }

            // Release playback critical lock after audio buffer is started
            scope.launch {
                delay(1200)
                com.example.hunterxmusic.core.network.NetworkFocusManager.releasePlaybackPriority()
            }

            // Non-blocking follow-up work — nothing here delays first audio.
            fetchSkipSegmentsForTrack(updatedTrack)
            recordCurrentTrack(updatedTrack)
            ensureAutoplayQueue(updatedTrack)
        }
    }

    /**
     * Play a list of tracks starting from the given index.
     */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        playbackJob?.cancel()
        // Immediately silence old audio upon switching queue
        mediaController?.pause()
        val initialIndex = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        val initialTrack = tracks.getOrNull(initialIndex)
        _playbackState.value = _playbackState.value.copy(
            currentTrack = initialTrack,
            isPlaying = false,
            isBuffering = true,
            currentPositionMs = 0L,
            queueMessage = null
        )
        playbackJob = scope.launch {
            if (tracks.isEmpty()) {
                Toast.makeText(context, "No tracks to play.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val controller = awaitController()
            if (controller == null) {
                Toast.makeText(context, "Player engine not ready. Please try again.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            controller.pause()

            val safeStartIndex = startIndex.coerceIn(0, tracks.lastIndex)
            com.example.hunterxmusic.core.network.NetworkFocusManager.acquirePlaybackPriority()
            resolvingQueueIndices.clear()
            _queue.clear()
            _queue.addAll(tracks)
            if (_playbackState.value.ultraMixEnabled) {
                reorderUpcomingFrom(safeStartIndex, setMediaItems = false)
            }

            val startTrack = _queue[safeStartIndex]
            _playbackState.value = _playbackState.value.copy(
                currentTrack = startTrack,
                queue = _queue.toList(),
                queueIndex = safeStartIndex,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 0L,
                queueMessage = null
            )
            observeActiveTrack(startTrack.id)

            val url = resolveUrl(startTrack)
            if (url.isBlank()) {
                com.example.hunterxmusic.core.network.NetworkFocusManager.releasePlaybackPriority()
                Log.w("MusicPlayerManager", "Track ${startTrack.title} stream unavailable. Skipping to next song.")
                scope.launch { skipToNext() }
                return@launch
            }

            if (!isActive) {
                com.example.hunterxmusic.core.network.NetworkFocusManager.releasePlaybackPriority()
                return@launch
            }

            _queue[safeStartIndex] = startTrack.copy(streamingUrl = url)
            _playbackState.value = _playbackState.value.copy(
                currentTrack = _queue[safeStartIndex],
                queue = _queue.toList()
            )

            val startMediaItem = buildMediaItem(_queue[safeStartIndex], url)
            controller.apply {
                setPlaybackSpeed(_playbackSpeed.value)
                setMediaItem(startMediaItem)
                prepare()
                play()
            }

            scope.launch {
                delay(1200)
                com.example.hunterxmusic.core.network.NetworkFocusManager.releasePlaybackPriority()
            }

            fetchSkipSegmentsForTrack(_queue[safeStartIndex])
            recordCurrentTrack(_queue[safeStartIndex])

            // Prefetch ONLY the next two items.
            scope.launch {
                prefetchAround(safeStartIndex)
            }
        }
    }

    /**
     * Warms stream URLs for the items immediately after [currentIndex] so the
     * next track starts without a resolve stall, without resolving the entire
     * queue up front.
     */
    private suspend fun prefetchAround(currentIndex: Int, lookahead: Int = 2) {
        for (offset in 1..lookahead) {
            val i = currentIndex + offset
            if (i !in _queue.indices) return
            if (!currentCoroutineContext().isActive) return
            try { ensureQueueItemResolved(i) } catch (_: Exception) { }
        }
    }

    /**
     * Search results and imported playlists frequently carry `durationMs = 0`.
     * A zero duration silently disables the lyrics duration-plausibility check,
     * which is one way wrong-song lyrics get through. Once ExoPlayer has
     * prepared the stream it knows the true length, so write it back into the
     * queue and republish state — lyric selection then re-runs against a real
     * duration.
     */
    private fun backfillRealDuration(track: Track) {
        if (track.durationMs > 0L) return
        scope.launch {
            // Duration is only known after the source is prepared.
            repeat(20) {
                delay(150)
                val real = mediaController?.duration ?: 0L
                if (real > 0L) {
                    val idx = _queue.indexOfFirst { it.id == track.id }
                    if (idx in _queue.indices) {
                        val fixed = _queue[idx].copy(durationMs = real)
                        _queue[idx] = fixed
                        val current = _playbackState.value
                        _playbackState.value = current.copy(
                            queue = _queue.toList(),
                            durationMs = real,
                            currentTrack = if (current.currentTrack?.id == track.id) fixed else current.currentTrack
                        )
                    }
                    return@launch
                }
            }
        }
    }

    /** AI-bridge: like/unlike whatever is playing right now. */
    fun toggleLikeCurrent() {
        val track = _playbackState.value.currentTrack ?: return
        scope.launch {
            try { musicRepository.toggleLikeTrack(track) } catch (_: Exception) { }
        }
    }

    fun enqueueTrack(track: Track) {
        scope.launch {
            val controller = mediaController ?: return@launch
            _queue.add(track)
            _playbackState.value = _playbackState.value.copy(
                queue = _queue.toList()
            )

            val mediaItem = buildMediaItem(track, track.streamingUrl ?: "")
            controller.addMediaItem(mediaItem)

            scope.launch {
                val idx = _queue.indexOfLast { it.id == track.id }
                if (idx != -1) ensureQueueItemResolved(idx)
            }
        }
    }

    fun playNext(track: Track) {
        scope.launch {
            val controller = mediaController ?: return@launch
            val currentIndex = controller.currentMediaItemIndex.takeIf { it in _queue.indices } ?: _playbackState.value.queueIndex
            val insertIndex = (currentIndex + 1).coerceAtMost(_queue.size)
            _queue.add(insertIndex, track)
            _playbackState.value = _playbackState.value.copy(
                queue = _queue.toList(),
                queueMessage = "\"${track.title}\" will play next."
            )
            val mediaItem = buildMediaItem(track, track.streamingUrl ?: "")
            controller.addMediaItem(insertIndex, mediaItem)
            ensureQueueItemResolved(insertIndex)
        }
    }

    fun toggleUltraMix() {
        val enabled = !_playbackState.value.ultraMixEnabled
        _playbackState.value = _playbackState.value.copy(
            ultraMixEnabled = enabled,
            queueMessage = if (enabled) "UltraMix is shaping the next songs." else "UltraMix off."
        )
        if (enabled) {
            val index = mediaController?.currentMediaItemIndex ?: _playbackState.value.queueIndex
            reorderUpcomingFrom(index.coerceAtLeast(0), setMediaItems = true)
        }
    }

    fun toggleAutoplay() {
        val enabled = !_playbackState.value.autoplayEnabled
        _playbackState.value = _playbackState.value.copy(
            autoplayEnabled = enabled,
            queueMessage = if (enabled) "Autoplay will keep the queue alive." else "Autoplay off."
        )
        if (enabled) {
            ensureAutoplayQueue(_playbackState.value.currentTrack)
        }
    }

    fun cycleRandomnessLevel() {
        val nextLevel = if (_playbackState.value.randomnessLevel >= 3) 1 else _playbackState.value.randomnessLevel + 1
        _playbackState.value = _playbackState.value.copy(
            randomnessLevel = nextLevel,
            queueMessage = when (nextLevel) {
                1 -> "Randomness: Flow."
                2 -> "Randomness: Discovery."
                else -> "Randomness: Chaos."
            }
        )
        if (_playbackState.value.ultraMixEnabled) {
            val index = mediaController?.currentMediaItemIndex ?: _playbackState.value.queueIndex
            reorderUpcomingFrom(index.coerceAtLeast(0), setMediaItems = true)
        }
    }

    fun playSurpriseNext() {
        scope.launch {
            val anchor = _playbackState.value.currentTrack
            val surpriseTracks = buildAutoplayTracks(anchor, forceWideSearch = true)
            if (surpriseTracks.isEmpty()) {
                Toast.makeText(context, "No surprise tracks found yet.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            appendTracksToQueue(surpriseTracks, "Surprise queue added.")
            skipToNext()
        }
    }

    fun removeQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index !in _queue.indices || index == controller.currentMediaItemIndex) return
        _queue.removeAt(index)
        controller.removeMediaItem(index)
        val currentIndex = controller.currentMediaItemIndex
        _playbackState.value = _playbackState.value.copy(
            queue = _queue.toList(),
            queueIndex = currentIndex,
            queueMessage = "Removed from queue."
        )
    }

    fun clearQueueAfterCurrent() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex !in _queue.indices || currentIndex == _queue.lastIndex) return
        _queue.subList(currentIndex + 1, _queue.size).clear()
        controller.removeMediaItems(currentIndex + 1, controller.mediaItemCount)
        _playbackState.value = _playbackState.value.copy(
            queue = _queue.toList(),
            queueIndex = currentIndex,
            queueMessage = "Cleared upcoming queue."
        )
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun resume() {
        mediaController?.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun skipToNext() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            val currentIndex = _playbackState.value.queueIndex
            val nextIndex = currentIndex + 1
            if (nextIndex in _queue.indices) {
                val nextTrack = _queue[nextIndex]
                playQueueItem(nextIndex, nextTrack)
                // Proactively refill queue when fewer than 6 songs remain
                if (_queue.size - nextIndex < 6) {
                    ensureAutoplayQueue(nextTrack)
                }
            } else {
                // Queue exhausted! NEVER STOP: pull fresh taste tracks immediately and keep playing
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = true,
                    queueMessage = "Generating endless mix..."
                )
                val anchor = _playbackState.value.currentTrack ?: _queue.lastOrNull()
                val freshTracks = fetchEndlessTracks(anchor)
                if (freshTracks.isNotEmpty()) {
                    appendTracksToQueue(freshTracks, "Added ${freshTracks.size} more tracks")
                    val autoIndex = _playbackState.value.queueIndex + 1
                    if (autoIndex in _queue.indices) {
                        playQueueItem(autoIndex, _queue[autoIndex])
                    } else if (_queue.isNotEmpty()) {
                        playQueueItem(0, _queue[0])
                    }
                } else if (_queue.isNotEmpty()) {
                    // Loop back to start if offline or unable to discover new tracks
                    playQueueItem(0, _queue[0])
                } else {
                    _playbackState.value = _playbackState.value.copy(isBuffering = false)
                }
            }
        }
    }

    fun skipToPrevious() {
        val controller = mediaController
        if (controller != null && controller.currentPosition > 3000L) {
            controller.seekTo(0L)
            return
        }
        playbackJob?.cancel()
        playbackJob = scope.launch {
            val currentIndex = _playbackState.value.queueIndex
            val prevIndex = currentIndex - 1
            if (prevIndex in _queue.indices) {
                playQueueItem(prevIndex, _queue[prevIndex])
            }
        }
    }

    private suspend fun playQueueItem(index: Int, track: Track) {
        val controller = awaitController() ?: return
        controller.pause()
        _playbackState.value = _playbackState.value.copy(
            currentTrack = track,
            queueIndex = index,
            isPlaying = false,
            isBuffering = true,
            currentPositionMs = 0L
        )
        observeActiveTrack(track.id)

        val (url, artworkBytes) = coroutineScope {
            val urlDeferred = async(Dispatchers.IO) { resolveUrl(track) }
            val artDeferred = async(Dispatchers.IO) { fetchArtworkBytes(track.albumArtUrl) }
            Pair(urlDeferred.await(), artDeferred.await())
        }
        if (url.isBlank()) {
            Log.w("MusicPlayerManager", "Track '${track.title}' could not resolve stream. Auto-skipping to keep music playing.")
            scope.launch {
                delay(250)
                skipToNext()
            }
            return
        }

        val updatedTrack = track.copy(streamingUrl = url)
        if (index in _queue.indices) {
            _queue[index] = updatedTrack
        }
        _playbackState.value = _playbackState.value.copy(
            currentTrack = updatedTrack,
            queue = _queue.toList()
        )

        val mediaItem = buildMediaItem(updatedTrack, url, artworkBytes)
        fetchSkipSegmentsForTrack(updatedTrack)
        controller.apply {
            setPlaybackSpeed(_playbackSpeed.value)
            setMediaItem(mediaItem)
            prepare()
            play()
        }
        recordCurrentTrack(updatedTrack)
    }

    fun toggleShuffle() {
        mediaController?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun cycleRepeatMode() {
        mediaController?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    private suspend fun fetchArtworkBytes(url: String?): ByteArray? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        try {
            val loader = coil.Coil.imageLoader(context)
            val request = coil.request.ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(512, 512)
                .build()
            val result = loader.execute(request)
            if (result is coil.request.SuccessResult) {
                val drawable = result.drawable
                val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: run {
                        val w = drawable.intrinsicWidth.coerceAtLeast(1)
                        val h = drawable.intrinsicHeight.coerceAtLeast(1)
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                stream.toByteArray()
            } else null
        } catch (_: Exception) { null }
    }

    private suspend fun resolveUrl(track: Track): String {
        return withContext(Dispatchers.IO) {
            // If downloaded offline, let repository resolve the encrypted URI
            if (track.isDownloaded && !track.localFilePath.isNullOrBlank()) {
                return@withContext musicRepository.getStreamingUrl(track)
            }

            val existing = track.streamingUrl
            if (isPlayableUrl(existing)) {
                return@withContext existing.orEmpty()
            }

            try {
                val resolved = musicRepository.getStreamingUrl(track)
                if (resolved.startsWith("http") || resolved.startsWith("encrypted")) resolved else ""
            } catch (e: Exception) {
                ""
            }
        }
    }

    private fun buildMediaItem(track: Track, url: String, artworkBytes: ByteArray? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                track.albumArtUrl?.let { setArtworkUri(Uri.parse(it)) }
                if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                    setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(track.id)
            .setMediaMetadata(metadata)

        if (url.isNotBlank()) {
            builder.setUri(url)
        }

        return builder.build()
    }

    private suspend fun awaitController(): MediaController? {
        var controller = mediaController
        if (controller != null) return controller

        connectToService()
        // Poll fast. This used to be repeat(5) { delay(500) }, which cost a
        // guaranteed 500ms of silence on the very first play even when the
        // service connected in 20ms.
        repeat(40) {
            delay(50)
            controller = mediaController
            if (controller != null) return controller
        }
        return null
    }

    private fun isPlayableUrl(url: String?): Boolean {
        return !url.isNullOrBlank() && (
            url.startsWith("http") || url.startsWith("encrypted") || url.startsWith("content")
            )
    }

    private suspend fun ensureQueueItemResolved(index: Int): Boolean {
        val originalTrack = _queue.getOrNull(index) ?: return false
        if (isPlayableUrl(originalTrack.streamingUrl)) return true

        if (!resolvingQueueIndices.add(index)) {
            repeat(40) {
                if (isPlayableUrl(_queue.getOrNull(index)?.streamingUrl)) return true
                delay(150)
            }
            return isPlayableUrl(_queue.getOrNull(index)?.streamingUrl)
        }

        try {
            val url = resolveUrl(originalTrack)
            if (url.isBlank()) return false

            if (index !in _queue.indices || _queue[index].id != originalTrack.id) {
                return false
            }

            val updatedTrack = originalTrack.copy(streamingUrl = url)
            _queue[index] = updatedTrack
            val currentState = _playbackState.value
            _playbackState.value = currentState.copy(
                currentTrack = if (currentState.queueIndex == index) updatedTrack else currentState.currentTrack,
                queue = _queue.toList()
            )
            mediaController?.let { controller ->
                if (index >= 0 && index < controller.mediaItemCount) {
                    val mediaItem = buildMediaItem(updatedTrack, url)
                    controller.replaceMediaItem(index, mediaItem)
                }
            }
            return true
        } finally {
            resolvingQueueIndices.remove(index)
        }
    }

    private fun prefetchNextFromController() {
        val controller = mediaController ?: return
        val nextIndex = controller.nextMediaItemIndex
        if (nextIndex >= 0) {
            prefetchAroundIndex(nextIndex)
        } else {
            ensureAutoplayQueue(_playbackState.value.currentTrack)
        }
    }

    private fun prefetchAroundIndex(index: Int) {
        if (index !in _queue.indices) return
        scope.launch {
            ensureQueueItemResolved(index)
            val nextIndex = mediaController?.nextMediaItemIndex ?: (index + 1)
            if (nextIndex in _queue.indices) ensureQueueItemResolved(nextIndex)
        }
    }

    private fun reorderUpcomingFrom(currentIndex: Int, setMediaItems: Boolean) {
        if (currentIndex !in _queue.indices || currentIndex >= _queue.lastIndex) return

        val upcoming = _queue.drop(currentIndex + 1)
        val reordered = buildSmartOrder(upcoming, _queue[currentIndex])
        for (i in reordered.indices) {
            _queue[currentIndex + 1 + i] = reordered[i]
        }

        _playbackState.value = _playbackState.value.copy(
            queue = _queue.toList(),
            queueIndex = currentIndex,
            queueMessage = "UltraMix refreshed upcoming songs."
        )

        if (setMediaItems) {
            syncFullQueueToController(currentIndex)
        }
    }

    private fun buildSmartOrder(candidates: List<Track>, anchor: Track?): List<Track> {
        val pool = candidates.distinctBy { it.id }.toMutableList()
        val ordered = mutableListOf<Track>()
        var previousArtist = anchor?.artist?.normalizedArtistKey().orEmpty()
        val recent = recentTrackIds.toSet()
        val randomness = when (_playbackState.value.randomnessLevel.coerceIn(1, 3)) {
            1 -> 0.25
            2 -> 0.75
            else -> 1.75
        }

        while (pool.isNotEmpty()) {
            val picked = pool.minBy { track ->
                val artist = track.artist.normalizedArtistKey()
                val sameArtistPenalty = if (artist.isNotBlank() && artist == previousArtist) 4.0 else 0.0
                val recentPenalty = if (track.id in recent) 3.0 else 0.0
                val downloadedBonus = if (track.isDownloaded) -0.4 else 0.0
                Random.nextDouble(0.0, randomness) + sameArtistPenalty + recentPenalty + downloadedBonus
            }
            ordered.add(picked)
            previousArtist = picked.artist.normalizedArtistKey()
            pool.remove(picked)
        }

        return ordered
    }

    private fun syncFullQueueToController(currentIndex: Int) {
        val controller = mediaController ?: return
        if (_queue.isEmpty()) return

        val safeIndex = currentIndex.coerceIn(0, _queue.lastIndex)
        val wasPlaying = controller.isPlaying
        val position = controller.currentPosition.coerceAtLeast(0L)
        controller.setMediaItems(
            _queue.map { buildMediaItem(it, it.streamingUrl.orEmpty()) },
            safeIndex,
            position
        )
        controller.prepare()
        if (wasPlaying) controller.play()
    }

    private fun appendTracksToQueue(tracks: List<Track>, message: String) {
        val controller = mediaController ?: return
        val existingIds = _queue.map { it.id }.toMutableSet()
        val uniqueTracks = tracks
            .filter { it.id !in existingIds }
            .distinctBy { it.id }
            .take(AUTOPLAY_BATCH_SIZE)

        if (uniqueTracks.isEmpty()) return

        _queue.addAll(uniqueTracks)
        if (_playbackState.value.ultraMixEnabled) {
            val currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
            reorderUpcomingFrom(currentIndex, setMediaItems = false)
            syncFullQueueToController(currentIndex)
        } else {
            controller.addMediaItems(uniqueTracks.map { buildMediaItem(it, it.streamingUrl.orEmpty()) })
        }

        _playbackState.value = _playbackState.value.copy(
            queue = _queue.toList(),
            queueIndex = controller.currentMediaItemIndex,
            queueMessage = message
        )

        scope.launch {
            val nextIndex = controller.nextMediaItemIndex
            if (nextIndex in _queue.indices) ensureQueueItemResolved(nextIndex)
        }
    }

    private fun ensureAutoplayQueue(anchor: Track?) {
        if (autoplayJob?.isActive == true) return

        val currentIndex = mediaController?.currentMediaItemIndex ?: _playbackState.value.queueIndex
        val remaining = _queue.lastIndex - currentIndex
        if (remaining >= 6) return

        val targetAnchor = anchor ?: _playbackState.value.currentTrack ?: _queue.lastOrNull()

        autoplayJob = scope.launch {
            _playbackState.value = _playbackState.value.copy(queueMessage = "Autoplay queueing more songs...")
            val tracks = fetchEndlessTracks(targetAnchor)
            if (tracks.isNotEmpty()) {
                appendTracksToQueue(tracks, "Autoplay added ${tracks.size.coerceAtMost(AUTOPLAY_BATCH_SIZE)} songs.")
            }
        }
    }

    suspend fun fetchEndlessTracks(anchor: Track?): List<Track> {
        val tracks = buildAutoplayTracks(anchor, forceWideSearch = false)
        if (tracks.isNotEmpty()) return tracks
        val wideTracks = buildAutoplayTracks(anchor, forceWideSearch = true)
        if (wideTracks.isNotEmpty()) return wideTracks
        val trending = withContext(Dispatchers.IO) {
            try { musicRepository.getTrendingSongs("global") } catch (_: Exception) { emptyList() }
        }
        val existingIds = _queue.map { it.id }.toSet()
        val freshTrending = trending.filter { it.id !in existingIds }
        if (freshTrending.isNotEmpty()) return freshTrending.take(AUTOPLAY_BATCH_SIZE)
        return trending.shuffled().take(AUTOPLAY_BATCH_SIZE)
    }

    private val sessionPlayedTrackIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private suspend fun buildAutoplayTracks(anchor: Track?, forceWideSearch: Boolean): List<Track> {
        val existingIds = _queue.map { it.id }.toMutableSet()
        existingIds.addAll(recentTrackIds)
        existingIds.addAll(sessionPlayedTrackIds)

        // Songs played in the last few sessions or current session stay out of autoplay —
        // users hear fresh music without repeats.
        val recentlyPlayedKeys = withContext(Dispatchers.IO) {
            try { musicRepository.getRecentlyPlayedKeys() } catch (_: Exception) { emptySet() }
        }
        fun isRecentlyHeard(track: Track): Boolean {
            val key = "${track.artist.trim().lowercase()}|${track.title.trim().lowercase()}"
            return key in recentlyPlayedKeys || key in sessionPlayedTrackIds || track.id in sessionPlayedTrackIds
        }

        val baseQueries = mutableListOf<String>()
        if (anchor != null) {
            val artist = anchor.artist.takeUnless { it.isBlank() || it.contains("Unknown", ignoreCase = true) }
            if (!artist.isNullOrBlank()) {
                baseQueries += "$artist songs"
                baseQueries += "$artist popular hits"
            }
            baseQueries += "${anchor.title} ${anchor.artist}"
        }

        baseQueries += withContext(Dispatchers.IO) { musicRepository.getListeningHistoryQueries() }

        if (forceWideSearch || baseQueries.isEmpty()) {
            baseQueries += listOf(
                "trending songs",
                "fresh music mix",
                "viral hits",
                "new releases",
                "songs for party",
                "chill hits"
            )
        }

        val discovered = mutableListOf<Track>()
        val queryList = baseQueries.distinct().filter { it.isNotBlank() }.shuffled()
        for (query in queryList) {
            val results = withContext(Dispatchers.IO) {
                try {
                    musicRepository.searchTracks(query)
                } catch (_: Exception) {
                    emptyList()
                }
            }

            for (track in results.shuffled()) {
                if (track.id !in existingIds && discovered.none { it.id == track.id } && !isRecentlyHeard(track)) {
                    discovered += track
                    existingIds += track.id
                }
                if (discovered.size >= AUTOPLAY_BATCH_SIZE) break
            }
            if (discovered.size >= AUTOPLAY_BATCH_SIZE) break
        }

        // If discovered is still empty after filtering, relax the recently-heard filter to never starve queue
        if (discovered.isEmpty()) {
            for (query in queryList) {
                val results = withContext(Dispatchers.IO) {
                    try { musicRepository.searchTracks(query) } catch (_: Exception) { emptyList() }
                }
                for (track in results.shuffled()) {
                    if (track.id !in existingIds && discovered.none { it.id == track.id }) {
                        discovered += track
                        existingIds += track.id
                    }
                    if (discovered.size >= AUTOPLAY_BATCH_SIZE) break
                }
                if (discovered.size >= AUTOPLAY_BATCH_SIZE) break
            }
        }

        return buildSmartOrder(discovered, anchor)
    }

    private fun recordCurrentTrack(track: Track) {
        sessionPlayedTrackIds.add(track.id)
        sessionPlayedTrackIds.add("${track.artist.trim().lowercase()}|${track.title.trim().lowercase()}")
        if (lastRecordedTrackId == track.id) return
        lastRecordedTrackId = track.id
        recentTrackIds.remove(track.id)
        recentTrackIds.addLast(track.id)
        while (recentTrackIds.size > RECENT_TRACK_MEMORY) {
            recentTrackIds.removeFirst()
        }
        scope.launch(Dispatchers.IO) {
            musicRepository.recordListenedTrack(track)
        }
        // Full-snapshot history for the Recently Played shelf
        localLibraryManager?.recordHistory(track)
    }

    private fun String.normalizedArtistKey(): String {
        return lowercase()
            .replace(Regex("\\b(feat|ft|featuring|and|&|,).*"), "")
            .trim()
    }
}
