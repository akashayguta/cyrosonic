package com.example.hunterxmusic.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * User-created playlists. Tracks are stored as full snapshots so playlists
 * survive independently of the tracks cache table.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    // Track snapshot — playlist rows render offline without lookups
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String?,
    val durationMs: Long,
    val streamingUrl: String?
) {
    fun toTrack(): com.example.hunterxmusic.domain.model.Track {
        return com.example.hunterxmusic.domain.model.Track(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            albumArtUrl = albumArtUrl,
            durationMs = durationMs,
            streamingUrl = streamingUrl,
            localFilePath = null,
            isDownloaded = false,
            encryptionIv = null,
            isLiked = false
        )
    }
}

/**
 * Recently-played log with full track snapshots — powers the "Recently
 * Played" shelf and richer stats while keeping only the last 50 plays.
 */
@Entity(tableName = "listen_history_v2", indices = [Index("playedAt")])
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val trackId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val durationMs: Long,
    val playedAt: Long = System.currentTimeMillis()
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlistsFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlistById(id: Long): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun trackCount(playlistId: Long): Int

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun playlistTracksFlow(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removePlaylistTrack(playlistId: Long, trackId: String)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int?
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntryEntity)

    // Deduped by trackId via SQLite's bare-column/MAX guarantee: with an
    // aggregate present, non-aggregated columns come from the row that
    // produced the MAX — so each song appears once at its latest play time.
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT *, MAX(playedAt) FROM listen_history_v2 GROUP BY trackId ORDER BY playedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int = 50): Flow<List<HistoryEntryEntity>>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT *, MAX(playedAt) FROM listen_history_v2 GROUP BY trackId ORDER BY playedAt DESC LIMIT :limit")
    fun fullHistoryFlow(limit: Int = 300): Flow<List<HistoryEntryEntity>>

    @Query("SELECT COUNT(DISTINCT trackId) FROM listen_history_v2")
    fun distinctCountFlow(): Flow<Int>

    @Query("DELETE FROM listen_history_v2 WHERE rowId NOT IN (SELECT rowId FROM listen_history_v2 ORDER BY playedAt DESC LIMIT 300)")
    suspend fun trim()

    @Query("DELETE FROM listen_history_v2")
    suspend fun clear()
}
