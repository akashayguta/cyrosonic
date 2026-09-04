package com.example.hunterxmusic.presentation.explore

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.domain.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class ExploreFilter(
    val id: String,
    val label: String,
    val iconEmoji: String
)

val EXPLORE_FILTERS = listOf(
    ExploreFilter("ALL", "All Explore", "✨"),
    ExploreFilter("VIRAL", "Viral Radar", "🔥"),
    ExploreFilter("VIBES", "Vibe Matrix", "🌌"),
    ExploreFilter("DECADES", "Time Machine", "⏳"),
    ExploreFilter("WORLD", "World Radar", "🌍"),
    ExploreFilter("CYBER", "Cyber & Bass", "⚡")
)

data class VibeBentoCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val query: String,
    val emoji: String,
    val tag: String,
    val gradientColors: List<Long>,
    val moodKey: String? = null
)

val VIBE_BENTO_ITEMS = listOf(
    VibeBentoCard(
        id = "cyber_phonk",
        title = "Cyberpunk & Phonk",
        subtitle = "Drift, Retrowave & 808 Bass",
        query = "cyberpunk synthwave phonk drift music 2026",
        emoji = "⚡",
        tag = "160 BPM",
        gradientColors = listOf(0xFF581C87, 0xFF0284C7),
        moodKey = "workout"
    ),
    VibeBentoCard(
        id = "lofi_rain",
        title = "Lo-Fi Midnight Rain",
        subtitle = "Chillhop, Binaural & Rain Drops",
        query = "lofi hip hop beats study chill rain",
        emoji = "☕",
        tag = "DEEP FOCUS",
        gradientColors = listOf(0xFF1E1B4B, 0xFF4338CA),
        moodKey = "chill"
    ),
    VibeBentoCard(
        id = "beast_mode",
        title = "Adrenaline Overload",
        subtitle = "Heavy Trap, Hardstyle & PR Energy",
        query = "workout gym motivation trap hardstyle",
        emoji = "🔥",
        tag = "BEAST MODE",
        gradientColors = listOf(0xFF991B1B, 0xFFEA580C),
        moodKey = "workout"
    ),
    VibeBentoCard(
        id = "acoustic_sanctuary",
        title = "Acoustic Sanctuary",
        subtitle = "Unplugged Strings, Soul & Coffee",
        query = "acoustic guitar folk mellow chill unplugged",
        emoji = "🍂",
        tag = "WARM VIBE",
        gradientColors = listOf(0xFF78350F, 0xFFD97706),
        moodKey = "peaceful"
    ),
    VibeBentoCard(
        id = "zen_flow",
        title = "Zen Space & Meditation",
        subtitle = "Healing 432Hz, Solfeggio & Calm",
        query = "432 hz meditation ambient healing sleep",
        emoji = "🌿",
        tag = "432 HZ",
        gradientColors = listOf(0xFF064E3B, 0xFF059669),
        moodKey = "peaceful"
    ),
    VibeBentoCard(
        id = "club_euphoria",
        title = "Club Euphoria",
        subtitle = "Festival Anthems, Bass Drops & House",
        query = "festival edm dance electro house party hits",
        emoji = "🪩",
        tag = "PARTY",
        gradientColors = listOf(0xFF701A75, 0xFFDB2777),
        moodKey = "party"
    )
)

data class WorldRadarItem(
    val code: String,
    val name: String,
    val flag: String,
    val genre: String,
    val query: String,
    val accentHex: Long
)

val WORLD_RADAR_ITEMS = listOf(
    WorldRadarItem("IN", "India", "🇮🇳", "Bollywood & Desi Drill", "trending hindi punjabi songs 2026", 0xFFFF9933),
    WorldRadarItem("US", "United States", "🇺🇸", "Hot 100 & Trap", "billboard hot 100 top hits", 0xFF3B82F6),
    WorldRadarItem("GB", "United Kingdom", "🇬🇧", "UK Drill & Garage", "uk drill grime garage hits", 0xFF6366F1),
    WorldRadarItem("KR", "South Korea", "🇰🇷", "K-Pop Global Horizon", "kpop top hits viral 2026", 0xFFEC4899),
    WorldRadarItem("JP", "Japan", "🇯🇵", "City Pop & J-Rock", "japanese city pop jrock anime hits", 0xFFA855F7),
    WorldRadarItem("BR", "Brazil", "🇧🇷", "Brazilian Phonk & Funk", "brazilian phonk funk mandelao viral", 0xFF10B981),
    WorldRadarItem("NG", "Africa", "🇳🇬", "Afrobeats & Amapiano", "afrobeats amapiano top viral hits", 0xFFF59E0B),
    WorldRadarItem("ES", "Latin America", "💃", "Reggaeton & Dembow", "top reggaeton latin urbano hits 2026", 0xFFEF4444)
)

data class DecadesItem(
    val era: String,
    val name: String,
    val description: String,
    val yearSpan: String,
    val query: String,
    val emoji: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long
)

val DECADES_TIME_MACHINE = listOf(
    DecadesItem("70s", "Funk & Classic Rock", "Queen, Led Zeppelin, ABBA, Pink Floyd", "1970–1979", "best 70s rock disco hits", "📻", 0xFFB45309, 0xFFF59E0B),
    DecadesItem("80s", "Neon Synth & City Pop", "Michael Jackson, Madonna, Retro Synth", "1980–1989", "best 80s synthpop retro hits", "📼", 0xFF831843, 0xFFEC4899),
    DecadesItem("90s", "Golden Hip-Hop & Grunge", "Nirvana, 2Pac, Biggie, Eurodance", "1990–1999", "best 90s hip hop grunge hits", "💿", 0xFF4C1D95, 0xFF8B5CF6),
    DecadesItem("2000s", "Y2K Pop & Nu-Metal", "Linkin Park, Eminem, Britney, Green Day", "2000–2009", "best 2000s y2k pop rock hits", "🎧", 0xFF0E7490, 0xFF06B6D4),
    DecadesItem("2010s", "EDM Boom & Modern Pop", "Avicii, The Weeknd, Drake, Calvin Harris", "2010–2019", "best 2010s edm pop hits", "📱", 0xFF065F46, 0xFF10B981),
    DecadesItem("2026", "Future Wave & TikTok", "Hyperpop, Viral Hits & Future Anthems", "2020–Future", "viral trending songs 2026", "⚡", 0xFF0369A1, 0xFF38BDF8)
)

data class BrowseTile(
    val key: String,
    val label: String,
    val query: String,
    val emoji: String
)

val EXPLORE_LANGUAGES = listOf(
    BrowseTile("hi", "Hindi", "latest hindi songs", "🇮🇳"),
    BrowseTile("pa", "Punjabi", "punjabi trending songs", "🎧"),
    BrowseTile("en", "English", "top english hits", "🌍"),
    BrowseTile("ta", "Tamil", "tamil hit songs", "🎬"),
    BrowseTile("te", "Telugu", "telugu hit songs", "🎼"),
    BrowseTile("ko", "Korean", "kpop top hits", "🇰🇷"),
    BrowseTile("es", "Spanish", "latin reggaeton hits", "💃"),
    BrowseTile("ar", "Arabic", "top arabic hits", "🕌")
)

val EXPLORE_DECADES = listOf(
    BrowseTile("d90", "90s", "best 90s songs hits", "📻"),
    BrowseTile("d00", "2000s", "best 2000s hits", "💿"),
    BrowseTile("d10", "2010s", "best 2010s hits", "📱"),
    BrowseTile("d20", "2020s", "best 2020s hits", "⚡")
)

data class ExploreState(
    val spotlightTracks: List<Track> = emptyList(),
    val newReleases: List<Track> = emptyList(),
    val charts: List<Track> = emptyList(),
    val viralRadar: List<Track> = emptyList(),
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val activeFilter: String = "ALL",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Explore - The high-octane discovery hub for HunterXMusic.
 * Featuring Spotlight Sonic Radar, Vibe Matrix, Decades Time Machine,
 * World Sound Radar, and Ranked Charts.
 */
class ExploreViewModel(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _state = mutableStateOf(ExploreState())
    val state: State<ExploreState> = _state

    private var loadJob: Job? = null
    private var hasLoaded = false

    fun selectFilter(filter: String) {
        _state.value = _state.value.copy(activeFilter = filter)
    }

    /** Loads once per process unless [force] is set (pull-to-refresh). */
    fun load(force: Boolean = false) {
        if (hasLoaded && !force) return
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = !hasLoaded,
                isRefreshing = hasLoaded && force,
                errorMessage = null
            )
            try {
                coroutineScope {
                    val spotlightDeferred = async {
                        try { musicRepository.getTrendingSongs("global") }
                        catch (_: Exception) { emptyList() }
                    }
                    val releasesDeferred = async {
                        try { musicRepository.searchTracks("latest hit music releases 2026") }
                        catch (_: Exception) { emptyList() }
                    }
                    val chartsDeferred = async {
                        try { musicRepository.getTrendingSongs("global top 50 chart") }
                        catch (_: Exception) { emptyList() }
                    }
                    val viralDeferred = async {
                        try { musicRepository.searchTracks("viral phonk synthwave underground hits") }
                        catch (_: Exception) { emptyList() }
                    }
                    val statsDeferred = async {
                        try { musicRepository.getListeningStats().topArtists }
                        catch (_: Exception) { emptyList() }
                    }

                    val rawSpotlights = spotlightDeferred.await()
                    val spotlightCleaned = rawSpotlights.filter { !it.albumArtUrl.isNullOrBlank() }.take(6)
                    val newReleases = releasesDeferred.await().take(20)
                    val charts = chartsDeferred.await().take(20)
                    val viral = viralDeferred.await().take(20)
                    val topArtists = statsDeferred.await().take(10)

                    _state.value = _state.value.copy(
                        spotlightTracks = if (spotlightCleaned.isNotEmpty()) spotlightCleaned else rawSpotlights.take(6),
                        newReleases = newReleases,
                        charts = charts,
                        viralRadar = viral,
                        topArtists = topArtists
                    )
                }
                hasLoaded = true

                val s = _state.value
                if (s.spotlightTracks.isEmpty() && s.newReleases.isEmpty() && s.charts.isEmpty()) {
                    _state.value = _state.value.copy(
                        errorMessage = "Couldn't load Explore. Check your connection and pull down to retry."
                    )
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Couldn't load Explore. Pull down to retry."
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    fun refresh() = load(force = true)

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }
}
