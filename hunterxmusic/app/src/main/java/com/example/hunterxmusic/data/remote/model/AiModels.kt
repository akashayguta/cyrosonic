package com.example.hunterxmusic.data.remote.model

data class AiResponse(
    val status: Boolean,
    val statusCode: Int,
    val creator: String,
    val model: String,
    val text: String,
    val note: String?
)
