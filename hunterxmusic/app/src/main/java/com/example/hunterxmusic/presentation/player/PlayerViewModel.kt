package com.example.hunterxmusic.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hunterxmusic.data.player.MusicPlayerManager
import com.example.hunterxmusic.data.player.PlaybackState
import com.example.hunterxmusic.domain.model.LyricLine
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.domain.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel that delegates all playback to MusicPlayerManager.
 * Acts as thin wrapper exposing player state and actions to the UI.
 */
class PlayerViewModel(
    val playerManager: MusicPlayerManager,
    private val musicRepository: MusicRepository,
    private val recentTracksStore: com.example.hunterxmusic.data.local.RecentTracksStore? = null
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    val activeDownloads = musicRepository.activeDownloads

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    private var sleepTimerJob: Job? = null
    private val _sleepTimerText = MutableStateFlow<String?>(null)
    val sleepTimerText: StateFlow<String?> = _sleepTimerText.asStateFlow()

    init {
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collectLatest { track ->
                    _lyrics.value = emptyList()
                    if (track != null) {
                        try {
                            val resolvedSource = musicRepository.getResolvedAudioSource(track.id)
                            val trackLyrics = musicRepository.getLyrics(
                                trackId = track.id,
                                title = track.title,
                                artist = track.artist,
                                audioSource = resolvedSource?.sourceType ?: com.example.hunterxmusic.domain.repository.AudioSourceType.UNKNOWN,
                                audioSourceId = resolvedSource?.sourceId,
                                durationMs = track.durationMs
                            )
                            _lyrics.value = trackLyrics
                        } catch (e: Exception) {
                            _lyrics.value = emptyList()
                        }
                    } else {
                        _lyrics.value = emptyList()
                    }
                }
        }
    }

    val playbackSpeed: StateFlow<Float> = playerManager.playbackSpeed
    val vocalMode: StateFlow<com.example.hunterxmusic.core.audio.AudioVocalMode> = playerManager.vocalMode
    val acousticSyncOffsetMs: StateFlow<Long> = playerManager.acousticSyncOffsetMs

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    fun cycleVocalMode(): com.example.hunterxmusic.core.audio.AudioVocalMode {
        return playerManager.cycleVocalMode()
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerText.value = null
            playerManager.setSleepVolumeMultiplier(1.0f)
            return
        }
        
        sleepTimerJob = viewModelScope.launch {
            var secondsLeft = minutes * 60
            while (secondsLeft > 0) {
                val mins = secondsLeft / 60
                val secs = secondsLeft % 60
                _sleepTimerText.value = String.format("%d:%02d", mins, secs)
                
                if (secondsLeft <= 30) {
                    val multiplier = secondsLeft.toFloat() / 30f
                    playerManager.setSleepVolumeMultiplier(multiplier)
                } else {
                    playerManager.setSleepVolumeMultiplier(1.0f)
                }
                
                delay(1000)
                secondsLeft--
            }
            playerManager.pause()
            playerManager.setSleepVolumeMultiplier(1.0f)
            _sleepTimerText.value = null
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleLikeTrack(track)
        }
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            musicRepository.downloadTrack(track).collect {
                // The DB update automatically triggers state update
            }
        }
    }

    fun playTrack(track: Track) {
        recentTracksStore?.add(track)
        playerManager.playTrack(track)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (startIndex in tracks.indices) {
            recentTracksStore?.add(tracks[startIndex])
        }
        playerManager.playQueue(tracks, startIndex)
    }

    fun enqueueTrack(track: Track) {
        playerManager.enqueueTrack(track)
    }

    fun playNext(track: Track) {
        playerManager.playNext(track)
    }

    fun toggleUltraMix() {
        playerManager.toggleUltraMix()
    }

    fun toggleAutoplay() {
        playerManager.toggleAutoplay()
    }

    fun cycleRandomnessLevel() {
        playerManager.cycleRandomnessLevel()
    }

    fun playSurpriseNext() {
        playerManager.playSurpriseNext()
    }

    fun removeQueueItem(index: Int) {
        playerManager.removeQueueItem(index)
    }

    fun clearQueueAfterCurrent() {
        playerManager.clearQueueAfterCurrent()
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun skipToNext() {
        playerManager.skipToNext()
    }

    fun skipToPrevious() {
        playerManager.skipToPrevious()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playerManager.cycleRepeatMode()
    }

    fun deleteDownloadedTrack(track: Track) {
        viewModelScope.launch {
            musicRepository.deleteTrack(track)
        }
    }

    /** Seeds a queue from the current track: title-similar + artist hits. */
    fun startSimilarMix(title: String, artist: String) {
        viewModelScope.launch {
            try {
                val merged = listOf(
                    "songs like $title $artist",
                    "$artist similar artists mix",
                    "$artist top hits songs"
                ).mapNotNull { q ->
                    try { musicRepository.searchTracks(q) } catch (_: Exception) { null }
                }.flatten().distinctBy { it.id }
                if (merged.isNotEmpty()) {
                    playerManager.playQueue(merged, 0)
                }
            } catch (_: Exception) { }
        }
    }

    fun startSingerRadio(artistName: String) {
        viewModelScope.launch {
            try {
                val radioTracks = musicRepository.searchTracks("$artistName radio mix top hits songs")
                if (radioTracks.isNotEmpty()) {
                    playerManager.playQueue(radioTracks, 0)
                }
            } catch (_: Exception) { }
        }
    }

    /** Plays the chosen track and seeds a fresh, diverse queue from user taste and track style. */
    fun playTrackWithTasteRadio(track: Track) {
        recentTracksStore?.add(track)
        viewModelScope.launch {
            try {
                playerManager.playTrack(track)
                val historyQueries = try { musicRepository.getListeningHistoryQueries() } catch (_: Exception) { emptyList() }
                val tasteQueries = listOfNotNull(
                    "${track.title} ${track.artist}",
                    "${track.artist} songs top hits",
                    historyQueries.firstOrNull(),
                    "top trending viral music 2026"
                )
                val tasteRecommendations = mutableListOf<Track>()
                for (query in tasteQueries) {
                    val pool = try { musicRepository.searchTracks(query) } catch (_: Exception) { emptyList() }
                    for (t in pool.shuffled()) {
                        if (t.id != track.id && tasteRecommendations.none { it.id == t.id }) {
                            tasteRecommendations.add(t)
                        }
                        if (tasteRecommendations.size >= 12) break
                    }
                    if (tasteRecommendations.size >= 12) break
                }
                for (rec in tasteRecommendations) {
                    playerManager.enqueueTrack(rec)
                }
            } catch (_: Exception) { }
        }
    }

    val volumeBoost: StateFlow<Float> = playerManager.volumeBoost

    fun setVolumeBoost(db: Float) {
        playerManager.setVolumeBoost(db)
    }
}
