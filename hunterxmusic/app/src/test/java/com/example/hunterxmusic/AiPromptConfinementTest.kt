package com.example.hunterxmusic

import com.example.hunterxmusic.data.remote.AiApiService
import com.example.hunterxmusic.data.remote.model.AiResponse
import com.example.hunterxmusic.data.repository.AiRepositoryImpl
import com.example.hunterxmusic.domain.model.LyricLine
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.domain.repository.AiChatStatus
import com.example.hunterxmusic.domain.repository.DownloadProgress
import com.example.hunterxmusic.domain.repository.DownloadStatus
import com.example.hunterxmusic.domain.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Unit tests verifying the assistant's prompt bounds, offline-error masking,
 * and chat-history cache persistence. The old "double password locks,
 * one-strike lockout" gate was removed from the product — the chat is always
 * open — so those scenarios are gone with it.
 */
class AiPromptConfinementTest {

    private lateinit var tempTestDir: File

    @Before
    fun setUp() {
        tempTestDir = File(System.getProperty("java.io.tmpdir"), "hunterx_test_${System.nanoTime()}")
        tempTestDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempTestDir.deleteRecursively()
    }

    private class FakeAiApiService : AiApiService {
        var lastSentText: String? = null
        var lastUnlockedVal: Boolean? = null
        var throwError = false
        
        override suspend fun getAiResponse(text: String, unlocked: Boolean): AiResponse {
            lastSentText = text
            lastUnlockedVal = unlocked
            if (throwError) throw IOException("Simulated connection drop")
            return AiResponse(
                status = true,
                statusCode = 200,
                creator = "prexzy",
                model = "gpt-5",
                text = "Processed: $text",
                note = null
            )
        }
    }

    private class FakeMusicRepository : MusicRepository {
        override val activeDownloads: StateFlow<Map<String, DownloadProgress>> =
            MutableStateFlow(emptyMap())

        override suspend fun searchTracks(query: String): List<Track> = emptyList()
        override suspend fun searchTracksPaginated(query: String): Pair<List<Track>, String?> =
            Pair(emptyList(), null)

        override suspend fun searchTracksNextPage(continuationToken: String): Pair<List<Track>, String?> =
            Pair(emptyList(), null)

        override suspend fun getTrendingSongs(language: String): List<Track> = emptyList()
        override fun getSearchSuggestions(partialQuery: String): List<String> = emptyList()
        override fun saveSearchQuery(query: String) = Unit
        override fun clearSearchHistory() = Unit
        override suspend fun getLyrics(
            trackId: String,
            title: String,
            artist: String,
            audioSource: com.example.hunterxmusic.domain.repository.AudioSourceType,
            audioSourceId: String?,
            durationMs: Long
        ): List<LyricLine> = emptyList()

        override fun getResolvedAudioSource(trackId: String): com.example.hunterxmusic.domain.repository.ResolvedAudioSource? = null

        override fun downloadTrack(track: Track): Flow<DownloadStatus> = emptyFlow()
        override suspend fun getStreamingUrl(track: Track): String = track.streamingUrl.orEmpty()
        override fun getOfflineTracks(): Flow<List<Track>> = flowOf(emptyList())
        override fun getTrack(trackId: String): Flow<Track?> = flowOf(null)
        override fun getPreferredLanguage(): String? = null
        override fun setPreferredLanguage(language: String) = Unit
        override fun recordListenedTrack(track: Track) = Unit
        override fun getListeningHistoryQueries(): List<String> = emptyList()
        override fun getRecentlyPlayedKeys(): Set<String> = emptySet()
        override fun getLikedTracks(): Flow<List<Track>> = flowOf(emptyList())
        override suspend fun toggleLikeTrack(track: Track) = Unit
        override suspend fun deleteTrack(track: Track) = Unit
        override suspend fun getSkipSegments(videoId: String): List<Pair<Long, Long>> = emptyList()
        override fun isSponsorBlockEnabled(): Boolean = false
        override fun setSponsorBlockEnabled(enabled: Boolean) = Unit
        override fun getListeningStats(): com.example.hunterxmusic.domain.repository.ListeningStats = 
            com.example.hunterxmusic.domain.repository.ListeningStats(0, emptyList(), emptyList())
    }

    private fun createRepository(fakeApi: FakeAiApiService): AiRepositoryImpl {
        return AiRepositoryImpl(tempTestDir, fakeApi, FakeMusicRepository())
    }

    @Test
    fun testDefaultConfinementPrompt() = runBlocking {
        val fakeApi = FakeAiApiService()
        val repository = createRepository(fakeApi)
        
        val flowList = repository.sendMessage("Hello").toList()
        
        assertEquals(2, flowList.size)
        assertTrue(flowList[0] is AiChatStatus.Loading)
        assertTrue(flowList[1] is AiChatStatus.Success)
        
        val sentText = fakeApi.lastSentText ?: ""
        assertTrue(sentText.contains("CyroSonic"))
        assertTrue(sentText.contains("Sandeep Patel"))
        assertTrue(sentText.contains("User Library Context"))
        assertTrue(sentText.contains("User: Hello"))
    }

    @Test
    fun testFallbackMasking() = runBlocking {
        val fakeApi = FakeAiApiService().apply { throwError = true }
        val repository = createRepository(fakeApi)
        
        val flowList = repository.sendMessage("Help").toList()
        val success = flowList[1] as AiChatStatus.Success
        val response = success.responseText
        
        assertTrue(response.isNotBlank())
        // Masking: the offline path must never surface internal plumbing.
        assertFalse(response.contains("prexzyapis"))
        assertFalse(response.contains("http"))
        assertFalse(response.lowercase().contains("api key"))
    }

    @Test
    fun testCachePersistence() = runBlocking {
        val fakeApi = FakeAiApiService()
        
        // Setup initial repo session, send message, and store in cache
        var repository = createRepository(fakeApi)
        repository.sendMessage("Hello there").toList()
        
        assertEquals(2, repository.getChatHistory().size)
        
        // Instantiate a new repo targeting the same directory (simulating app relaunch)
        val nextRepository = createRepository(fakeApi)
        
        // Confirm history loads for a new repository session.
        assertEquals(2, nextRepository.getChatHistory().size)
        
        // Clear history and verify file deletion
        nextRepository.clearChat()
        assertEquals(0, nextRepository.getChatHistory().size)
        
        // Verify reloading empty directory yields clean state
        val cleanRepository = createRepository(fakeApi)
        assertEquals(0, cleanRepository.getChatHistory().size)
    }
}
