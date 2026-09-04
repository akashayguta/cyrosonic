package com.example.hunterxmusic.data.remote

import com.example.hunterxmusic.data.remote.model.SaavnSearchResponse
import com.example.hunterxmusic.data.remote.model.SaavnSongDetailResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Internal music catalog service powering CyroSonic global library.
 * Provides search and song detail resolution with direct streaming URLs.
 */
interface MusicCatalogService {

    @GET("search")
    suspend fun searchSongs(
        @Query("query") query: String
    ): SaavnSearchResponse

    @GET("song")
    suspend fun getSongDetail(
        @Query("id") songId: String
    ): SaavnSongDetailResponse
}
