package com.example.hunterxmusic.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.example.hunterxmusic.BuildConfig
import com.example.hunterxmusic.data.notification.BroadcastNotificationPayload
import com.example.hunterxmusic.data.notification.OwnerNotificationManager
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class CloudAppConfig(
    // Maintenance Mode
    val isMaintenanceMode: Boolean = false,
    val maintenanceTitle: String = "🚧 CyroSonic Under Maintenance",
    val maintenanceMessage: String = "We are currently upgrading our 320kbps Lossless audio servers. Please check back shortly!",
    val buttonAction: String = "CLOSE_APP", // "CLOSE_APP" or "UPDATE_LINK"
    val buttonText: String = "Close App",
    
    // Version Control & Force Update Barrier
    val minRequiredVersion: String = "1.0.0",
    val latestVersion: String = "10.2.0",
    val isForceUpdate: Boolean = false,
    val updateTitle: String = "🚀 Major Update Required",
    val updateChangelog: String = "• Supercharged Lossless 320kbps Engine\n• Instant Parallel Multi-Engine Lyrics\n• Global Broadcast Notifications & Auto-Play\n• Pitch Black Pure AMOLED Interface",
    val updateUrl: String = "https://github.com/SandeepPatel/CyroSonic/releases/latest",
    
    // Global In-App Announcement Banner
    val announcementActive: Boolean = false,
    val announcementText: String = "🔥 Diljit & Karan new tracks added in 320kbps Lossless!",
    
    // Audio Server Routing
    val audioServerMode: String = "AUTO_BALANCED", // "AUTO_BALANCED", "SAAVN_PRIORITY", "YOUTUBE_PRIORITY"
    
    // Global Broadcast Notification
    val latestBroadcast: BroadcastNotificationPayload? = null,
    val broadcastId: String = ""
)

object AeroChaseCloudSyncManager {

    private const val PREFS_NAME = "cyrosonic_cloud_config"
    private const val KEY_CONFIG_JSON = "cached_cloud_config"
    private const val KEY_LAST_BROADCAST_ID = "last_processed_broadcast_id"

    // Primary Secure Cloud JSON Endpoint for live broadcast sync across devices
    private val CLOUD_CONFIG_URLS = listOf(
        "https://raw.githubusercontent.com/AeroChase/cloud-config/main/config.json"
    )

    private val gson = Gson()
    private val _configState = MutableStateFlow(CloudAppConfig())
    val configState: StateFlow<CloudAppConfig> = _configState.asStateFlow()

    private var syncJob: Job? = null

    fun getInstalledAppVersion(): String {
        val raw = try {
            BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            "10.2.0"
        }
        return raw.split("-")[0].trim()
    }

    /**
     * Checks if currentVersion is strictly lower than targetVersion (e.g. 4.5.0 < 4.95.1 -> true)
     */
    fun isVersionLower(currentVersion: String, targetVersion: String): Boolean {
        val cleanCurrent = currentVersion.split("-")[0].trim()
        val cleanTarget = targetVersion.split("-")[0].trim()

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val targetParts = cleanTarget.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currentParts.size, targetParts.size)
        for (i in 0 until maxLen) {
            val curr = currentParts.getOrElse(i) { 0 }
            val targ = targetParts.getOrElse(i) { 0 }
            if (curr < targ) return true
            if (curr > targ) return false
        }
        return false
    }

    /**
     * Determines whether the current user is locked out due to requiring an update
     */
    fun isUpdateBarrierActive(config: CloudAppConfig): Boolean {
        if (!config.isForceUpdate) return false
        val currentVersion = getInstalledAppVersion()
        return isVersionLower(currentVersion, config.minRequiredVersion)
    }

    fun init(context: Context, okHttpClient: OkHttpClient) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadCachedConfig(prefs)

        // Start background sync loop to poll remote maintenance mode, updates and broadcasts
        syncJob?.cancel()
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            // runCatching at the loop level: an uncaught exception here (Gson
            // drift, JSON shape change) used to kill the whole process
            // seconds after launch.
            while (isActive) {
                runCatching { fetchRemoteConfig(context, prefs, okHttpClient) }
                delay(300_000L) // Check every 5 minutes — battery kindness
            }
        }
    }

    private fun loadCachedConfig(prefs: SharedPreferences) {
        val cachedJson = prefs.getString(KEY_CONFIG_JSON, null)
        if (!cachedJson.isNullOrBlank()) {
            try {
                val loaded = gson.fromJson(cachedJson, CloudAppConfig::class.java) ?: return
                // Gson bypasses Kotlin defaults: fields absent from an older
                // cached blob deserialize as null inside non-null Strings,
                // then NPE the first consumer that touches them. Sanitize.
                _configState.value = loaded.copy(
                    broadcastId = loaded.broadcastId ?: "",
                    announcementText = loaded.announcementText ?: "",
                    minRequiredVersion = loaded.minRequiredVersion ?: "",
                    maintenanceTitle = loaded.maintenanceTitle ?: "CyroSonic Under Maintenance",
                    maintenanceMessage = loaded.maintenanceMessage ?: "",
                    buttonAction = loaded.buttonAction ?: "CLOSE_APP",
                    buttonText = loaded.buttonText ?: "Close App",
                    latestVersion = loaded.latestVersion ?: "1.0.0",
                    updateTitle = loaded.updateTitle ?: "Major Update Required",
                    updateChangelog = loaded.updateChangelog ?: "",
                    updateUrl = loaded.updateUrl ?: "",
                    announcementActive = loaded.announcementActive ?: false,
                    audioServerMode = loaded.audioServerMode ?: "AUTO_BALANCED"
                )
            } catch (_: Exception) { }
        }
    }

    private suspend fun fetchRemoteConfig(
        context: Context,
        prefs: SharedPreferences,
        client: OkHttpClient
    ) {
        for (url in CLOUD_CONFIG_URLS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .addHeader("Cache-Control", "no-cache")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank() && body.contains("{")) {
                        val loaded = gson.fromJson(body, CloudAppConfig::class.java)
                        if (loaded != null) {
                            val sanitized = loaded.copy(
                                broadcastId = loaded.broadcastId ?: "",
                                announcementText = loaded.announcementText ?: "",
                                minRequiredVersion = loaded.minRequiredVersion ?: "",
                                maintenanceTitle = loaded.maintenanceTitle ?: "CyroSonic Under Maintenance",
                                maintenanceMessage = loaded.maintenanceMessage ?: "",
                                buttonAction = loaded.buttonAction ?: "CLOSE_APP",
                                buttonText = loaded.buttonText ?: "Close App",
                                latestVersion = loaded.latestVersion ?: "1.0.0",
                                updateTitle = loaded.updateTitle ?: "Major Update Required",
                                updateChangelog = loaded.updateChangelog ?: "",
                                updateUrl = loaded.updateUrl ?: "",
                                announcementActive = loaded.announcementActive ?: false,
                                audioServerMode = loaded.audioServerMode ?: "AUTO_BALANCED"
                            )
                            _configState.value = sanitized
                            prefs.edit().putString(KEY_CONFIG_JSON, body).apply()

                            // Check for new broadcast notification
                            val lastBroadcastId = prefs.getString(KEY_LAST_BROADCAST_ID, "")
                            if (sanitized.broadcastId.isNotBlank() && sanitized.broadcastId != lastBroadcastId && sanitized.latestBroadcast != null) {
                                OwnerNotificationManager.sendRichBroadcastNotification(
                                    context = context,
                                    payload = sanitized.latestBroadcast
                                )
                                prefs.edit().putString(KEY_LAST_BROADCAST_ID, sanitized.broadcastId).apply()
                            }
                            return
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * Owner publishes updated Maintenance Mode, Version Gate, or Global Push Notification
     */
    suspend fun publishCloudConfig(
        context: Context,
        newConfig: CloudAppConfig,
        okHttpClient: OkHttpClient
    ): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(newConfig)

        _configState.value = newConfig
        prefs.edit().putString(KEY_CONFIG_JSON, json).apply()

        // Also trigger locally immediately if there is a new broadcast
        if (newConfig.latestBroadcast != null && newConfig.broadcastId.isNotBlank()) {
            OwnerNotificationManager.sendRichBroadcastNotification(
                context = context,
                payload = newConfig.latestBroadcast
            )
            prefs.edit().putString(KEY_LAST_BROADCAST_ID, newConfig.broadcastId).apply()
        }

        // Local update succeeded and is applied to current session
        return@withContext true
    }
}
