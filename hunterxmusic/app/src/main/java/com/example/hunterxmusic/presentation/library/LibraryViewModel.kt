package com.example.hunterxmusic.presentation.library

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hunterxmusic.data.repository.DeviceAudioCategory
import com.example.hunterxmusic.data.repository.DeviceTrack
import com.example.hunterxmusic.data.repository.LocalDeviceMusicManager
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.domain.repository.MusicRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class LibrarySortOrder {
    RECENT,
    TITLE,
    ARTIST,
    DURATION
}

data class LibraryState(
    val downloadedTracks: List<Track> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val selectedFilter: String = "All",
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENT,

    // ── On-device storage ────────────────────────────────────
    val deviceTracks: List<DeviceTrack> = emptyList(),
    val isScanningDevice: Boolean = false,
    val hasScannedDevice: Boolean = false,
    val devicePermissionDenied: Boolean = false,
    val deviceError: String? = null,
    val deviceCategoryFilter: DeviceAudioCategory? = null
)

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val deviceMusic: LocalDeviceMusicManager? = null
) : ViewModel() {

    private val _state = mutableStateOf(LibraryState())
    val state: State<LibraryState> = _state

    init {
        loadOfflineTracks()
        loadLikedTracks()
    }

    private fun loadOfflineTracks() {
        viewModelScope.launch {
            musicRepository.getOfflineTracks().collectLatest { tracks ->
                _state.value = _state.value.copy(downloadedTracks = tracks)
            }
        }
    }

    private fun loadLikedTracks() {
        viewModelScope.launch {
            musicRepository.getLikedTracks().collectLatest { tracks ->
                _state.value = _state.value.copy(likedTracks = tracks)
            }
        }
    }

    fun onFilterSelected(filter: String) {
        _state.value = _state.value.copy(selectedFilter = filter)
    }

    fun onSearchQueryChanged(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun onSortOrderChanged(order: LibrarySortOrder) {
        _state.value = _state.value.copy(sortOrder = order)
    }

    fun getFilteredTracks(): List<Track> {
        val s = _state.value
        val sourceList = when (s.selectedFilter) {
            "Liked" -> s.likedTracks
            "Downloaded" -> s.downloadedTracks
            else -> (s.likedTracks + s.downloadedTracks).distinctBy { it.id }
        }

        val q = s.searchQuery.trim().lowercase()
        val searched = if (q.isEmpty()) sourceList else {
            sourceList.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }

        return when (s.sortOrder) {
            LibrarySortOrder.RECENT -> searched
            LibrarySortOrder.TITLE -> searched.sortedBy { it.title.lowercase() }
            LibrarySortOrder.ARTIST -> searched.sortedBy { it.artist.lowercase() }
            LibrarySortOrder.DURATION -> searched.sortedByDescending { it.durationMs }
        }
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            musicRepository.deleteTrack(track)
        }
    }

    // ── Device storage ───────────────────────────────────────

    /** Scans every mounted volume for audio, including ringtones and alarms. */
    fun scanDevice() {
        val manager = deviceMusic ?: return
        if (_state.value.isScanningDevice) return
        _state.value = _state.value.copy(
            isScanningDevice = true,
            deviceError = null,
            devicePermissionDenied = false
        )
        viewModelScope.launch {
            val result = manager.scanDevice()
            _state.value = _state.value.copy(
                deviceTracks = result.tracks,
                isScanningDevice = false,
                hasScannedDevice = true,
                devicePermissionDenied = result.permissionDenied,
                deviceError = result.error
            )
        }
    }

    fun onDevicePermissionDenied() {
        _state.value = _state.value.copy(
            isScanningDevice = false,
            hasScannedDevice = true,
            devicePermissionDenied = true
        )
    }

    fun setDeviceCategoryFilter(category: DeviceAudioCategory?) {
        _state.value = _state.value.copy(deviceCategoryFilter = category)
    }

    /** Device tracks after the active category filter. */
    fun visibleDeviceTracks(): List<DeviceTrack> {
        val filter = _state.value.deviceCategoryFilter ?: return _state.value.deviceTracks
        return _state.value.deviceTracks.filter { it.category == filter }
    }

    /** Which categories the scan actually found, in display order. */
    fun deviceCategoriesPresent(): List<DeviceAudioCategory> {
        val present = _state.value.deviceTracks.map { it.category }.toSet()
        return DeviceAudioCategory.values().filter { it in present }
    }

    /**
     * Delete request for a device file. On API 30+ this returns an IntentSender
     * the screen launches so the system can ask the user to confirm; on older
     * versions the delete happens directly.
     */
    fun buildDeviceDeleteRequest(track: DeviceTrack): android.content.IntentSender? {
        return deviceMusic?.buildDeleteRequest(listOf(track))
    }

    fun deleteDeviceTrackDirectly(track: DeviceTrack, onDone: () -> Unit = {}) {
        val manager = deviceMusic ?: return
        viewModelScope.launch {
            manager.deleteDirectly(listOf(track))
            removeDeviceTrackLocally(track)
            onDone()
        }
    }

    /** Drops a row after a confirmed system delete, without a full rescan. */
    fun removeDeviceTrackLocally(track: DeviceTrack) {
        _state.value = _state.value.copy(
            deviceTracks = _state.value.deviceTracks.filterNot { it.mediaId == track.mediaId }
        )
    }
}
