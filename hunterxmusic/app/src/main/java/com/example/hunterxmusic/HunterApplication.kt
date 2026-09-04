package com.example.hunterxmusic

import android.app.Application

/**
 * Main Application context class for CyroSonic.
 */
class HunterApplication : Application() {
    companion object {
        lateinit var dependencies: AppDependencies
            private set

        /**
         * Process-lifetime gate for the CyroSonic opening animation. The intro
         * used to be gated on rememberSaveable, which restores "false" from the
         * saved instance state after process death — so the intro played only
         * on the very first launch ever and every later entry felt broken.
         * This flag lives for the life of the process instead: a fresh process
         * (every app launch) replays the intro; rotation/config changes skip it.
         */
        @Volatile
        var introShownThisProcess = false
    }
    override fun onCreate() {
        super.onCreate()
        dependencies = AppDependencies(this)
        installCrashLogger()

        // Load the persisted skin/light/dynamic prefs SYNCHRONOUSLY so frame
        // #1 composes in the right theme — the old LaunchedEffect ran a frame
        // late and returning light-skin users got a black flash.
        try {
            val nightMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val systemDark = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            com.example.hunterxmusic.data.local.ThemeManager.load(
                com.example.hunterxmusic.data.local.ThemePrefs(this),
                systemDark
            )
        } catch (_: Exception) { }
    }

    /**
     * Writes every uncaught crash to Downloads/cyrosonic-crash.txt (MediaStore,
     * no storage permission needed for our own files) so a real-device stack
     * trace is always recoverable without adb.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                val text = buildString {
                    append("=== CyroSonic CRASH @ $stamp (thread: ${thread.name}) ===\n")
                    append(android.util.Log.getStackTraceString(throwable))
                    append("\n\n")
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "cyrosonic-crash.txt")
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                    }
                    val uri = contentResolver.insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    )
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { os ->
                            os.write(text.toByteArray())
                        }
                    }
                }
                // Mirror to app-specific external storage as backup on all Android versions
                try {
                    val targetDir = getExternalFilesDir(null) ?: filesDir
                    java.io.File(targetDir, "cyrosonic-crash.txt").writeText(text)
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
