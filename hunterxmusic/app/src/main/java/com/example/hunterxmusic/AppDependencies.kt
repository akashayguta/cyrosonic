package com.example.hunterxmusic

import android.content.Context
import androidx.room.Room
import com.example.hunterxmusic.core.security.CryptoManager
import com.example.hunterxmusic.data.local.db.MusicDatabase
import com.example.hunterxmusic.data.player.MusicPlayerManager
import com.example.hunterxmusic.data.remote.AiApiService
import com.example.hunterxmusic.data.remote.LrcLibService
import com.example.hunterxmusic.data.remote.MusicCatalogService
import com.example.hunterxmusic.data.repository.AiRepositoryImpl
import com.example.hunterxmusic.data.repository.MusicRepositoryImpl
import com.example.hunterxmusic.domain.repository.AiRepository
import com.example.hunterxmusic.domain.repository.MusicRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Centralized manual dependency injection container.
 */
class AppDependencies(context: Context) {

    val okHttpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(12, 5, TimeUnit.MINUTES))
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        })
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val musicCatalogRetrofit = Retrofit.Builder()
        .baseUrl("https://jiosaavn-api.vercel.app/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // LRCLIB's API contract demands an identifying User-Agent and sequential,
    // spaced-out requests — default `okhttp/x` UA strings get throttled hard,
    // which silently degrades synced lyrics to estimated timestamps.
    private val lrcLibClient = okHttpClient.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "CyroSonic/3.0 (Android; https://lrclib.net/api-docs)")
                    .build()
            )
        }
        .build()

    private val lrcLibRetrofit = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .client(lrcLibClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val dynamicAiServerClient = okHttpClient.newBuilder()
        .addInterceptor { chain ->
            var req = chain.request()
            val customHost = context.getSharedPreferences("ai_server_prefs", Context.MODE_PRIVATE)
                .getString("host_url", null)
                ?.trim()
            if (!customHost.isNullOrBlank() && req.url.host == "10.0.2.2") {
                try {
                    val parsed = customHost.toHttpUrlOrNull()
                    if (parsed != null) {
                        val newUrl = req.url.newBuilder()
                            .scheme(parsed.scheme)
                            .host(parsed.host)
                            .port(parsed.port)
                            .build()
                        req = req.newBuilder().url(newUrl).build()
                    }
                } catch (_: Exception) { }
            }
            chain.proceed(req)
        }
        .build()

    private val aiServerRetrofit = Retrofit.Builder()
        // Configurable via Settings or BuildConfig.AI_SERVER_URL
        .baseUrl(BuildConfig.AI_SERVER_URL)
        .client(dynamicAiServerClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val aiApiService: AiApiService = aiServerRetrofit.create(AiApiService::class.java)
    val musicCatalogService: MusicCatalogService = musicCatalogRetrofit.create(MusicCatalogService::class.java)
    val lrcLibService: LrcLibService = lrcLibRetrofit.create(LrcLibService::class.java)

    val cryptoManager = CryptoManager()
    val userProfileManager = com.example.hunterxmusic.data.repository.UserProfileManager(context.applicationContext)
    val localDeviceMusicManager = com.example.hunterxmusic.data.repository.LocalDeviceMusicManager(context.applicationContext)
    val vocalRemoverAudioProcessor = com.example.hunterxmusic.core.audio.VocalRemoverAudioProcessor()

    // Explicit migrations registered to ensure existing user likes, downloads,
    // playlists, history and translations are preserved across upgrades.
    val database = Room.databaseBuilder(
        context.applicationContext,
        MusicDatabase::class.java,
        "hunterxmusic.db"
    )
        .addMigrations(
            MusicDatabase.MIGRATION_1_2,
            MusicDatabase.MIGRATION_2_3,
            MusicDatabase.MIGRATION_3_4
        )
        .build()

    val localLibraryManager = com.example.hunterxmusic.data.repository.LocalLibraryManager(
        playlistDao = database.playlistDao(),
        historyDao = database.historyDao()
    )

    val homeShelvesCache = com.example.hunterxmusic.data.local.HomeShelvesCache(context.applicationContext)
    val recentTracksStore = com.example.hunterxmusic.data.local.RecentTracksStore(context.applicationContext)
    val lyricsTranslationPrefs = com.example.hunterxmusic.data.local.LyricsTranslationPrefs(context.applicationContext)
    val lyricsTranslator = com.example.hunterxmusic.data.translation.LyricsTranslator(okHttpClient, database.translationDao())
    val themePrefs = com.example.hunterxmusic.data.local.ThemePrefs(context.applicationContext)
    val personalizationEngine = com.example.hunterxmusic.data.analytics.PersonalizationEngine(context.applicationContext)
    val audioFxManager = com.example.hunterxmusic.core.audio.AudioFxManager(context.applicationContext)
    val otaUpdateManager = com.example.hunterxmusic.core.ota.OtaUpdateManager(context.applicationContext, okHttpClient)

    val musicRepository: MusicRepository = MusicRepositoryImpl(
        context = context,
        trackDao = database.trackDao(),
        cryptoManager = cryptoManager,
        okHttpClient = okHttpClient,
        lrcLibService = lrcLibService,
        musicCatalogService = musicCatalogService,
        searchHistoryDao = database.searchHistoryDao(),
        translationDao = database.translationDao()
    )

    // Audio engine manager — bridges UI to ExoPlayer MediaSession service
    val musicPlayerManager = MusicPlayerManager(
        context = context.applicationContext,
        musicRepository = musicRepository,
        vocalRemoverAudioProcessor = vocalRemoverAudioProcessor,
        localLibraryManager = localLibraryManager,
        personalizationEngine = personalizationEngine
    )

    val aiRepository: AiRepository = AiRepositoryImpl(
        cacheDir = context.cacheDir,
        aiApiService = aiApiService,
        musicRepository = musicRepository,
        userProfileManager = userProfileManager,
        okHttpClient = okHttpClient,
        onPlayCommand = { track -> musicPlayerManager.playTrack(track) },
        onQueueCommand = { track -> musicPlayerManager.enqueueTrack(track) },
        onLikeCommand = { musicPlayerManager.toggleLikeCurrent() },
        currentTrackProvider = { musicPlayerManager.playbackState.value.currentTrack }
    )
}
