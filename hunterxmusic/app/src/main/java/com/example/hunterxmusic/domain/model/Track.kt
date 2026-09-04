package com.example.hunterxmusic.domain.model

/**
 * Domain model representing a music track, independent of storage or network implementations.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtUrl: String? = null,
    val durationMs: Long = 0L,
    val streamingUrl: String? = null,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val encryptionIv: String? = null,
    val isLiked: Boolean = false
)
