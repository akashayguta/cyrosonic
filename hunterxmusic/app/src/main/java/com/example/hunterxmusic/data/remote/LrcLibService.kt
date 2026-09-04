package com.example.hunterxmusic.data.remote

import com.example.hunterxmusic.data.remote.model.LrcResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * LRCLIB — the free, no-auth, community lyrics database. This is the app's
 * primary source of time-synced (karaoke) lyrics.
 *
 * https://lrclib.net/api/search?q=starboy
 */
interface LrcLibService {

    /** Exact lookup. 404s when there is no precise metadata match. */
    @GET("api/get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String,
        @Query("album_name") album: String?,
        @Query("duration") duration: Double?
    ): LrcResponse

    /** Free-text search — returns many candidates to score and choose from. */
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrcResponse>

    /**
     * Field-scoped search. Far more accurate than the free-text [searchLyrics]
     * because the title and artist are matched separately instead of being
     * mashed into one blob.
     */
    @GET("api/search")
    suspend fun searchLyricsByFields(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String?
    ): List<LrcResponse>
}
