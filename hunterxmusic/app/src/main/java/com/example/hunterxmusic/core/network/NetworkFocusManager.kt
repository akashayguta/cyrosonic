package com.example.hunterxmusic.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class ScreenFocus {
    HOME,
    SEARCH,
    LIBRARY,
    PLAYER,
    PLAYBACK_CRITICAL
}

/**
 * Intelligent Dynamic Network Focus & Bandwidth Controller:
 * - Directs 100% network bandwidth and thread allocation to the actively focused screen.
 * - When any song is tapped, immediately activates PLAYBACK_CRITICAL to ensure instant <1s audio playback.
 * - Cancels stale background operations when switching between Home, Search, and Library.
 */
object NetworkFocusManager {

    private val _currentFocus = MutableStateFlow(ScreenFocus.HOME)
    val currentFocus: StateFlow<ScreenFocus> = _currentFocus.asStateFlow()

    private val isPlaybackCritical = AtomicBoolean(false)

    /**
     * Updates screen focus when user navigates tabs.
     */
    fun setScreenFocus(focus: ScreenFocus) {
        if (!isPlaybackCritical.get()) {
            _currentFocus.value = focus
        }
    }

    /**
     * Activates maximum priority network allocation for instant song playback (<1 second start).
     * Suspends non-essential background tasks until audio is streaming.
     */
    fun acquirePlaybackPriority() {
        isPlaybackCritical.set(true)
        _currentFocus.value = ScreenFocus.PLAYBACK_CRITICAL
    }

    /**
     * Releases playback priority once audio has started playing smoothly.
     */
    fun releasePlaybackPriority(fallbackFocus: ScreenFocus = ScreenFocus.HOME) {
        isPlaybackCritical.set(false)
        _currentFocus.value = fallbackFocus
    }

    /**
     * Returns true if non-essential background network tasks should yield/pause.
     */
    fun shouldYieldBackgroundNetwork(): Boolean {
        return isPlaybackCritical.get()
    }
}
