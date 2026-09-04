package com.example.hunterxmusic.core.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.hunterxmusic.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class OtaUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val forceUpdate: Boolean = false,
    val featureFlags: Map<String, Any> = emptyMap()
)

sealed interface OtaState {
    data object Idle : OtaState
    data object Checking : OtaState
    data class UpdateAvailable(val info: OtaUpdateInfo) : OtaState
    data class Downloading(val progressPercent: Int) : OtaState
    data class ReadyToInstall(val apkFile: File, val info: OtaUpdateInfo) : OtaState
    data object UpToDate : OtaState
    data class Error(val message: String) : OtaState
}

/**
 * Over-The-Air (OTA) Production Update & Remote Telematics Engine for CyroSonic.
 * Connects securely to the official domain (https://api.cyrosonic.com/api/version),
 * detects newer builds, downloads public APKs in the background, and seamlessly
 * initiates system-level package updates via Android FileProvider.
 */
class OtaUpdateManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("cyrosonic_ota_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<OtaState>(OtaState.Idle)
    val state: StateFlow<OtaState> = _state.asStateFlow()

    companion object {
        private const val PRIMARY_OTA_ENDPOINT = "https://api.cyrosonic.com/api/version"
        private const val FALLBACK_OTA_ENDPOINT = "https://cyrosonic.com/api/version"
        private const val KEY_FEATURE_FLAGS = "ota_feature_flags_json"
    }

    /**
     * Checks if a new release is available over-the-air.
     */
    fun checkForUpdates(silent: Boolean = true) {
        if (_state.value is OtaState.Checking || _state.value is OtaState.Downloading) return

        _state.value = OtaState.Checking
        scope.launch {
            try {
                val endpoints = listOf(PRIMARY_OTA_ENDPOINT, FALLBACK_OTA_ENDPOINT)
                var jsonResponse: String? = null

                for (url in endpoints) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "CyroSonic-Android/${BuildConfig.VERSION_NAME} (Linux; Android ${Build.VERSION.RELEASE})")
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            jsonResponse = response.body?.string()
                            if (!jsonResponse.isNullOrBlank()) break
                        }
                    } catch (_: Exception) {
                        // Fallback to next endpoint
                    }
                }

                if (jsonResponse.isNullOrBlank()) {
                    _state.value = if (silent) OtaState.Idle else OtaState.Error("Could not reach update server")
                    return@launch
                }

                val obj = JSONObject(jsonResponse)
                val remoteCode = obj.optInt("versionCode", 0)
                val remoteName = obj.optString("versionName", "")
                val apkUrl = obj.optString("apkUrl", "")
                val changelog = obj.optString("changelog", "• Performance improvements and audio engine updates")
                val forceUpdate = obj.optBoolean("forceUpdate", false)

                // Save remote feature flags if present
                val flagsObj = obj.optJSONObject("featureFlags")
                if (flagsObj != null) {
                    prefs.edit().putString(KEY_FEATURE_FLAGS, flagsObj.toString()).apply()
                }

                val updateInfo = OtaUpdateInfo(
                    versionCode = remoteCode,
                    versionName = remoteName,
                    apkUrl = apkUrl,
                    changelog = changelog,
                    forceUpdate = forceUpdate
                )

                val currentCode = BuildConfig.VERSION_CODE
                if (remoteCode > currentCode && apkUrl.isNotBlank()) {
                    _state.value = OtaState.UpdateAvailable(updateInfo)
                } else {
                    _state.value = if (silent) OtaState.Idle else OtaState.UpToDate
                }
            } catch (e: Exception) {
                _state.value = if (silent) OtaState.Idle else OtaState.Error(e.localizedMessage ?: "Check failed")
            }
        }
    }

    /**
     * Downloads the APK file in the background with progress and initiates install.
     */
    fun startDownloadAndInstall(updateInfo: OtaUpdateInfo) {
        if (_state.value is OtaState.Downloading) return
        _state.value = OtaState.Downloading(0)

        scope.launch {
            try {
                val request = Request.Builder()
                    .url(updateInfo.apkUrl)
                    .header("User-Agent", "CyroSonic-OTA-Downloader")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    _state.value = OtaState.Error("Server returned ${response.code} during APK download")
                    return@launch
                }

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val totalBytes = body.contentLength()

                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val destinationFile = File(downloadDir, "CyroSonic-${updateInfo.versionName}.apk")
                if (destinationFile.exists()) destinationFile.delete()

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastReportedPercent = 0

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    _state.value = OtaState.Downloading(percent)
                                }
                            }
                        }
                    }
                }

                _state.value = OtaState.ReadyToInstall(destinationFile, updateInfo)
                withContext(Dispatchers.Main) {
                    launchPackageInstaller(destinationFile)
                }
            } catch (e: Exception) {
                _state.value = OtaState.Error("Download failed: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Hands the downloaded APK over to the Android system package installer.
     */
    fun launchPackageInstaller(apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show()
                return
            }

            // Android 8.0+ Unknown App Sources Permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Please allow permission to install CyroSonic updates", Toast.LENGTH_LONG).show()
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permissionIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Installation failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun dismiss() {
        _state.value = OtaState.Idle
    }

    /**
     * Checks remote dynamic feature toggles.
     */
    fun isFeatureEnabled(featureKey: String, default: Boolean = true): Boolean {
        val json = prefs.getString(KEY_FEATURE_FLAGS, null) ?: return default
        return try {
            val obj = JSONObject(json)
            obj.optBoolean(featureKey, default)
        } catch (_: Exception) {
            default
        }
    }
}
