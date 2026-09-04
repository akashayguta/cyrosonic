package com.example.hunterxmusic.core.broadcast

import android.content.Context
import com.example.hunterxmusic.data.notification.BroadcastNotificationPayload
import com.example.hunterxmusic.data.notification.OwnerNotificationManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * ServerBroadcastManager
 *
 * Checks the CyroSonic cloud backend for Swiggy/Zomato-style server broadcasts,
 * new releases, flash drops, and trending track announcements.
 * When a new broadcast is detected, it triggers a native rich push notification
 * with cover art, vibration, and a 1-tap "Listen Now" action button.
 */
object ServerBroadcastManager {

    private const val PREFS_NAME = "cyrosonic_server_broadcasts"
    private const val KEY_LAST_SEEN_ID = "last_seen_broadcast_id"

    private val ENDPOINTS = listOf(
        "https://api.cyrosonic.com/api/broadcast/latest",
        "https://cyrosonic.com/api/broadcast/latest"
    )

    private val syncClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Triggers asynchronous broadcast sync in background.
     */
    fun syncLatestBroadcast(context: Context, client: OkHttpClient? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            checkAndDispatch(context.applicationContext, client ?: syncClient)
        }
    }

    suspend fun checkAndDispatch(context: Context, client: OkHttpClient) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastSeenId = prefs.getString(KEY_LAST_SEEN_ID, "") ?: ""

            for (endpoint in ENDPOINTS) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .header("Accept", "application/json")
                        .header("User-Agent", "CyroSonic-Android/${android.os.Build.VERSION.RELEASE}")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use

                        val bodyStr = response.body?.string() ?: return@use
                        val gson = Gson()
                        val json = gson.fromJson(bodyStr, JsonObject::class.java)

                        if (json.has("success") && json.get("success").asBoolean && json.has("broadcast")) {
                            val bcObj = json.getAsJsonObject("broadcast")
                            val id = bcObj.get("id")?.asString ?: return@use
                            val title = bcObj.get("title")?.asString ?: "CyroSonic Music"
                            val message = bcObj.get("message")?.asString ?: ""
                            val imageUrl = bcObj.get("imageUrl")?.asString
                            val trackQuery = bcObj.get("trackQuery")?.asString
                                ?: bcObj.get("trackId")?.asString
                            val actionText = bcObj.get("actionText")?.asString ?: "▶️ Listen Now"

                            // Only show if this broadcast has not been seen yet
                            if (id.isNotBlank() && id != lastSeenId) {
                                prefs.edit().putString(KEY_LAST_SEEN_ID, id).apply()

                                val payload = BroadcastNotificationPayload(
                                    title = title,
                                    message = message,
                                    imageUrl = if (!imageUrl.isNullOrBlank()) imageUrl else null,
                                    targetTrackQuery = if (!trackQuery.isNullOrBlank()) trackQuery else null,
                                    actionButtonText = actionText,
                                    styleType = "SERVER_BROADCAST"
                                )

                                OwnerNotificationManager.sendRichBroadcastNotification(context, payload)
                                return@withContext
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Try next endpoint on failure
                }
            }
        } catch (_: Exception) { }
    }
}
