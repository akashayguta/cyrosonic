package com.example.hunterxmusic.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import com.example.hunterxmusic.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What kind of audio a device file is registered as. The MediaStore flags
 * these separately, and ringtones/alarms/notifications used to be invisible
 * to this app entirely.
 */
enum class DeviceAudioCategory(val label: String) {
    MUSIC("Music"),
    RINGTONE("Ringtones"),
    ALARM("Alarms"),
    NOTIFICATION("Notifications"),
    OTHER("Other audio")
}

/** A device track plus the metadata needed to group and delete it. */
data class DeviceTrack(
    val track: Track,
    val category: DeviceAudioCategory,
    val mediaId: Long,
    val volumeName: String,
    val relativePath: String,
    val sizeBytes: Long
)

/**
 * Result of a storage scan. Failures are reported rather than swallowed —
 * the previous version returned an empty list for "no permission", "no files"
 * and "crashed" alike, so the UI could never explain itself.
 */
data class DeviceScanResult(
    val tracks: List<DeviceTrack> = emptyList(),
    val permissionDenied: Boolean = false,
    val error: String? = null
) {
    val isEmptySuccess: Boolean get() = tracks.isEmpty() && !permissionDenied && error == null
}

/**
 * Reads the audio already living in the phone's storage — the Music folder,
 * Downloads, WhatsApp audio, ringtones, alarms, anything the MediaStore
 * indexes, across every mounted volume including SD cards.
 *
 * URIs are `content://` media links the player handles directly: nothing is
 * copied and nothing is uploaded.
 */
class LocalDeviceMusicManager(private val context: Context) {

    /**
     * Every audio volume on the device. On API 29+ this includes SD cards and
     * USB storage; the old code only ever queried the primary external volume,
     * so SD-card music was invisible.
     */
    private fun audioVolumeNames(): Set<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context)
            } else {
                setOf(MediaStore.VOLUME_EXTERNAL)
            }
        } catch (_: Exception) {
            setOf(MediaStore.VOLUME_EXTERNAL)
        }
    }

    suspend fun scanDevice(
        includeCategories: Set<DeviceAudioCategory> = DeviceAudioCategory.values().toSet(),
        minDurationMs: Long = 0L
    ): DeviceScanResult = withContext(Dispatchers.IO) {
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.DATE_MODIFIED
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        // Accept every audio flavour the MediaStore knows about. The old
        // filter was `DURATION > 30000`, which is exactly why no ringtones
        // ever showed up — they are typically 10-30 seconds long.
        val selection = buildString {
            append("(")
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" OR ${MediaStore.Audio.Media.IS_RINGTONE} != 0")
            append(" OR ${MediaStore.Audio.Media.IS_ALARM} != 0")
            append(" OR ${MediaStore.Audio.Media.IS_NOTIFICATION} != 0")
            // IS_PODCAST only exists on API 29+; referencing it on older
            // versions throws IllegalArgumentException and kills the whole scan.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" OR ${MediaStore.Audio.Media.IS_PODCAST} != 0")
            }
            append(")")
            if (minDurationMs > 0L) {
                append(" AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMs")
            }
        }

        val collected = mutableListOf<DeviceTrack>()
        val seenIds = HashSet<Long>()
        var sawSecurityException = false
        var lastError: String? = null

        for (volume in audioVolumeNames()) {
            val collectionUri = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(volume)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            } catch (_: Exception) { continue }

            try {
                context.contentResolver.query(
                    collectionUri,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val isMusicCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
                    val isRingtoneCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE)
                    val isAlarmCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_ALARM)
                    val isNotifCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION)
                    val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else {
                        @Suppress("DEPRECATION")
                        cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    }

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        if (!seenIds.add(id)) continue

                        val category = when {
                            isRingtoneCol >= 0 && cursor.getInt(isRingtoneCol) != 0 -> DeviceAudioCategory.RINGTONE
                            isAlarmCol >= 0 && cursor.getInt(isAlarmCol) != 0 -> DeviceAudioCategory.ALARM
                            isNotifCol >= 0 && cursor.getInt(isNotifCol) != 0 -> DeviceAudioCategory.NOTIFICATION
                            isMusicCol >= 0 && cursor.getInt(isMusicCol) != 0 -> DeviceAudioCategory.MUSIC
                            else -> DeviceAudioCategory.OTHER
                        }
                        if (category !in includeCategories) continue

                        val albumId = cursor.getLong(albumIdCol)
                        val contentUri = ContentUris.withAppendedId(collectionUri, id)

                        collected.add(
                            DeviceTrack(
                                track = Track(
                                    id = "device_$id",
                                    title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                                        ?: "Unknown title",
                                    artist = cursor.getString(artistCol)
                                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                                        ?: "Unknown artist",
                                    album = cursor.getString(albumCol).orEmpty(),
                                    // The old code built
                                    // content://media/external/audio/albumart/<id>,
                                    // an unofficial path that has been broken
                                    // since Android 10 — hence no artwork.
                                    albumArtUrl = albumArtUri(albumId),
                                    durationMs = cursor.getLong(durationCol),
                                    streamingUrl = contentUri.toString(),
                                    // Marked local so the player resolves the
                                    // content:// URI directly instead of
                                    // trying to fetch a remote stream.
                                    localFilePath = contentUri.toString(),
                                    isDownloaded = true,
                                    encryptionIv = null,
                                    isLiked = false
                                ),
                                category = category,
                                mediaId = id,
                                volumeName = volume,
                                relativePath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "",
                                sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                sawSecurityException = true
            } catch (e: Exception) {
                lastError = e.message
            }
        }

        when {
            collected.isEmpty() && sawSecurityException -> DeviceScanResult(permissionDenied = true)
            collected.isEmpty() && lastError != null ->
                DeviceScanResult(error = "Couldn't read your storage: $lastError")
            else -> DeviceScanResult(tracks = collected)
        }
    }

    /** Album art for a device track, via the supported Albums collection. */
    private fun albumArtUri(albumId: Long): String? {
        if (albumId <= 0L) return null
        return ContentUris.withAppendedId(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            albumId
        ).toString()
    }

    /**
     * Builds the system delete request for one device file.
     *
     * On API 30+ Android requires the user to confirm deleting media this app
     * doesn't own, so this returns an [IntentSender] the caller launches
     * through an `IntentSenderRequest` contract. Returns null on older
     * versions, where [deleteDirectly] applies instead.
     */
    fun buildDeleteRequest(tracks: List<DeviceTrack>): IntentSender? {
        if (tracks.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val uris = tracks.map { dt ->
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.getContentUri(dt.volumeName),
                    dt.mediaId
                )
            }
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } catch (_: Exception) { null }
    }

    /** Pre-API-30 delete path. Returns how many rows were removed. */
    suspend fun deleteDirectly(tracks: List<DeviceTrack>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (dt in tracks) {
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    dt.mediaId
                )
                deleted += context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) { }
        }
        deleted
    }
}

fun formatDeviceDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
