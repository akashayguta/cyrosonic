package com.example.hunterxmusic.data.remote

import com.example.hunterxmusic.data.remote.model.AiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface AiApiService {
    @GET("ai/gpt-5")
    suspend fun getAiResponse(
        @Query("text") text: String,
        @Query("unlocked") unlocked: Boolean
    ): AiResponse
}
