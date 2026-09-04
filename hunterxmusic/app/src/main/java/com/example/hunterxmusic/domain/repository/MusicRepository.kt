package com.example.hunterxmusic.domain.repository

import com.example.hunterxmusic.domain.model.LyricLine
import com.example.hunterxmusic.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class AudioSourceType {
    YOUTUBE,
    JIOSAAVN,
    NEWPIPE,
    LOCAL,
    UNKNOWN
}

data class ResolvedAudioSource(
    val sourceType: AudioSourceType,
    val sourceId: String,
    val streamUrl: String
)

/**
 * Domain boundary interface governing track discovery, synchronized lyrics retrieval, and download processes.
 */
interface MusicRepository {

    /**
     * Exposes live map of active download progresses by Track ID.
     */
    val activeDownloads: StateFlow<Map<String, DownloadProgress>>

    suspend fun searchTracks(query: String): List<Track>

    /**
     * Queries global music catalog, returning tracks and continuation token for next page.
     */
    suspend fun searchTracksPaginated(query: String): Pair<List<Track>, String?>

    /**
     * Loads next page of search results using the continuation token.
     */
    suspend fun searchTracksNextPage(continuationToken: String): Pair<List<Track>, String?>

    /**
     * Fetches trending songs for a specific language/region.
     */
    suspend fun getTrendingSongs(language: String): List<Track>

    /**
     * Returns search suggestions based on user's search history.
     */
    fun getSearchSuggestions(partialQuery: String): List<String>

    /**
     * Saves a successful search query to history for predictive suggestions.
     */
    fun saveSearchQuery(query: String)

    /**
     * Clears all saved search history.
     */
    fun clearSearchHistory()

    /**
     * Resolves and parses timestamped lyrics for a specific track, locking to the exact audio source when known.
     * [durationMs] (when > 0) is used to verify candidate lyrics actually belong to the playing track.
     */
    suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        audioSource: AudioSourceType = AudioSourceType.UNKNOWN,
        audioSourceId: String? = null,
        durationMs: Long = 0L
    ): List<LyricLine>

    /**
     * Retrieves the audio source that was resolved for playback of a given track ID.
     */
    fun getResolvedAudioSource(trackId: String): ResolvedAudioSource?

    /**
     * Downloads an online streaming file, encrypts it on the fly, and updates local database state.
     * Emits download status and progress percentage.
     */
    fun downloadTrack(track: Track): Flow<DownloadStatus>

    /**
     * Resolves the direct streaming URL for online audio playback (without downloading to disk).
     */
    suspend fun getStreamingUrl(track: Track): String

    /**
     * Subscribes to database list of fully downloaded offline tracks.
     */
    fun getOfflineTracks(): Flow<List<Track>>

    /**
     * Subscribes to a single track entity to watch download or playback states.
     */
    fun getTrack(trackId: String): Flow<Track?>

    /**
     * Returns the user's preferred language, or null if not set.
     */
    fun getPreferredLanguage(): String?

    /**
     * Saves the user's preferred music language.
     */
    fun setPreferredLanguage(language: String)

    /**
     * Records a played track for listening history personalization.
     */
    fun recordListenedTrack(track: Track)

    /**
     * Returns recommendation queries based on listening history personalization.
     */
    fun getListeningHistoryQueries(): List<String>

    /**
     * Normalized "artist|title" keys for the most recently played tracks,
     * used to keep autoplay and suggestions from re-serving fresh listens.
     */
    fun getRecentlyPlayedKeys(): Set<String>

    /**
     * Subscribes to the list of liked tracks.
     */
    fun getLikedTracks(): Flow<List<Track>>

    /**
     * Toggles the favorite/liked status of a track.
     */
    suspend fun toggleLikeTrack(track: Track)

    /**
     * Deletes a downloaded track's encrypted file and updates its database state.
     */
    suspend fun deleteTrack(track: Track)

    /**
     * Fetches SponsorBlock skip segments for a YouTube Video ID.
     */
    suspend fun getSkipSegments(videoId: String): List<Pair<Long, Long>>

    /**
     * Checks if SponsorBlock auto-skipping is enabled.
     */
    fun isSponsorBlockEnabled(): Boolean

    /**
     * Sets the SponsorBlock preference state.
     */
    fun setSponsorBlockEnabled(enabled: Boolean)

    /**
     * Compiles listening statistics from the user's history logs.
     */
    fun getListeningStats(): ListeningStats
}

/**
 * Model summarizing user listening statistics for the Time Machine recap.
 */
data class ListeningStats(
    val totalSongs: Int,
    val topArtists: List<Pair<String, Int>>,
    val topSongs: List<Pair<String, Int>>
)

/**
 * States representing download task execution progress.
 */
sealed interface DownloadStatus {
    object Idle : DownloadStatus
    object Started : DownloadStatus
    data class Progress(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadStatus
    data class Success(val localPath: String, val iv: String) : DownloadStatus
    data class Error(val exception: Throwable) : DownloadStatus
}

/**
 * Model representing active download progress details.
 */
data class DownloadProgress(
    val trackId: String,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long
)
