package com.example.hunterxmusic.data.remote.model

import com.google.gson.annotations.SerializedName

// ──────────────────────────────────────────────────────────────
// Search endpoint: GET /search?query={query}
// ──────────────────────────────────────────────────────────────

data class SaavnSearchResponse(
    val status: Boolean,
    val results: List<SaavnSearchItem>?
)

data class SaavnSearchItem(
    val id: String,
    val title: String,
    val image: String?,
    val images: SaavnImages?,
    val album: String?,
    val description: String?,
    @SerializedName("more_info")
    val moreInfo: SaavnMoreInfo?,
    @SerializedName("api_url")
    val apiUrl: SaavnApiUrls?
)

data class SaavnImages(
    @SerializedName("50x50")
    val small: String?,
    @SerializedName("150x150")
    val medium: String?,
    @SerializedName("500x500")
    val large: String?
)

data class SaavnMoreInfo(
    val singers: String?,
    val language: String?,
    @SerializedName("album_id")
    val albumId: String?,
    val vlink: String?
)

data class SaavnApiUrls(
    val song: String?,
    val album: String?
)

// ──────────────────────────────────────────────────────────────
// Song detail endpoint: GET /song?id={id}
// ──────────────────────────────────────────────────────────────

data class SaavnSongDetailResponse(
    val status: Boolean,
    val id: String?,
    val song: String?,
    val album: String?,
    val year: Any?,  // API returns int or string
    @SerializedName("primary_artists")
    val primaryArtists: String?,
    val singers: String?,
    val image: String?,
    val images: SaavnImages?,
    val duration: String?,
    val label: String?,
    val language: String?,
    @SerializedName("has_lyrics")
    val hasLyrics: Boolean?,
    @SerializedName("media_url")
    val mediaUrl: String?,
    @SerializedName("media_urls")
    val mediaUrls: SaavnMediaUrls?,
    @SerializedName("release_date")
    val releaseDate: String?
)

data class SaavnMediaUrls(
    @SerializedName("96_KBPS")
    val low: String?,
    @SerializedName("160_KBPS")
    val medium: String?,
    @SerializedName("320_KBPS")
    val high: String?
)
