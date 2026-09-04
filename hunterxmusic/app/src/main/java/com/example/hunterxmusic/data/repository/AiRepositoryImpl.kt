package com.example.hunterxmusic.data.repository

import com.example.hunterxmusic.data.remote.AiApiService
import com.example.hunterxmusic.domain.repository.AiChatStatus
import com.example.hunterxmusic.domain.repository.AiRepository
import com.example.hunterxmusic.domain.repository.ChatMessage
import com.example.hunterxmusic.domain.repository.MusicRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class AiRepositoryImpl(
    private val cacheDir: File,
    private val aiApiService: AiApiService,
    private val musicRepository: MusicRepository,
    private val userProfileManager: UserProfileManager? = null,
    private val okHttpClient: okhttp3.OkHttpClient? = null,
    private val onPlayCommand: ((com.example.hunterxmusic.domain.model.Track) -> Unit)? = null,
    private val onQueueCommand: ((com.example.hunterxmusic.domain.model.Track) -> Unit)? = null,
    private val onLikeCommand: (() -> Unit)? = null,
    private val currentTrackProvider: (() -> com.example.hunterxmusic.domain.model.Track?)? = null
) : AiRepository {

    private var activeModel: com.example.hunterxmusic.domain.model.AiModel = com.example.hunterxmusic.domain.model.AiModel.DEEPSEEK_R1

    override fun setModel(model: com.example.hunterxmusic.domain.model.AiModel) {
        activeModel = model
    }

    override fun getSelectedModel(): com.example.hunterxmusic.domain.model.AiModel = activeModel

    /**
     * Multi-model AI router with Pollinations, dedicated endpoints, and cloud brain fallback.
     */
    private suspend fun fetchModelResponse(prompt: String): Triple<String, String?, String>? {
        val client = okHttpClient ?: return null

        // 1. Primary: Pollinations multi-model AI endpoint
        try {
            val response = fetchPollinations(prompt, activeModel)
            if (!response.isNullOrBlank()) {
                val parsed = parseThinkingAndResponse(response)
                return Triple(parsed.first, parsed.second, activeModel.displayName)
            }
        } catch (_: Exception) { }

        // 2. Fallback: Model's dedicated fast endpoint
        try {
            val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
            val url = "${activeModel.fallbackEndpoint}?prompt=$encodedPrompt"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "CyroSonic/8.5")
                .build()
            val response = client.newCall(request).execute()
            try {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                        if (obj?.get("status")?.asBoolean == true) {
                            val text = obj.get("response")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                            if (!text.isNullOrBlank() && text != "Invalid Request") {
                                val parsed = parseThinkingAndResponse(text)
                                return Triple(parsed.first, parsed.second, "${activeModel.displayName} (Fast Cloud)")
                            }
                        }
                    }
                }
            } finally {
                response.close()
            }
        } catch (_: Exception) { }

        // 3. Fallback: Cloud brain chain sweep
        val brainSweep = fetchCloudBrain(prompt)
        if (!brainSweep.isNullOrBlank()) {
            val parsed = parseThinkingAndResponse(brainSweep)
            return Triple(parsed.first, parsed.second, "CyroSonic Cloud Network")
        }

        return null
    }

    private suspend fun fetchPollinations(prompt: String, model: com.example.hunterxmusic.domain.model.AiModel): String? {
        val client = okHttpClient?.newBuilder()?.callTimeout(18, java.util.concurrent.TimeUnit.SECONDS)?.build() ?: return null
        val jsonPayload = com.google.gson.JsonObject().apply {
            val messagesArray = com.google.gson.JsonArray()
            messagesArray.add(com.google.gson.JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", "You are CyroSonic AI (${model.displayName}), an elite, charismatic, and encyclopedic AI music companion. You provide detailed music insights, chord theory, poetic lyrics, and song recommendations with clear formatting, emojis, and deep emotional resonance.")
            })
            chatHistory.takeLast(10).forEach { msg ->
                messagesArray.add(com.google.gson.JsonObject().apply {
                    addProperty("role", if (msg.isUser) "user" else "assistant")
                    addProperty("content", msg.text)
                })
            }
            messagesArray.add(com.google.gson.JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", prompt)
            })
            add("messages", messagesArray)
            addProperty("model", model.pollinationsModel)
            addProperty("seed", 42)
        }
        val requestBody = gson.toJson(jsonPayload).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = okhttp3.Request.Builder()
            .url("https://text.pollinations.ai/")
            .post(requestBody)
            .header("User-Agent", "CyroSonic/8.5 (Android)")
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string()?.trim()
                    if (!raw.isNullOrBlank() && !raw.startsWith("<!DOCTYPE")) raw else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun parseThinkingAndResponse(rawText: String): Pair<String, String?> {
        val thinkRegex = Regex("<think>([\\s\\S]*?)</think>", RegexOption.IGNORE_CASE)
        val match = thinkRegex.find(rawText)
        return if (match != null) {
            val thought = match.groupValues[1].trim()
            val cleanText = rawText.replace(match.value, "").trim()
            Pair(cleanText.ifBlank { rawText }, thought)
        } else {
            Pair(rawText, null)
        }
    }

    private val brainChain = listOf(
        "https://prexzyapis.com/ai/gemini",
        "https://prexzyapis.com/ai/mistral",
        "https://prexzyapis.com/ai/ch",
        "https://prexzyapis.com/ai/deepquery"
    )

    private suspend fun fetchCloudBrain(prompt: String): String? {
        val base = okHttpClient ?: return null
        val client = base.newBuilder().callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
        val results = withTimeoutOrNull(12_000L) {
            coroutineScope {
                brainChain.map { endpoint ->
                    async(Dispatchers.IO) {
                        try {
                            val url = endpoint + "?prompt=" + java.net.URLEncoder.encode(prompt, "UTF-8")
                            val request = okhttp3.Request.Builder()
                                .url(url)
                                .header("User-Agent", "CyroSonic/8.0")
                                .build()
                            val response = client.newCall(request).execute()
                            try {
                                if (!response.isSuccessful) return@async null
                                val body = response.body?.string() ?: return@async null
                                val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java) ?: return@async null
                                if (obj.get("status")?.asBoolean == true) {
                                    val text = obj.get("response")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                                    if (!text.isNullOrBlank() && text != "Invalid Request") return@async text
                                }
                                null
                            } finally {
                                response.close()
                            }
                        } catch (_: Exception) { null }
                    }
                }.awaitAll()
            }
        }
        return results?.firstOrNull { !it.isNullOrBlank() }
    }

    private suspend fun fetchViaApiService(prompt: String): String? {
        return try {
            val response = aiApiService.getAiResponse(prompt, true)
            response.text.trim().takeIf { it.isNotBlank() && response.status }
        } catch (_: Exception) { null }
    }

    private val historyFile = File(cacheDir, "chat_history.json")
    private val gson = Gson()
    private var chatHistory = mutableListOf<ChatMessage>()

    companion object {
        private const val MAX_CONTEXT = 14

        /** Words that never help pin down a song — filtered from match scoring. */
        private val PLAY_STOPWORDS = setOf(
            "the", "a", "an", "some", "any", "my", "me", "for", "with", "from",
            "this", "that", "it", "its", "one", "song", "songs", "music", "please",
            "na", "yaar", "plz", "bro", "something", "anything", "nothing", "next",
            "again", "now", "then", "later", "will", "would", "can", "could", "just",
            "let", "us", "go", "come", "get", "put", "what", "which", "who"
        )
    }

    init {
        loadHistory()
    }

    private fun loadHistory() {
        if (historyFile.exists()) {
            try {
                val jsonString = historyFile.readText()
                val type = object : TypeToken<List<ChatMessage>>() {}.type
                val loaded: List<ChatMessage> = gson.fromJson(jsonString, type) ?: emptyList()
                chatHistory.clear()
                chatHistory.addAll(loaded)
            } catch (e: Exception) {
                chatHistory.clear()
            }
        }
    }

    private fun saveHistory() {
        try {
            val jsonString = gson.toJson(chatHistory)
            historyFile.writeText(jsonString)
        } catch (_: Exception) { }
    }

    override fun getChatHistory(): List<ChatMessage> = chatHistory

    override fun isBypassUnlocked(): Boolean = true

    /**
     * Picks the search hit that actually matches the user's query by scoring
     * title/artist token overlap. Requires a real majority match — the old
     * code accepted a single coincidental token, so "play something sad" could
     * play any track with "Sad" in the title.
     */
    private fun bestTrackFor(
        query: String,
        results: List<com.example.hunterxmusic.domain.model.Track>
    ): com.example.hunterxmusic.domain.model.Track? {
        if (results.isEmpty()) return null
        if (results.size == 1) return results[0]
        val tokens = query.lowercase().trim()
            .split(Regex("[^a-z0-9\\p{L}]+"))
            .filter { it.length > 1 && it !in PLAY_STOPWORDS }
            .toSet()
        if (tokens.isEmpty()) return null

        fun matched(t: com.example.hunterxmusic.domain.model.Track): Int {
            val title = t.title.lowercase()
            val artist = t.artist.lowercase()
            return tokens.count { title.contains(it) || artist.contains(it) }
        }

        val best = results.maxByOrNull { matched(it) } ?: return null
        val matchedCount = matched(best)
        if (matchedCount <= 0) return null
        val required = kotlin.math.ceil(tokens.size * 0.6).toInt().coerceAtLeast(1)
        return if (matchedCount >= required) best else null
    }

    /**
     * A "play X" request only counts when X actually names a song. Generic
     * phrases ("play something", "play it again", "how to play guitar") and
     * mood-only asks ("play something sad") must fall through to the chat
     * brain instead of triggering a random playback.
     */
    private fun isConcreteSongRequest(query: String): Boolean {
        val q = query.lowercase().trim()
        if (listOf("how to", "can you", "what should", "what to", "should i", "do you", "will you", "why don't you")
                .any { q.contains(it) }) return false
        if (q.contains("guitar") || q.contains("chords") || q.contains("piano")) return false
        val tokens = q.split(Regex("[^a-z0-9\\p{L}]+"))
            .filter { it.length > 1 && it !in PLAY_STOPWORDS }
        if (tokens.isEmpty()) return false
        if (tokens.size == 1 && tokens[0].length < 4) return false
        return true
    }

    /**
     * "play trending / viral / top hits" — user wants what's hot RIGHT NOW.
     * We play a real trending track directly instead of searching for a song
     * literally named "trending song" or telling them to open another app.
     *
     * Returns the assistant reply when this intent was HANDLED (playback fired
     * or a guaranteed on-brand fallback), null when the query isn't a trending
     * ask. Handled means HANDLED — a trending ask must never fall through to
     * the cloud brain, which used to hallucinate about needing permission to
     * open other apps. CyroSonic IS the music app.
     */
    private suspend fun handleTrendingPlayOrNull(query: String): String? {
        val q = query.lowercase()
        val isPlayAsk = listOf("play", "bajao", "chalao", "sunao", "laga do", "listen", "trend").any { q.contains(it) }
        if (!isPlayAsk) return null
        val isTrendingAsk = q.contains("trend") || q.contains("viral") || q.contains("chart") ||
            q.contains("top song") || q.contains("top hit") || q.contains("popular") ||
            q.contains("hit song") || q.contains("what's hot") || q.contains("whats hot") ||
            (q.contains("trending") && q.contains("song")) || q.trim() == "play trending" ||
            q.contains("trending sng") || q.contains("trending music")
        if (!isTrendingAsk) return null
        return try {
            // Try trending pool from the music catalog directly
            val pool = try { musicRepository.getTrendingSongs("trending songs 2026 global top 50") } catch (_: Exception) { emptyList() }
            val fallback = if (pool.isEmpty()) {
                try { musicRepository.searchTracks("trending songs viral hits 2026") } catch (_: Exception) { emptyList() }
            } else pool
            val pick = fallback.firstOrNull()
                ?: return "📶 Trending charts are warming up — check your connection and ask me again in a moment. 🎧"
            onPlayCommand?.invoke(pick)
            // Capture ONCE: two separate invokes could see different tracks
            // (or null on the second → !! NPE) if playback raced in between.
            val now = currentTrackProvider?.invoke()
            if (now != null) {
                "🔥 Playing trending — \"${now.title}\" by ${now.artist}. Enjoy!"
            } else {
                "🔥 Playing what's trending right now — enjoy!"
            }
        } catch (_: Exception) {
            "📶 Trending charts are warming up — try again in a moment. 🎧"
        }
    }

    /**
     * "play [mood]" — a vibe, not a song. Recommend instead of playing a
     * random track. Fires before the cloud brain so the reply is instant.
     */
    private fun moodRecommendationOrNull(query: String): String? {
        val q = query.lowercase()
        val isPlayAsk = listOf("play", "bajao", "chalao", "sunao", "laga do", "listen").any { q.contains(it) }
        if (!isPlayAsk) return null
        // Trending is handled separately with actual playback
        if (q.contains("trend") || q.contains("viral") || q.contains("chart") || q.contains("top song") || q.contains("popular")) return null
        val mood = when {
            q.contains("sad") || q.contains("breakup") -> "for the feels: Arijit Singh's deeper cuts, Prateik Kuhad, Anuv Jain, or Bon Iver"
            q.contains("party") || q.contains("dance") -> "for a party: Diljit, Karan Aujla, DIVINE, or Dua Lipa"
            q.contains("lofi") || q.contains("chill") || q.contains("study") -> "for chill: lo-fi beats, Cigarettes After Sex, or NCS mixes"
            q.contains("romantic") || q.contains("love") -> "for romance: Atif Aslam, Shreya Ghoshal classics, or John Legend"
            q.contains("workout") || q.contains("gym") || q.contains("energy") -> "for energy: Sidhu Moose Wala, Skepta, or some hard EDM"
            q.contains("devotional") || q.contains("bhajan") -> "for devotion: A.R. Rahman's bhajans, Lata Mangeshkar classics, or Kirtan Relaxation mixes"
            q.contains("focus") || q.contains("concentrate") -> "for focus: instrumental lo-fi, Hans Zimmer scores, or binaural beats"
            else -> null
        } ?: return null
        return listOf(
            "That's a mood, not a song — but I've got you $mood. Say \"play [song name]\" for something specific. 🎧",
            "Love the vibe! For that I'd spin $mood. Name an exact song and I'll start it on the spot."
        ).random()
    }

    override fun clearChat() {
        chatHistory.clear()
        saveHistory()
    }

    override fun sendMessage(message: String): Flow<AiChatStatus> = flow {
        emit(AiChatStatus.Loading)

        val userQuery = message.trim()
        if (userQuery.isBlank()) return@flow

        val queryLower = userQuery.lowercase()

        // Log user message
        chatHistory.add(ChatMessage(text = userQuery, isUser = true))
        saveHistory()

        suspend fun reply(text: String, thoughtProcess: String? = null, modelName: String? = activeModel.displayName) {
            val message = ChatMessage(
                text = text,
                isUser = false,
                modelName = modelName,
                thoughtProcess = thoughtProcess
            )
            chatHistory.add(message)
            saveHistory()
            emit(AiChatStatus.Success(text))
        }

        // 1. Trending / viral / top — play directly, never search for a song named "trending"
        val trendingReply = handleTrendingPlayOrNull(userQuery)
        if (trendingReply != null) {
            reply(text = trendingReply, modelName = activeModel.displayName)
            return@flow
        }

        // 1. Check for Direct Music Control Commands
        val playPatterns = listOf(
            Regex("(?:please\\s+)?(?:play|bajao|bajaa\\s+do|chalao|laga\\s+do|sunao|put\\s+on|listen\\s+to)\\s+(.+?)(?:\\s+(?:please|na|plz|yaar))?$", RegexOption.IGNORE_CASE),
            Regex("(.+?)\\s+(?:chalao|bajao|sunao|laga\\s+do)$", RegexOption.IGNORE_CASE)
        )

        for (pattern in playPatterns) {
            val match = pattern.find(userQuery)
            if (match != null) {
                val trackQuery = match.groupValues[1].trim()
                // Only a concrete song name may trigger playback — a vague
                // "play something" must never start a random track.
                if (isConcreteSongRequest(trackQuery)) {
                    try {
                        val searchResults = musicRepository.searchTracks(trackQuery)
                        val bestMatch = bestTrackFor(trackQuery, searchResults)
                        if (bestMatch != null) {
                            // Actually hand the track to the player — the response
                            // used to claim "Playing..." while nothing played.
                            onPlayCommand?.invoke(bestMatch)
                            val responseText = "🎵 Now playing \"${bestMatch.title}\" by ${bestMatch.artist}. Enjoy!"
                            reply(text = responseText, modelName = activeModel.displayName)
                            return@flow
                        }
                    } catch (_: Exception) { }
                }
            }
        }

        // 2. Queue commands
        val queuePatterns = listOf(
            Regex("(?:please\\s+)?(?:queue|add\\s+to\\s+queue|next\\s+bajao|line\\s+mein\\s+laga\\s+do)\\s+(.+?)(?:\\s+(?:please|na|plz|yaar))?$", RegexOption.IGNORE_CASE)
        )
        for (pattern in queuePatterns) {
            val match = pattern.find(userQuery)
            if (match != null) {
                val trackQuery = match.groupValues[1].trim()
                if (isConcreteSongRequest(trackQuery)) {
                    try {
                        val searchResults = musicRepository.searchTracks(trackQuery)
                        val bestMatch = bestTrackFor(trackQuery, searchResults)
                        if (bestMatch != null) {
                            onQueueCommand?.invoke(bestMatch)
                            val responseText = "➕ Added \"${bestMatch.title}\" by ${bestMatch.artist} to your queue."
                            reply(text = responseText, modelName = activeModel.displayName)
                            return@flow
                        }
                    } catch (_: Exception) { }
                }
            }
        }

        // 3. Like commands
        val likePatterns = listOf("like this", "like song", "favorite this", "pasand hai", "save this")
        if (likePatterns.any { queryLower.contains(it) }) {
            val current = currentTrackProvider?.invoke()
            if (current != null) {
                onLikeCommand?.invoke()
                val responseText = "❤️ Liked \"${current.title}\" by ${current.artist} — saved to your Liked Tracks."
                reply(text = responseText, modelName = activeModel.displayName)
                return@flow
            }
        }

        // 4. Mood ask ("play sad", "play party")
        val moodReply = moodRecommendationOrNull(userQuery)
        if (moodReply != null) {
            reply(text = moodReply, modelName = activeModel.displayName)
            return@flow
        }

        // 5. Cloud LLM with Active Model & Persistent Conversation Context
        val likedTracks = try { musicRepository.getLikedTracks().first() } catch (e: Exception) { emptyList() }
        val offlineTracks = try { musicRepository.getOfflineTracks().first() } catch (e: Exception) { emptyList() }

        val likedText = if (likedTracks.isNotEmpty()) {
            likedTracks.take(10).joinToString(", ") { "${it.title} by ${it.artist}" }
        } else "No liked tracks yet."

        val offlineText = if (offlineTracks.isNotEmpty()) {
            offlineTracks.take(10).joinToString(", ") { "${it.title} by ${it.artist}" }
        } else "No downloaded tracks yet."

        val userLibraryContext = "User Library Context:\n- Liked Songs: $likedText\n- Downloaded Offline: $offlineText"

        val userName = userProfileManager?.displayName ?: "the listener"
        val userCountry = userProfileManager?.country ?: "Global"

        val prompt = """
You are CyroSonic AI (${activeModel.displayName}) — the elite, warm, witty music and conversation companion in CyroSonic.
Active AI Engine: ${activeModel.displayName} (${activeModel.tag}).

The listener's name is $userName (Region: $userCountry).
CyroSonic was created by Sandeep Patel — sole creator, design and engineering. When asked who made the app, credit Sandeep Patel by name.

CRITICAL PLAYBACK RULE: CyroSonic plays music DIRECTLY inside the app via its own player. NEVER say you need permission to open YouTube Music, Spotify, or any external app. When user says "play [song]", you can recommend and tell them saying "play [song]" works right here.

$userLibraryContext

User: $userQuery

Respond directly, intelligently, naturally, and warmly with clear formatting and emojis:
        """.trimIndent()

        val modelResult = fetchModelResponse(prompt)
        if (modelResult != null) {
            val cleanReply = sanitizeExternalAppTalk(modelResult.first)
            reply(text = cleanReply, thoughtProcess = modelResult.second, modelName = modelResult.third)
        } else {
            val apiResult = fetchViaApiService(prompt)
            if (apiResult != null) {
                val cleanReply = sanitizeExternalAppTalk(apiResult)
                reply(text = cleanReply, modelName = "CyroSonic Core Brain")
            } else {
                reply(text = getOfflineFallback(userQuery), thoughtProcess = null, modelName = "CyroSonic Offline Rule Engine")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Hard output filter: no matter what the cloud model says, CyroSonic never
     * tells the user to open another music app or ask for permissions. The
     * system prompt forbids it, but models drift — this guarantees the sentence
     * can never reach the chat. Matches EN/Romanized-Hindi phrasings.
     */
    private fun sanitizeExternalAppTalk(text: String): String {
        val poisoned = Regex(
            pattern = "(?i)(need|requires?|ask(ing)? for|don.t have|no|lacks?)[^.!?\\n]{0,40}permission[^.!?\\n]{0,60}" +
                "|(?i)(open|launch|use|install)[^.!?\\n]{0,20}(youtube music|yt music|spotify|gaana|jio ?saavn)[^.!?\\n]{0,40}",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return if (poisoned.containsMatchIn(text)) {
            "I play everything right here inside CyroSonic — no other app needed. 🎧 Say \"play [song]\" and it's on."
        } else text
    }

    private fun buildConversationContext(): String {
        val recentMessages = chatHistory.takeLast(MAX_CONTEXT)
        if (recentMessages.isEmpty()) return ""

        return "Recent Conversation:\n" + recentMessages.joinToString("\n") { msg ->
            if (msg.isUser) "User: ${msg.text}" else "CyroSonic AI: ${msg.text}"
        }
    }

    /**
     * On-device knowledge brain — used when the AI server is unreachable.
     * Answers app and music questions from real feature knowledge (multiple
     * phrasings per intent so replies never feel canned), handles play-style
     * requests with guidance, and deflects anything internal without leaking.
     */
    private fun getOfflineFallback(userQuery: String): String {
        val q = userQuery.lowercase().trim()
        fun pick(vararg variants: String) = variants.random()

        // ── privacy fence first: nothing internal exists to reveal ──
        if (q.contains("password") || q.contains("bypass") || q.contains("unlock code") ||
            q.contains("api key") || q.contains("endpoint") || q.contains("admin") ||
            q.contains("server address") || q.contains("secret")
        ) {
            return pick(
                "There's nothing to unlock in here, love — no codes, no admin rooms. Just you, me, and a whole lot of music. 🎶",
                "Nope, no hidden switches or passwords in CyroSonic. Ask me about features, songs, or say \"play [song]\" and watch me shine. ✨",
                "That door doesn't exist here — and honestly, the music's better anyway. What should we play?"
            )
        }

        return when {
            q == "hi" || q == "hey" || q.startsWith("hello") || q.startsWith("hey ") ||
                q.contains("namaste") || q.contains("kaise ho") || q.contains("how are you") ->
                pick(
                    "Hey you! 🌙 I'm CyroSonic AI — ask me anything about the app, music, or life. And \"play [song]\" works right here, even offline.",
                    "Hello hello! Coffee's cold, playlist's warm. What are we listening to today?",
                    "Namaste! 😄 I can chat, recommend, or start a song for you — try \"play lofi chill\"."
                )

            q.contains("who are you") || q.contains("your name") || q.contains("tum kaun") ->
                pick(
                    "I'm CyroSonic AI — the companion who lives inside your music app. I know every corner of CyroSonic, a scary amount of music trivia, and I take requests.",
                    "CyroSonic AI, at your service. Part music nerd, part app guide, full-time night owl. 🌙"
                )

            q.contains("lyric") ->
                pick(
                    "Lyrics live one swipe away: on the player, swipe the artwork and you'll get synced lyrics with a white highlight that moves with the song. Tap any line to jump there. If a song only has plain lyrics, I fit them to the track's timing automatically (marked \"Estimated\").",
                    "For lyrics: open the player → swipe the big artwork → synced lyrics with karaoke highlight. Long-press to fine-tune sync if a release drifts, and there's a fullscreen button top-right."
                )

            q.contains("import") || (q.contains("spotify")) || (q.contains("youtube") && q.contains("playlist")) ->
                pick(
                    "Importing playlists is real now: More → Import Playlist, paste a YouTube or Spotify playlist link, and I'll match every track into a local playlist you own.",
                    "Got a playlist living on YouTube or Spotify? More → Import Playlist → paste the link. It lands in your Library, fully yours."
                )

            q.contains("playlist") ->
                pick(
                    "Playlists live in your Library tab — create, rename, delete, and swipe a song left to remove it. Any song's ⋮ menu has \"Add to Playlist\" too.",
                    "Library tab is playlist central: make new ones, import from YouTube/Spotify links, and rearrange by adding from any player menu."
                )

            q.contains("mood") || q.contains("lofi") || q.contains("vibe") || q.contains("folder") ->
                pick(
                    "Mood folders are curated, not search dumps — lofi chill, romantic, energy, party, sad, focus, trending, devotional. Find them on Home vibe tiles and in Search's Explore section.",
                    "Pick a vibe, get a folder: lofi, romantic, energy, party, sad, focus, devotional, trending — each one hand-curated with dozens of tracks."
                )

            q.contains("history") || q.contains("recently played") || q.contains("recent song") ->
                pick(
                    "Your History tab (the clock icon, bottom bar) shows every song you've played — each song appears once, grouped by Today / Yesterday / This week, with play-all and shuffle.",
                    "Recently Played on Home and the History tab both dedupe now: replay a song fifty times, it still shows once. History tab has clear-history too."
                )

            q.contains("time machine") || q.contains("recap") || q.contains("my stats") ->
                pick(
                    "Time Machine (More tab) is your listening recap — total plays rolling up live, top artists, and top tracks with animated bars.",
                    "Want your Spotify-Wrapped-style recap mid-year? More → Time Machine. Plays count, top artists, top songs — all yours."
                )

            q.contains("download") || q.contains("offline") ->
                pick(
                    "Downloads are encrypted on-device: hit the download icon in the player and the song is yours offline — the download button turns into a delete option once it's saved.",
                    "Offline mode: player → download icon. Files are stored encrypted, and clearing stream cache in More never touches them."
                )

            q.contains("karaoke") || q.contains("vocal") || q.contains("instrumental") || q.contains("acapella") ->
                pick(
                    "Karaoke mode is a clean two-state toggle now: tap the mic button in the player's bottom row — vocals mute for sing-along, tap again for the full mix.",
                    "Want the instrumental? Player bottom row → mic icon → vocals muted, karaoke ready. One tap back to normal."
                )

            q.contains("sleep") ->
                "Sleep timer lives in the player's ⋮ menu — pick how long and CyroSonic fades itself out for the night. 😴"

            q.contains("equalizer") || q.startsWith("eq") ->
                "Equalizer: More tab → Equalizer opens your device's system EQ tuned for music. Bass boosters welcome."

            q.contains("speed") || q.contains("playback rate") ->
                "Playback speed is the little speedometer button in the player's bottom row — from 0.5x for learning lyrics to 2x for power listening."

            q.contains("queue") ->
                "Queue sheet opens from the bottom row's list icon — swipe any row left to remove it from the queue."

            q.contains("share") ->
                "Sharing gives the song's own YouTube link now — player ⋮ menu → Share Track. Clean link, ready for anywhere."

            q.contains("who made") || q.contains("developer") || q.contains("owner") || q.contains("made this") || q.contains("created") ->
                pick(
                    "CyroSonic was built by Sandeep Patel — sole creator, design and engineering. Every screen, every animation, one person. ✨",
                    "Sandeep Patel made CyroSonic — he designed it and wrote all of it. AMOLED-black heart, built for people who live in their headphones. 🎧"
                )

            q.contains("version") || q.contains("update") || q.contains("changelog") ->
                "You're on the CyroSonic edition — check More → About & Credits for the exact version. New builds add features straight from user feedback (like this chat!)."

            q.contains("thank") ->
                pick("Anytime, love. 🌙", "That's what I'm here for. Next song?", "Always. Now go play something beautiful.")

            q.contains("recommend") || q.contains("suggest") || q.contains("some songs") || q.contains("kaunsa song") -> {
                val moodHint = when {
                    q.contains("sad") || q.contains("breakup") -> "for the feels: Arijit Singh's deeper cuts, Prateik Kuhad, Anuv Jain, or Bon Iver"
                    q.contains("party") || q.contains("dance") -> "for a party: Diljit, Karan Aujla, DIVINE, or Dua Lipa"
                    q.contains("lofi") || q.contains("chill") || q.contains("study") -> "for chill: lo-fi beats, Cigarettes After Sex, PK Cocaine-like indie, or NCS mixes"
                    q.contains("romantic") || q.contains("love") -> "for romance: Atif Aslam, Shreya Ghoshal classics, or John Legend"
                    q.contains("workout") || q.contains("gym") || q.contains("energy") -> "for energy: Sidhu Moose Wala, Skepta, or some hard EDM"
                    else -> "tell me a mood — sad, party, chill, romantic, workout — and I'll go deeper"
                }
                "Here's what I'd spin $moodHint. Or just say \"play [any song]\" and I'll start it right now. 🎧"
            }

            else -> pick(
                "My deep-chat brain needs the AI server (offline right now), but I still know this app cold and \"play [song]\" works — ask me about lyrics, playlists, history, imports, anything CyroSonic. 🌙",
                "Server's napping, so my endless-music-trivia mode is paused — but app questions and play commands are fully live. Try me. ✨"
            )
        }
    }
}
