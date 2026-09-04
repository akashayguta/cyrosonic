package com.example.hunterxmusic.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class DownloadState {
    STREAMING,
    DOWNLOADING,
    COMPLETED_OFFLINE
}

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String?,
    val durationMs: Long,
    val onlineUrl: String?,
    val localFilePath: String?,
    val encryptionIv: String?,
    val downloadState: DownloadState,
    val isLiked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY timestamp DESC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE downloadState = 'COMPLETED_OFFLINE' ORDER BY timestamp DESC")
    fun getOfflineTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY timestamp DESC")
    fun getLikedTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchTracksFlow(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchLikedTracksFlow(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE downloadState = 'COMPLETED_OFFLINE' AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchOfflineTracksFlow(query: String): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET isLiked = :isLiked WHERE id = :id")
    suspend fun updateLikedStatus(id: String, isLiked: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Delete
    suspend fun deleteTrack(track: TrackEntity)
}
