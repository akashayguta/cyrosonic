package com.example.hunterxmusic.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * High-performance ExoPlayer persistent chunk caching layer.
 * Mirrors the architecture found in the official YouTube Music client:
 * uses SimpleCache with a 300 MB LRU evictor and standalone database index.
 * Enables zero-data replaying and instant scrubbing.
 */
@OptIn(UnstableApi::class)
object ExoAudioCache {
    private const val CACHE_DIR_NAME = "media_cache"
    private const val MAX_CACHE_BYTES = 300L * 1024L * 1024L // 300 MB

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getSimpleCache(context: Context): SimpleCache {
        return simpleCache ?: run {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            SimpleCache(cacheDir, evictor, databaseProvider).also {
                simpleCache = it
            }
        }
    }

    fun buildCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        val cache = getSimpleCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
