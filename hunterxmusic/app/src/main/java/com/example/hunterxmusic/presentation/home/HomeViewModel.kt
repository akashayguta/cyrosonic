package com.example.hunterxmusic.presentation.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.domain.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class HomeTheme(
    val id: String,
    val name: String,
    val vibeTag: String,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val icon: String
)

val HOME_THEMES = listOf(
    HomeTheme("cyber_horizon", "CYBER HORIZON", "NEON DRIFT", Color(0xFF06B6D4), Color(0xFF8B5CF6), "⚡"),
    HomeTheme("cosmic_chill", "COSMIC CHILL", "LATE NIGHT ORBIT", Color(0xFF818CF8), Color(0xFF4F46E5), "🌌"),
    HomeTheme("high_voltage", "HIGH VOLTAGE", "EUPHORIA RUSH", Color(0xFFF59E0B), Color(0xFFEF4444), "🔥"),
    HomeTheme("retro_wave", "RETRO WAVE", "SYNTH AESTHETIC", Color(0xFFEC4899), Color(0xFF8B5CF6), "✨"),
    HomeTheme("aurora_emerald", "AURORA EMERALD", "DEEP FLOW", Color(0xFF10B981), Color(0xFF06B6D4), "🍃"),
    HomeTheme("obsidian_pulse", "OBSIDIAN PULSE", "PURE MIDNIGHT", Color(0xFF38BDF8), Color(0xFF0284C7), "🌙")
)

data class CountryInfo(
    val id: String,
    val name: String,
    val flag: String,
    val defaultSearchQuery: String,
    val secondaryQueries: List<String>
)

val SUPPORTED_COUNTRIES = listOf(
    CountryInfo("in", "India", "🇮🇳", "trending hindi bollywood songs", listOf("punjabi trending songs", "latest hindi hits", "arijit singh romantic")),
    CountryInfo("us", "United States", "🇺🇸", "billboard hot 100", listOf("top pop hits", "trending hip hop", "us viral 50")),
    CountryInfo("gb", "United Kingdom", "🇬🇧", "uk top 40 chart", listOf("british pop hits", "uk trending music", "uk drill and r&b")),
    CountryInfo("ca", "Canada", "🇨🇦", "canada top hits", listOf("canadian billboard", "toronto pop and rap", "canada viral 50")),
    CountryInfo("pk", "Pakistan", "🇵🇰", "coke studio pakistan", listOf("pakistani pop hits", "urdu romantic songs", "sufi trending")),
    CountryInfo("au", "Australia", "🇦🇺", "aria top 50 singles", listOf("australian pop hits", "aussie trending songs", "australia viral")),
    CountryInfo("de", "Germany", "🇩🇪", "top 50 germany", listOf("german pop hits", "deutschrap trending", "germany charts")),
    CountryInfo("fr", "France", "🇫🇷", "top 50 france", listOf("french pop hits", "rap francais", "france viral")),
    CountryInfo("es", "Spain", "🇪🇸", "top 50 espana", listOf("latin hits", "reggaeton trending", "spanish pop")),
    CountryInfo("kr", "South Korea", "🇰🇷", "kpop hot 100", listOf("melon top 50", "kpop viral hits", "newjeans bts blackpink")),
    CountryInfo("jp", "Japan", "🇯🇵", "billboard japan hot 100", listOf("jpop top hits", "anime opening songs", "japan viral")),
    CountryInfo("br", "Brazil", "🇧🇷", "top 50 brasil", listOf("funk brasil", "sertanejo hits", "brazilian pop")),
    CountryInfo("ae", "UAE & Middle East", "🇦🇪", "top arabic hits", listOf("khaleeji trending", "arabic pop", "dubai party hits")),
    CountryInfo("ng", "Nigeria", "🇳🇬", "afrobeats top hits", listOf("naija trending", "burna boy wizkid asake", "afropop")),
    CountryInfo("global", "Global / Worldwide", "🌍", "global top 50 viral", listOf("worldwide pop hits", "spotify global hits", "viral songs on tiktok"))
)

data class HomeState(
    val spotlightTrack: Track? = null,
    val quickPicks: List<Track> = emptyList(),
    val forYouRecommendations: List<Track> = emptyList(),
    val trending: List<Track> = emptyList(),
    val selectedCountry: CountryInfo = SUPPORTED_COUNTRIES.first(),
    val showCountryPicker: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,

    // Dynamic Theme (updated on refresh / first launch)
    val currentHomeTheme: HomeTheme = HOME_THEMES.first(),

    // Recently played (full snapshots from the v2 history table)
    val recentlyPlayed: List<Track> = emptyList(),

    // YouTube Music 3-Phase Speed Dial (9 tracks per page, 3 scrollable pages)
    val speedDialPhase1: List<Track> = emptyList(), // Phase 1: Most Listened
    val speedDialPhase2: List<Track> = emptyList(), // Phase 2: Recommended Picks
    val speedDialPhase3: List<Track> = emptyList(), // Phase 3: Related to Favorite Artist/Track
    val speedDialPhase3Title: String = "Related to Your Taste",

    // Speed Dial legacy compatibility
    val speedDial: List<Track> = emptyList(),
    val speedDialIsPersonalised: Boolean = false,

    // Personalized Analytics Engine Shelves
    val mostPlayed: List<Track> = emptyList(),
    val currentVibe: com.example.hunterxmusic.data.analytics.VibeInfo? = null,
    val topArtists: List<String> = emptyList(),

    val dailyMixes: List<List<Track>> = emptyList(),
    val weeklyRadar: List<Track> = emptyList(),

    // Country Specials section
    val countrySongs: List<Track> = emptyList(),
    val isCountrySongsLoading: Boolean = false,

    // Continue Listening — last tracks the listener chose to play, newest first
    val recentTracks: List<Track> = emptyList()
)

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val localLibraryManager: com.example.hunterxmusic.data.repository.LocalLibraryManager? = null,
    private val homeShelvesCache: com.example.hunterxmusic.data.local.HomeShelvesCache? = null,
    private val recentTracksStore: com.example.hunterxmusic.data.local.RecentTracksStore? = null,
    private val personalizationEngine: com.example.hunterxmusic.data.analytics.PersonalizationEngine? = null
) : ViewModel() {

    private val _state = mutableStateOf(HomeState())
    val state: State<HomeState> = _state

    private var refreshJob: Job? = null
    private var mixJob: Job? = null
    private var speedDialJob: Job? = null

    /**
     * Mix building costs ~8 network searches. It used to run inside the
     * recently-played collector, so it fired on EVERY song change — which
     * throttled the upstream API and made search, lyrics and stream
     * resolution start failing. It now runs on first load and on manual
     * refresh only, and never more often than [MIX_MIN_INTERVAL_MS].
     */
    private var lastMixBuildAt = 0L
    private var isDisposed = false
    private val sessionSeenTrackIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    companion object {
        private const val MIX_MIN_INTERVAL_MS = 15 * 60 * 1000L
        private const val SPEED_DIAL_SIZE = 27
    }

    init {
        initDashboard()

        // Live recently-played feed — updates the shelf the moment a song
        // starts. Deliberately cheap: state assignment plus one local Room
        // read, no network.
        viewModelScope.launch {
            localLibraryManager?.recentHistory?.collect { recent ->
                _state.value = _state.value.copy(
                    recentlyPlayed = recent.take(10),
                    recentTracks = recentTracksStore?.load().orEmpty()
                )
                rebuildSpeedDial()
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // SPEED DIAL
    // Cold start (no history): shuffled discovery picks so the grid is never
    // empty. Once there is real history it switches to a play-count × recency
    // ranking, so the tiles follow how this listener actually listens.
    // ──────────────────────────────────────────────────────────

    private fun rebuildSpeedDial() {
        if (speedDialJob?.isActive == true) return
        speedDialJob = viewModelScope.launch {
            val history = try {
                localLibraryManager?.fullHistory?.firstOrNull()
            } catch (_: Exception) { null }

            val mostPlayedTracks = personalizationEngine?.getMostPlayedTracks(18) ?: emptyList()
            val vibe = personalizationEngine?.getCurrentVibe()
            val artists = personalizationEngine?.getTopArtists(8) ?: emptyList()

            // Strictly exclude recently played tracks as requested, unless pool is depleted
            val excludedIds = (_state.value.recentlyPlayed.map { it.id } + _state.value.recentTracks.map { it.id }).toSet()

            // ─── PHASE 1: Most Listened (Cold-Start Random -> Adaptive Taste) ───
            val scoredHistory = buildPersonalisedSpeedDial(history)
            val mostListenedPool = (mostPlayedTracks + scoredHistory)
                .distinctBy { it.id }
                .filter { it.id !in excludedIds }

            val phase1 = if (mostListenedPool.size >= 9) {
                mostListenedPool.take(9)
            } else {
                // If user history has < 9 songs (cold start / new install), fill remainder
                // with vibrant, random trending & discovery hits so Speed Dial is never empty!
                val discoveryPool = (_state.value.trending + _state.value.quickPicks + _state.value.forYouRecommendations + _state.value.countrySongs)
                    .distinctBy { it.id }
                    .filter { candidate -> mostListenedPool.none { it.id == candidate.id } }
                    .shuffled()
                (mostListenedPool + discoveryPool).take(9)
            }
            val phase1Ids = phase1.map { it.id }.toSet()

            // ─── PHASE 2: Recommended & Fresh Discovery (9 tracks) ───
            val recPool = (_state.value.quickPicks + _state.value.forYouRecommendations + _state.value.trending + _state.value.countrySongs)
                .distinctBy { it.id }
                .filter { it.id !in phase1Ids }
                .shuffled()
            val phase2 = (recPool + _state.value.trending.shuffled()).distinctBy { it.id }.filter { it.id !in phase1Ids }.take(9)
            val phase2Ids = phase2.map { it.id }.toSet()

            // ─── PHASE 3: Related to Favorite Artist / Track (9 tracks) ───
            val topArtistCandidate = artists.firstOrNull { it.isNotBlank() }
                ?: phase1.firstOrNull()?.artist
                ?: _state.value.trending.firstOrNull()?.artist
                ?: "Global Hits"

            val phase3Title = if (topArtistCandidate != "Global Hits") "Related to $topArtistCandidate" else "Related to Your Taste"

            val relatedTracks = try {
                musicRepository.searchTracks("$topArtistCandidate best songs")
                    .distinctBy { it.id }
                    .filter { it.id !in phase1Ids && it.id !in phase2Ids }
                    .take(9)
            } catch (_: Exception) { emptyList() }

            val phase3 = if (relatedTracks.size >= 6) {
                relatedTracks.take(9)
            } else {
                (_state.value.countrySongs + _state.value.trending + _state.value.quickPicks)
                    .distinctBy { it.id }
                    .filter { it.id !in phase1Ids && it.id !in phase2Ids }
                    .shuffled()
                    .take(9)
            }

            _state.value = _state.value.copy(
                speedDialPhase1 = phase1,
                speedDialPhase2 = phase2,
                speedDialPhase3 = phase3,
                speedDialPhase3Title = phase3Title,
                speedDial = (phase1 + phase2 + phase3).take(27),
                speedDialIsPersonalised = mostListenedPool.isNotEmpty(),
                mostPlayed = mostPlayedTracks,
                currentVibe = vibe,
                topArtists = artists
            )
        }
    }

    /**
     * Score = play count weighted by how recently the song was last heard, so
     * something played 8 times last night outranks something played 12 times
     * two months ago.
     */
    private fun buildPersonalisedSpeedDial(
        history: List<com.example.hunterxmusic.data.repository.LocalLibraryManager.HistoryItem>?
    ): List<Track> {
        if (history.isNullOrEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val scored = LinkedHashMap<String, Pair<Track, Double>>()
        for (item in history) {
            val ageDays = ((now - item.playedAt).coerceAtLeast(0L)) / 86_400_000.0
            val recency = 1.0 / (1.0 + ageDays * 0.35)
            val key = item.track.id
            val existing = scored[key]
            val add = 1.0 + recency * 2.0
            scored[key] = if (existing == null) {
                item.track to add
            } else {
                existing.first to (existing.second + add)
            }
        }
        return scored.values.sortedByDescending { it.second }.map { it.first }
    }

    private fun initDashboard() {
        val savedLangOrCountry = musicRepository.getPreferredLanguage() ?: "in"

        val matchedCountry = SUPPORTED_COUNTRIES.firstOrNull {
            it.id.equals(savedLangOrCountry, ignoreCase = true) ||
            it.name.equals(savedLangOrCountry, ignoreCase = true)
        } ?: SUPPORTED_COUNTRIES.first()

        homeShelvesCache?.countryPickerPrompted = true

        // Instant first frame: restore the last session's shelves from local
        // storage so the home screen renders before any network call returns.
        // The background refresh below then swaps in fresh data as it lands.
        val cached = homeShelvesCache?.load()
        if (cached != null) {
            _state.value = _state.value.copy(
                selectedCountry = matchedCountry,
                spotlightTrack = cached.quickPicks.firstOrNull() ?: cached.trending.firstOrNull(),
                trending = cached.trending,
                forYouRecommendations = cached.forYou,
                quickPicks = cached.quickPicks,
                countrySongs = cached.countrySongs,
                recentTracks = recentTracksStore?.load().orEmpty(),
                isLoading = false,
                showCountryPicker = false
            )
        } else {
            _state.value = _state.value.copy(
                selectedCountry = matchedCountry,
                recentTracks = recentTracksStore?.load().orEmpty(),
                isLoading = false,
                showCountryPicker = false
            )
        }

        loadContent(matchedCountry, showLoading = cached == null)
        startAutoRefresh()
    }

    /** Pull-to-refresh entry point: reloads every shelf and forces new mixes & new dynamic visual vibe theme. */
    fun refresh() {
        val current = _state.value.currentHomeTheme
        val nextTheme = HOME_THEMES.filter { it.id != current.id }.randomOrNull() ?: current
        _state.value = _state.value.copy(currentHomeTheme = nextTheme)
        loadContent(_state.value.selectedCountry, isPullToRefresh = true)
    }

    fun onCountrySelected(country: CountryInfo) {
        musicRepository.setPreferredLanguage(country.id)
        _state.value = _state.value.copy(
            showCountryPicker = false,
            selectedCountry = country,
            isLoading = true
        )
        loadContent(country)
        startAutoRefresh()
    }

    fun openCountryPicker() {
        _state.value = _state.value.copy(showCountryPicker = true)
    }

    fun dismissCountryPicker() {
        _state.value = _state.value.copy(showCountryPicker = false)
        val currentCountry = _state.value.selectedCountry
        if (_state.value.trending.isEmpty() && _state.value.quickPicks.isEmpty()) {
            val fallback = currentCountry ?: SUPPORTED_COUNTRIES.first()
            _state.value = _state.value.copy(selectedCountry = fallback, isLoading = true)
            loadContent(fallback)
            startAutoRefresh()
        }
    }

    private fun loadContent(country: CountryInfo, isPullToRefresh: Boolean = false, showLoading: Boolean = true) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = !isPullToRefresh && showLoading,
                isRefreshing = isPullToRefresh,
                errorMessage = null
            )

            try {
                // Strict max 3s timeout for pull-to-refresh so user never experiences long spin
                kotlinx.coroutines.withTimeoutOrNull(if (isPullToRefresh) 3000L else 12000L) {
                    val isGlobal = country.id.equals("gl", ignoreCase = true) || country.name.contains("global", ignoreCase = true)
                    val dynamicTrendingPool = if (isGlobal) {
                        listOf(
                            "viral 50 global music charts",
                            "popular trending songs 2026",
                            "trending music breakout discovery",
                            "global top hits 2026",
                            "billboard hot 100 global"
                        ) + country.secondaryQueries
                    } else {
                        listOf(
                            "${country.name} top charts viral songs",
                            "${country.name} latest hits 2026",
                            "${country.name} new party releases",
                            "${country.name} top acoustic vibe",
                            "${country.name} popular trending"
                        ) + country.secondaryQueries + listOf(country.defaultSearchQuery)
                    }
                    val selectedTrendingQuery = if (isPullToRefresh) dynamicTrendingPool.random() else country.defaultSearchQuery

                    // All sections load in PARALLEL and update the UI progressively.
                    coroutineScope {
                        val trendingDeferred = async {
                            try {
                                val results = musicRepository.getTrendingSongs(selectedTrendingQuery)
                                val candidate = if (isPullToRefresh) {
                                    val fresh = results.filter { it.id !in sessionSeenTrackIds }
                                    if (fresh.size >= 8) fresh.shuffled().take(20) else results.shuffled().take(20)
                                } else results
                                candidate
                            } catch (_: Exception) { emptyList() }
                        }
                        val historyDeferred = async {
                            try { musicRepository.getListeningHistoryQueries() } catch (_: Exception) { emptyList() }
                        }

                        val trending = trendingDeferred.await()
                        trending.forEach { sessionSeenTrackIds.add(it.id) }

                        val historyQueries = historyDeferred.await()
                        val seenIds = trending.map { it.id }.toMutableSet()
                        if (isPullToRefresh) {
                            seenIds.addAll(sessionSeenTrackIds)
                        }
                        val recentlyPlayedKeys = try { musicRepository.getRecentlyPlayedKeys() } catch (_: Exception) { emptySet() }

                        val forYouDeferred = async {
                            val picks = mutableListOf<Track>()
                            val queriesPool = if (historyQueries.isNotEmpty()) {
                                (historyQueries.shuffled() + country.secondaryQueries.shuffled()).distinct()
                            } else country.secondaryQueries.shuffled()

                            for (q in queriesPool.take(4)) {
                                val results = try { musicRepository.searchTracks(q) } catch (_: Exception) { emptyList() }
                                for (t in results.shuffled()) {
                                    val heardKey = "${t.artist.trim().lowercase()}|${t.title.trim().lowercase()}"
                                    val stale = t.id in seenIds || heardKey in recentlyPlayedKeys
                                    if (!stale && picks.size < 14) {
                                        seenIds.add(t.id)
                                        picks.add(t)
                                        sessionSeenTrackIds.add(t.id)
                                    }
                                }
                            }
                            picks
                        }

                        val forYou = forYouDeferred.await()
                        val quickPicks = buildQuickPicks(seenIds, country, isPullToRefresh)
                        quickPicks.forEach { sessionSeenTrackIds.add(it.id) }

                        // Deduplicate Hero Spotlight so it is NEVER dual-shown in trending or quickPicks list!
                        val prevSpotlightId = _state.value.spotlightTrack?.id
                        val candidatePool = (quickPicks + forYou + trending)
                        val freshCandidates = candidatePool.filter { it.id != prevSpotlightId }
                        val chosenSpotlight = (if (isPullToRefresh) freshCandidates.shuffled() else candidatePool).firstOrNull()
                            ?: _state.value.spotlightTrack
                            ?: trending.firstOrNull()

                        val cleanTrending = if (chosenSpotlight != null) trending.filter { it.id != chosenSpotlight.id } else trending
                        val cleanQuickPicks = if (chosenSpotlight != null) quickPicks.filter { it.id != chosenSpotlight.id } else quickPicks

                        _state.value = _state.value.copy(
                            spotlightTrack = chosenSpotlight,
                            trending = cleanTrending,
                            forYouRecommendations = forYou,
                            quickPicks = cleanQuickPicks
                        )
                    }

                    if (sessionSeenTrackIds.size > 250) {
                        val toDrop = sessionSeenTrackIds.take(100)
                        sessionSeenTrackIds.removeAll(toDrop.toSet())
                    }

                    persistShelves()
                    loadCountrySongs(country, randomize = isPullToRefresh)
                    rebuildSpeedDial()
                    maybeRefreshMixes(force = isPullToRefresh)
                }

                val s = _state.value
                if (s.trending.isEmpty() && s.quickPicks.isEmpty() && s.forYouRecommendations.isEmpty()) {
                    _state.value = _state.value.copy(
                        errorMessage = "Couldn't reach the music service. Check your connection and pull down to retry."
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Something went wrong loading your music. Pull down to retry."
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    private suspend fun buildQuickPicks(seenIds: MutableSet<String>, country: CountryInfo, randomize: Boolean = false): List<Track> {
        val picks = mutableListOf<Track>()
        val queries = if (randomize) country.secondaryQueries.shuffled() else country.secondaryQueries
        for (query in queries) {
            val results = try { musicRepository.searchTracks(query) } catch (_: Exception) { emptyList() }
            val pool = if (randomize) results.shuffled() else results
            for (t in pool) {
                if (t.id !in seenIds && picks.size < 10) {
                    seenIds.add(t.id)
                    picks.add(t)
                }
            }
        }
        return picks
    }

    /** Snapshots the current shelves to local storage for instant cold starts. */
    private fun persistShelves() {
        val s = _state.value
        if (s.trending.isEmpty() && s.quickPicks.isEmpty() && s.forYouRecommendations.isEmpty()) return
        homeShelvesCache?.save(
            com.example.hunterxmusic.data.local.HomeShelvesCache.CachedShelves(
                trending = s.trending,
                forYou = s.forYouRecommendations,
                quickPicks = s.quickPicks,
                countrySongs = s.countrySongs
            )
        )
    }

    fun loadCountrySongs(country: CountryInfo, randomize: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCountrySongsLoading = true)
            try {
                val query = if (randomize) country.secondaryQueries.random() else country.defaultSearchQuery
                val results = musicRepository.searchTracks(query)
                val finalTracks = if (randomize) results.shuffled() else results
                _state.value = _state.value.copy(countrySongs = finalTracks.take(15))
                persistShelves()
            } catch (_: Exception) {
                // Keep whatever is already on screen; the section just doesn't grow.
            } finally {
                _state.value = _state.value.copy(isCountrySongsLoading = false)
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // DAILY MIXES + WEEKLY RADAR
    // Clustered around the listener's most-played artists. Expensive, so
    // rate-limited rather than run on every track change.
    // ──────────────────────────────────────────────────────────

    private fun maybeRefreshMixes(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastMixBuildAt < MIX_MIN_INTERVAL_MS) return
        if (mixJob?.isActive == true) return
        lastMixBuildAt = now

        mixJob = viewModelScope.launch {
            try {
                val s = _state.value
                val artistCounts = LinkedHashMap<String, Int>()
                (s.recentlyPlayed + s.forYouRecommendations + s.quickPicks)
                    .mapNotNull { t -> t.artist.split(",", "&").firstOrNull()?.trim()?.takeIf { it.length > 1 } }
                    .forEach { name -> artistCounts[name] = (artistCounts[name] ?: 0) + 1 }
                val topArtists = artistCounts.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    .take(3)
                if (topArtists.isEmpty()) return@launch

                val mixes = coroutineScope {
                    topArtists.mapIndexed { index, artist ->
                        async {
                            try {
                                val query = when (index) {
                                    0 -> "$artist hits"
                                    1 -> "$artist songs mix"
                                    else -> "$artist best tracks"
                                }
                                musicRepository.searchTracks(query).distinctBy { it.id }.take(20)
                            } catch (_: Exception) { emptyList() }
                        }
                    }.mapNotNull { d -> d.await().takeIf { it.isNotEmpty() } }
                }
                if (mixes.isNotEmpty()) {
                    _state.value = _state.value.copy(dailyMixes = mixes)
                }

                val radar = try {
                    musicRepository.searchTracks("new ${topArtists[0]} song 2026")
                        .distinctBy { it.id }
                        .take(15)
                } catch (_: Exception) { emptyList() }
                if (radar.isNotEmpty()) {
                    _state.value = _state.value.copy(weeklyRadar = radar)
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * Single-shot freshness refresh 5 minutes after load. The old version
     * looped forever (while(true)) with no lifecycle awareness, hammering the
     * network in the background for the whole app session. One refresh keeps
     * trending fresh without the perpetual polling; pull-to-refresh and
     * country changes re-arm it.
     */
    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(300_000) // 5 minutes
            // Guarded: an unguarded throw here used to kill the refresh
            // loop permanently for the rest of the session.
            try {
                val country = _state.value.selectedCountry
                val trending = musicRepository.getTrendingSongs(country.defaultSearchQuery)
                if (trending.isNotEmpty()) {
                    _state.value = _state.value.copy(trending = trending)
                }
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        mixJob?.cancel()
        speedDialJob?.cancel()
    }
}
