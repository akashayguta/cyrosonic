package com.example.hunterxmusic.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromDownloadState(state: DownloadState): String {
        return state.name
    }

    @TypeConverter
    fun toDownloadState(state: String): DownloadState {
        return DownloadState.valueOf(state)
    }
}

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntryEntity::class,
        SearchHistoryEntity::class,
        TranslationEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun translationDao(): TranslationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v1 -> v2 schema safety
            }
        }

        /**
         * v2 -> v3: playlists, playlist tracks, and the v2 listen history.
         * Existing likes/downloads are untouched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                        `playlistId` INTEGER NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `albumArtUrl` TEXT,
                        `durationMs` INTEGER NOT NULL,
                        `streamingUrl` TEXT,
                        PRIMARY KEY(`playlistId`, `trackId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON DELETE CASCADE )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId` ON `playlist_tracks` (`playlistId`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `listen_history_v2` (
                        `rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `albumArtUrl` TEXT,
                        `durationMs` INTEGER NOT NULL,
                        `playedAt` INTEGER NOT NULL)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_history_v2_playedAt` ON `listen_history_v2` (`playedAt`)")
            }
        }

        /**
         * v3 -> v4: persistent search history and lyrics translation cache.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `search_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `query` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL)"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_query` ON `search_history` (`query`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `lyrics_translations` (
                        `targetLang` TEXT NOT NULL,
                        `songFingerprint` TEXT NOT NULL,
                        `lineIndex` INTEGER NOT NULL,
                        `translatedText` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`targetLang`, `songFingerprint`, `lineIndex`))"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_translations_songFingerprint_targetLang` ON `lyrics_translations` (`songFingerprint`, `targetLang`)")
            }
        }
    }
}
