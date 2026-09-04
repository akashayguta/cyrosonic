package com.example.hunterxmusic.data.remote

import com.google.gson.annotations.SerializedName

// ──────────────────────────────────────────────────────────────
// Echo-style JioSaavn API response models (saavn.echomusic.fun)
// ──────────────────────────────────────────────────────────────

// Search: GET /api/search/songs?query={q}
data class EchoSaavnSearchResponse(
    val success: Boolean = false,
    val data: EchoSaavnSearchData? = null
)

data class EchoSaavnSearchData(
    val total: Int = 0,
    val results: List<EchoSaavnSong> = emptyList()
)

// Song detail: GET /api/songs/{id}
data class EchoSaavnSongResponse(
    val success: Boolean = false,
    val data: List<EchoSaavnSong> = emptyList()
)

data class EchoSaavnSong(
    val id: String = "",
    val name: String = "",
    val duration: Int? = null,
    val artists: EchoSaavnArtists = EchoSaavnArtists(),
    val image: List<EchoSaavnQualityUrl> = emptyList(),
    val downloadUrl: List<EchoSaavnQualityUrl> = emptyList()
)

data class EchoSaavnArtists(
    val primary: List<EchoSaavnArtistItem> = emptyList(),
    val featured: List<EchoSaavnArtistItem> = emptyList(),
    val all: List<EchoSaavnArtistItem> = emptyList()
)

data class EchoSaavnArtistItem(
    val id: String = "",
    val name: String = ""
)

data class EchoSaavnQualityUrl(
    val quality: String = "",
    val url: String = ""
)
