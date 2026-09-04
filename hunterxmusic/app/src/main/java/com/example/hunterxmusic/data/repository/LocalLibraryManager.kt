package com.example.hunterxmusic.data.repository

import com.example.hunterxmusic.data.local.db.HistoryDao
import com.example.hunterxmusic.data.local.db.HistoryEntryEntity
import com.example.hunterxmusic.data.local.db.PlaylistDao
import com.example.hunterxmusic.data.local.db.PlaylistEntity
import com.example.hunterxmusic.data.local.db.PlaylistTrackEntity
import com.example.hunterxmusic.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local library layer: user playlists + recently-played history.
 * Kept separate from [MusicRepository] so the remote-catalog interface
 * stays lean.
 */
class LocalLibraryManager(
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao,
    // App-lifetime scope injected once from the Application class. The old
    // GlobalScope launches were unmanageable and leaked across tests.
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    // ── Playlists ────────────────────────────────────────────────

    val playlists: Flow<List<PlaylistEntity>> = playlistDao.playlistsFlow()

    fun playlistTracks(playlistId: Long): Flow<List<Track>> {
        return playlistDao.playlistTracksFlow(playlistId).map { rows ->
            rows.map { it.toTrack() }
        }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name.trim().ifBlank { "New Playlist" }))
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        playlistDao.playlistById(playlistId)?.let {
            playlistDao.updatePlaylist(it.copy(name = newName.trim().ifBlank { it.name }))
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: Track) = withContext(Dispatchers.IO) {
        val nextPos = (playlistDao.maxPosition(playlistId) ?: -1) + 1
        playlistDao.insertPlaylistTracks(listOf(track.toPlaylistTrack(playlistId, nextPos)))
    }

    suspend fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>) = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext
        var pos = (playlistDao.maxPosition(playlistId) ?: -1) + 1
        // IGNORE conflict strategy skips tracks already in the playlist
        playlistDao.insertPlaylistTracks(tracks.map { it.toPlaylistTrack(playlistId, pos++) })
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) = withContext(Dispatchers.IO) {
        playlistDao.removePlaylistTrack(playlistId, trackId)
    }

    private fun Track.toPlaylistTrack(playlistId: Long, position: Int) = PlaylistTrackEntity(
        playlistId = playlistId,
        trackId = id,
        position = position,
        title = title,
        artist = artist,
        album = album,
        albumArtUrl = albumArtUrl,
        durationMs = durationMs,
        streamingUrl = streamingUrl
    )

    // ── Recently played ──────────────────────────────────────────

    /** A history entry projected into a playable track plus its last-play time. */
    data class HistoryItem(val track: Track, val playedAt: Long)

    private fun HistoryEntryEntity.toHistoryItem(): HistoryItem = HistoryItem(
        track = Track(
            id = trackId,
            title = title,
            artist = artist,
            album = "",
            albumArtUrl = albumArtUrl,
            durationMs = durationMs,
            streamingUrl = null,
            localFilePath = null,
            isDownloaded = false,
            encryptionIv = null,
            isLiked = false
        ),
        playedAt = playedAt
    )

    val recentHistory: Flow<List<Track>> = historyDao.recentFlow().map { rows ->
        rows.map { it.toHistoryItem().track }
    }

    /** Every distinct song ever played, newest first — feeds the History tab. */
    val fullHistory: Flow<List<HistoryItem>> = historyDao.fullHistoryFlow().map { rows ->
        rows.map { it.toHistoryItem() }
    }

    val historyCount: Flow<Int> = historyDao.distinctCountFlow()

    fun clearHistory() {
        appScope.launch {
            try { historyDao.clear() } catch (_: Exception) { }
        }
    }

    fun recordHistory(track: Track) {
        // Fire-and-forget on a background dispatcher; caller stays non-blocking
        appScope.launch {
            try {
                historyDao.insert(
                    HistoryEntryEntity(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        albumArtUrl = track.albumArtUrl,
                        durationMs = track.durationMs
                    )
                )
                historyDao.trim()
            } catch (_: Exception) { }
        }
    }
}
