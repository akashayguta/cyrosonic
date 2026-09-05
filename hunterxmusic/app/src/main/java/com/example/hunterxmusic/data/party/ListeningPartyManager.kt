package com.example.hunterxmusic.data.party

import android.content.Context
import android.util.Log
import com.example.hunterxmusic.data.player.MusicPlayerManager
import com.example.hunterxmusic.domain.model.Track
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

enum class PartyConnectionStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class ListeningPartyState(
    val status: PartyConnectionStatus = PartyConnectionStatus.IDLE,
    val roomCode: String? = null,
    val isHost: Boolean = false,
    val listenersCount: Int = 1,
    val hostId: String? = null,
    val errorMessage: String? = null
)

object ListeningPartyManager {
    private const val TAG = "ListeningPartyManager"
    private const val BASE_URL = "https://cyrosonic.com"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var okHttpClient: OkHttpClient = OkHttpClient()

    private val _partyState = MutableStateFlow(ListeningPartyState())
    val partyState: StateFlow<ListeningPartyState> = _partyState.asStateFlow()

    private val myUserId = "user_" + UUID.randomUUID().toString().take(8)
    private var playerManagerRef: MusicPlayerManager? = null

    fun init(client: OkHttpClient) {
        this.okHttpClient = client
    }

    fun createParty(playerManager: MusicPlayerManager, onComplete: (Boolean, String?) -> Unit) {
        playerManagerRef = playerManager
        _partyState.value = _partyState.value.copy(status = PartyConnectionStatus.CONNECTING, errorMessage = null)

        val currentTrack = playerManager.playbackState.value.currentTrack
        val isPlaying = playerManager.playbackState.value.isPlaying
        val positionMs = playerManager.playbackState.value.currentPositionMs

        val payload = JsonObject().apply {
            addProperty("hostId", myUserId)
            addProperty("isPlaying", isPlaying)
            addProperty("positionMs", positionMs)
            if (currentTrack != null) {
                add("track", gson.toJsonTree(currentTrack))
            }
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/party/create")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                scope.launch {
                    _partyState.value = _partyState.value.copy(
                        status = PartyConnectionStatus.ERROR,
                        errorMessage = "Network error: ${e.message}"
                    )
                    onComplete(false, e.message)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val body = resp.body?.string()
                    try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val success = json.get("success")?.asBoolean == true
                        if (success) {
                            val code = json.get("roomCode").asString
                            scope.launch {
                                _partyState.value = ListeningPartyState(
                                    status = PartyConnectionStatus.CONNECTED,
                                    roomCode = code,
                                    isHost = true,
                                    listenersCount = 1,
                                    hostId = myUserId
                                )
                                startHostSyncLoop(code, playerManager)
                                onComplete(true, code)
                            }
                        } else {
                            val err = json.get("error")?.asString ?: "Failed to create party"
                            scope.launch {
                                _partyState.value = _partyState.value.copy(status = PartyConnectionStatus.ERROR, errorMessage = err)
                                onComplete(false, err)
                            }
                        }
                    } catch (e: Exception) {
                        scope.launch {
                            _partyState.value = _partyState.value.copy(status = PartyConnectionStatus.ERROR, errorMessage = e.message)
                            onComplete(false, e.message)
                        }
                    }
                }
            }
        })
    }

    fun joinParty(roomCode: String, playerManager: MusicPlayerManager, onComplete: (Boolean, String?) -> Unit) {
        playerManagerRef = playerManager
        _partyState.value = _partyState.value.copy(status = PartyConnectionStatus.CONNECTING, errorMessage = null)

        val cleanCode = roomCode.trim().uppercase()
        val payload = JsonObject().apply {
            addProperty("roomCode", cleanCode)
            addProperty("userId", myUserId)
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/party/join")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                scope.launch {
                    _partyState.value = _partyState.value.copy(
                        status = PartyConnectionStatus.ERROR,
                        errorMessage = "Cannot connect: ${e.message}"
                    )
                    onComplete(false, e.message)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val body = resp.body?.string()
                    try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val success = json.get("success")?.asBoolean == true
                        if (success) {
                            val partyObj = json.getAsJsonObject("party")
                            val listeners = partyObj.get("listeners")?.asInt ?: 2
                            val hostId = partyObj.get("hostId")?.asString
                            scope.launch {
                                _partyState.value = ListeningPartyState(
                                    status = PartyConnectionStatus.CONNECTED,
                                    roomCode = cleanCode,
                                    isHost = false,
                                    listenersCount = listeners,
                                    hostId = hostId
                                )
                                syncGuestFromPartyObject(partyObj, playerManager)
                                startGuestSyncLoop(cleanCode, playerManager)
                                onComplete(true, cleanCode)
                            }
                        } else {
                            val err = json.get("error")?.asString ?: "Room $cleanCode not found"
                            scope.launch {
                                _partyState.value = _partyState.value.copy(status = PartyConnectionStatus.ERROR, errorMessage = err)
                                onComplete(false, err)
                            }
                        }
                    } catch (e: Exception) {
                        scope.launch {
                            _partyState.value = _partyState.value.copy(status = PartyConnectionStatus.ERROR, errorMessage = e.message)
                            onComplete(false, e.message)
                        }
                    }
                }
            }
        })
    }

    fun leaveParty() {
        val currentState = _partyState.value
        val code = currentState.roomCode
        val isHost = currentState.isHost

        syncJob?.cancel()
        syncJob = null
        _partyState.value = ListeningPartyState(status = PartyConnectionStatus.IDLE)

        if (!code.isNullOrBlank()) {
            val payload = JsonObject().apply {
                addProperty("roomCode", code)
                if (isHost) addProperty("hostId", myUserId)
            }
            val request = Request.Builder()
                .url("$BASE_URL/api/party/leave")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
        }
    }

    private fun startHostSyncLoop(roomCode: String, playerManager: MusicPlayerManager) {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1200)
                try {
                    val playback = playerManager.playbackState.value
                    val payload = JsonObject().apply {
                        addProperty("roomCode", roomCode)
                        addProperty("hostId", myUserId)
                        addProperty("isPlaying", playback.isPlaying)
                        addProperty("positionMs", playback.currentPositionMs)
                        if (playback.currentTrack != null) {
                            add("track", gson.toJsonTree(playback.currentTrack))
                        }
                    }

                    val request = Request.Builder()
                        .url("$BASE_URL/api/party/sync")
                        .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val json = gson.fromJson(resp.body?.string(), JsonObject::class.java)
                            val party = json.getAsJsonObject("party")
                            val listeners = party?.get("listeners")?.asInt ?: 1
                            withContext(Dispatchers.Main) {
                                _partyState.value = _partyState.value.copy(listenersCount = listeners)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Host sync loop error: ${e.message}")
                }
            }
        }
    }

    private fun startGuestSyncLoop(roomCode: String, playerManager: MusicPlayerManager) {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1500)
                try {
                    val payload = JsonObject().apply {
                        addProperty("roomCode", roomCode)
                    }

                    val request = Request.Builder()
                        .url("$BASE_URL/api/party/sync")
                        .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val json = gson.fromJson(resp.body?.string(), JsonObject::class.java)
                            val party = json.getAsJsonObject("party")
                            if (party != null) {
                                withContext(Dispatchers.Main) {
                                    syncGuestFromPartyObject(party, playerManager)
                                }
                            }
                        } else if (resp.code == 404) {
                            // Host ended room
                            withContext(Dispatchers.Main) {
                                leaveParty()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Guest sync loop error: ${e.message}")
                }
            }
        }
    }

    private fun syncGuestFromPartyObject(party: JsonObject, playerManager: MusicPlayerManager) {
        val listeners = party.get("listeners")?.asInt ?: 2
        _partyState.value = _partyState.value.copy(listenersCount = listeners)

        val trackElement = party.get("track")
        if (trackElement != null && !trackElement.isJsonNull) {
            val serverTrack = gson.fromJson(trackElement, Track::class.java)
            val isPlaying = party.get("isPlaying")?.asBoolean ?: true
            val positionMs = party.get("positionMs")?.asLong ?: 0L
            val updatedAt = party.get("updatedAt")?.asLong ?: System.currentTimeMillis()

            // Account for network latency in seek target
            val elapsedSinceUpdate = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0L)
            val expectedPositionMs = if (isPlaying) positionMs + elapsedSinceUpdate else positionMs

            val localTrack = playerManager.playbackState.value.currentTrack
            val localIsPlaying = playerManager.playbackState.value.isPlaying
            val localPos = playerManager.playbackState.value.currentPositionMs

            if (localTrack?.id != serverTrack.id) {
                // Different song: play the host's track immediately at exact position
                playerManager.playTrack(serverTrack, seekToMs = expectedPositionMs)
            } else {
                // Same song: synchronize play/pause and seek if drifted > 1800ms
                if (localIsPlaying != isPlaying) {
                    playerManager.togglePlayPause()
                }
                val driftMs = kotlin.math.abs(localPos - expectedPositionMs)
                if (driftMs > 1800L) {
                    playerManager.seekTo(expectedPositionMs)
                }
            }
        }
    }
}
