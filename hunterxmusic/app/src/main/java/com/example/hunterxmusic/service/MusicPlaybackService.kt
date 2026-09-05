package com.example.hunterxmusic.service

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.hunterxmusic.MainActivity
import com.example.hunterxmusic.HunterApplication
import com.example.hunterxmusic.data.player.EncryptedDataSourceFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Foreground Service hosting Jetpack Media3 ExoPlayer and managing MediaSession.
 * Enables background audio playback, notification controls, Bluetooth callbacks,
 * and audio focus management. Uses direct HTTP streaming for online tracks.
 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var widgetTickJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val defaultProvider = androidx.media3.session.DefaultMediaNotificationProvider(this)
            val customProvider = object : androidx.media3.session.MediaNotification.Provider {
                override fun createNotification(
                    mediaSession: androidx.media3.session.MediaSession,
                    customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
                    actionFactory: androidx.media3.session.MediaNotification.ActionFactory,
                    onNotificationChangedCallback: androidx.media3.session.MediaNotification.Provider.Callback
                ): androidx.media3.session.MediaNotification {
                    val mediaNotification = defaultProvider.createNotification(
                        mediaSession, customLayout, actionFactory, onNotificationChangedCallback
                    )
                    @Suppress("DEPRECATION")
                    mediaNotification.notification.icon = com.example.hunterxmusic.R.drawable.ic_notification
                    return mediaNotification
                }

                override fun handleCustomCommand(
                    session: androidx.media3.session.MediaSession,
                    action: String,
                    extras: android.os.Bundle
                ): Boolean {
                    return defaultProvider.handleCustomCommand(session, action, extras)
                }
            }
            setMediaNotificationProvider(customProvider)
        } catch (_: Exception) { }
        initializePlayer()
        observeVolumeBoost()
    }

    private fun startWidgetTick() {
        widgetTickJob?.cancel()
        widgetTickJob = serviceScope.launch {
            while (kotlin.coroutines.coroutineContext.isActive) {
                try {
                    exoPlayer?.let { player ->
                        com.example.hunterxmusic.widget.HuntrWidget.tick(
                            context = this@MusicPlaybackService,
                            positionMs = player.currentPosition.coerceAtLeast(0),
                            playing = player.isPlaying
                        )
                    }
                } catch (_: Exception) { }
                kotlinx.coroutines.delay(1200)
            }
        }
    }

    private fun stopWidgetTick() {
        widgetTickJob?.cancel()
        widgetTickJob = null
        try {
            exoPlayer?.let { player ->
                com.example.hunterxmusic.widget.HuntrWidget.tick(
                    context = this@MusicPlaybackService,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    playing = false
                )
            }
        } catch (_: Exception) { }
    }

    private fun observeVolumeBoost() {
        serviceScope.launch {
            try {
                HunterApplication.dependencies.musicPlayerManager.volumeBoost.collect { db ->
                    loudnessEnhancer?.setTargetGain((db * 100).toInt())
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlaybackService", "Error collecting volumeBoost flow: ${e.message}")
            }
        }
    }

    private fun setupLoudnessEnhancer(audioSessionId: Int) {
        try {
            loudnessEnhancer?.release()
            val enhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId)
            enhancer.enabled = true
            loudnessEnhancer = enhancer
            
            val currentBoost = HunterApplication.dependencies.musicPlayerManager.volumeBoost.value
            enhancer.setTargetGain((currentBoost * 100).toInt())
            HunterApplication.dependencies.audioFxManager.attachSession(audioSessionId)
        } catch (e: Exception) {
            android.util.Log.e("MusicPlaybackService", "Failed to setup LoudnessEnhancer: ${e.message}")
        }
    }

    private fun initializePlayer() {
        // Use OkHttp data source for streaming: fast, reliable, handles redirection/TLS.
        val okHttpClient = HunterApplication.dependencies.okHttpClient
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*"
            ))

        val cryptoManager = HunterApplication.dependencies.cryptoManager
        val encryptedDataSourceFactory = EncryptedDataSourceFactory(cryptoManager)

        // Persistent ExoPlayer chunk cache (mirrors YouTube Music architecture):
        // Automatically caches streaming audio chunks to disk so scrubbing and replaying
        // consume zero data and start with zero latency.
        val cachedHttpDataSourceFactory = com.example.hunterxmusic.data.player.ExoAudioCache.buildCacheDataSourceFactory(
            context = this,
            upstreamFactory = httpDataSourceFactory
        )

        // DefaultDataSource resolves content:// (MediaStore / downloaded device audio),
        // file://, asset:// and rawresource:// URIs, and delegates http(s):// to the
        // cached HTTP factory above.
        val deviceAndHttpDataSourceFactory = DefaultDataSource.Factory(this, cachedHttpDataSourceFactory)

        val mainDataSourceFactory = DataSource.Factory {
            val standardDataSource = deviceAndHttpDataSourceFactory.createDataSource()
            val encryptedDataSource = encryptedDataSourceFactory.createDataSource()

            object : DataSource {
                private var activeDataSource: DataSource? = null

                override fun addTransferListener(transferListener: TransferListener) {
                    standardDataSource.addTransferListener(transferListener)
                    encryptedDataSource.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val scheme = dataSpec.uri.scheme
                    val dataSource = if (scheme == "encrypted") encryptedDataSource else standardDataSource
                    activeDataSource = dataSource
                    return dataSource.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return activeDataSource?.read(buffer, offset, length) ?: -1
                }

                override fun getUri(): Uri? {
                    return activeDataSource?.uri
                }

                override fun close() {
                    activeDataSource?.close()
                    activeDataSource = null
                }
            }
        }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1_200,  // minBufferMs (1.2 seconds)
                8_000,  // maxBufferMs (8 seconds)
                25,     // bufferForPlaybackMs (25 milliseconds - ultra-instant 1s playback start)
                250     // bufferForPlaybackAfterRebufferMs (250 milliseconds)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(mainDataSourceFactory)

        val vocalRemover = HunterApplication.dependencies.vocalRemoverAudioProcessor
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(vocalRemover))
                    .build()
            }
        }

        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                // Audio focus management: set handleAudioFocus = false so incoming
                // notifications (WhatsApp, SMS, etc.) NEVER auto-pause playback.
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build()
                setAudioAttributes(audioAttributes, false)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // Auto-handled by MediaSession for notification updates
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            startWidgetTick()
                        } else {
                            stopWidgetTick()
                        }
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                            setupLoudnessEnhancer(audioSessionId)
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        refreshMediaButtons()
                    }
                })
            }

        exoPlayer?.let { player ->
            val intent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_PLAYER
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaSession = MediaSession.Builder(this, buildQueueNavigationPlayer(player))
                .setSessionActivity(pendingIntent)
                .setCallback(HuntrMediaCallback())
                .build()
            refreshMediaButtons()
        }
    }

    /**
     * ExoPlayer only ever holds a single MediaItem — the play queue is managed
     * separately by [MusicPlayerManager]. That means ExoPlayer never reports
     * COMMAND_SEEK_TO_NEXT / COMMAND_SEEK_TO_PREVIOUS as available, so the media
     * notification (and lockscreen / Bluetooth / Android Auto) never render the
     * Previous and Next buttons. This wrapper advertises those commands as always
     * available and routes them to the manager's own queue navigation, which is
     * what makes the Next button appear and work like a normal music app.
     */
    private fun buildQueueNavigationPlayer(player: ExoPlayer): Player {
        return object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() {
                HunterApplication.dependencies.musicPlayerManager.skipToNext()
            }

            override fun seekToPrevious() {
                HunterApplication.dependencies.musicPlayerManager.skipToPrevious()
            }

            override fun seekToNextMediaItem() {
                HunterApplication.dependencies.musicPlayerManager.skipToNext()
            }

            override fun seekToPreviousMediaItem() {
                HunterApplication.dependencies.musicPlayerManager.skipToPrevious()
            }
        }
    }

    // ── Notification actions: like / prev / play-pause / next / share ──

    private fun refreshMediaButtons() {
        val session = mediaSession ?: return
        val manager = HunterApplication.dependencies.musicPlayerManager
        val liked = manager.playbackState.value.currentTrack?.isLiked == true

        val likeButton = androidx.media3.session.CommandButton.Builder()
            .setDisplayName(if (liked) "Unlike" else "Like")
            .setIconResId(
                if (liked) com.example.hunterxmusic.R.drawable.ic_notif_heart
                else com.example.hunterxmusic.R.drawable.ic_notif_heart_outline
            )
            .setSessionCommand(
                androidx.media3.session.SessionCommand(CUSTOM_COMMAND_LIKE, android.os.Bundle.EMPTY)
            )
            .build()

        val shareButton = androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Share")
            .setIconResId(com.example.hunterxmusic.R.drawable.ic_notif_share)
            .setSessionCommand(
                androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SHARE, android.os.Bundle.EMPTY)
            )
            .build()

        // Previous / play-pause / next are provided by the session player's
        // available commands (see buildQueueNavigationPlayer) and rendered
        // automatically by the media notification in slots 0/1/2. The custom
        // layout only needs to add the extra Like and Share actions.
        session.setCustomLayout(listOf(likeButton, shareButton))
    }

    private fun fireShareIntent() {
        try {
            val track = HunterApplication.dependencies.musicPlayerManager
                .playbackState.value.currentTrack ?: return
            val link = "https://cyrosonic.com/track/${track.id}"
            val shareText = buildString {
                append("\"").append(track.title).append("\" by ").append(track.artist)
                if (link != null) append("\n\n").append(link)
                append("\n\nShared via CyroSonic")
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(
                android.content.Intent.createChooser(intent, "Share")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { }
    }

    private inner class HuntrMediaCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_LIKE, android.os.Bundle.EMPTY))
                    .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SHARE, android.os.Bundle.EMPTY))
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            return when (customCommand.customAction) {
                CUSTOM_COMMAND_LIKE -> {
                    HunterApplication.dependencies.musicPlayerManager.toggleLikeCurrent()
                    refreshMediaButtons()
                    doneFuture()
                }
                CUSTOM_COMMAND_SHARE -> {
                    fireShareIntent()
                    doneFuture()
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        private fun doneFuture(): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            val result = androidx.media3.session.SessionResult(
                androidx.media3.session.SessionResult.RESULT_SUCCESS,
                android.os.Bundle.EMPTY
            )
            return object : com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
                override fun isDone() = true
                override fun get(): androidx.media3.session.SessionResult = result
                override fun get(timeout: Long, unit: java.util.concurrent.TimeUnit): androidx.media3.session.SessionResult = result
                override fun isCancelled() = false
                override fun cancel(mayInterruptIfRunning: Boolean) = false
                override fun addListener(runnable: Runnable, executor: java.util.concurrent.Executor) {
                    executor.execute(runnable)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    companion object {
        const val CUSTOM_COMMAND_LIKE = "com.example.hunterxmusic.action.NOTIFICATION_LIKE"
        const val CUSTOM_COMMAND_SHARE = "com.example.hunterxmusic.action.NOTIFICATION_SHARE"
    }

    override fun onDestroy() {
        serviceScope.cancel()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}
