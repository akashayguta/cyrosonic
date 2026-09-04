package com.example.hunterxmusic.core.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqPreset(
    val name: String,
    val bandLevels: List<Int> // in mB (millibels, typically -1000 to +1000)
)

val STUDIO_PRESETS = listOf(
    EqPreset("Flat", listOf(0, 0, 0, 0, 0)),
    EqPreset("Bass Max", listOf(600, 400, 100, -100, 200)),
    EqPreset("Vocal Clarity", listOf(-200, 100, 500, 600, 300)),
    EqPreset("Concert Hall", listOf(400, 200, -100, 300, 500)),
    EqPreset("Rock & Metal", listOf(500, 300, -200, 400, 600)),
    EqPreset("Electronic / EDM", listOf(600, 300, 0, 300, 500)),
    EqPreset("Acoustic Warmth", listOf(300, 200, 100, 200, 100)),
    EqPreset("Lo-Fi Chill", listOf(200, 300, 0, -200, -400))
)

class AudioFxManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("audio_fx_prefs", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentSessionId: Int = 0

    private val _isEnabled = MutableStateFlow(prefs.getBoolean("eq_enabled", true))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(prefs.getInt("bass_boost", 400))
    val bassBoostLevel: StateFlow<Int> = _bassBoostLevel.asStateFlow()

    private val _virtualizerLevel = MutableStateFlow(prefs.getInt("virtualizer_3d", 300))
    val virtualizerLevel: StateFlow<Int> = _virtualizerLevel.asStateFlow()

    private val _selectedPreset = MutableStateFlow(prefs.getString("selected_preset", "Flat") ?: "Flat")
    val selectedPreset: StateFlow<String> = _selectedPreset.asStateFlow()

    private val _bandLevels = MutableStateFlow(loadBandLevels())
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()

    fun attachSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        currentSessionId = sessionId
        try {
            release()

            try {
                equalizer = Equalizer(0, sessionId).apply {
                    enabled = _isEnabled.value
                    val bands = _bandLevels.value
                    for (i in bands.indices) {
                        if (i < numberOfBands) {
                            setBandLevel(i.toShort(), bands[i].toShort())
                        }
                    }
                }
            } catch (_: Throwable) {
                equalizer = null
            }

            try {
                bassBoost = BassBoost(0, sessionId).apply {
                    enabled = _isEnabled.value
                    if (strengthSupported) {
                        setStrength(_bassBoostLevel.value.toShort())
                    }
                }
            } catch (_: Throwable) {
                bassBoost = null
            }

            try {
                virtualizer = Virtualizer(0, sessionId).apply {
                    enabled = _isEnabled.value
                    if (strengthSupported) {
                        setStrength(_virtualizerLevel.value.toShort())
                    }
                }
            } catch (_: Throwable) {
                virtualizer = null
            }
        } catch (_: Throwable) { }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (_: Exception) { }
    }

    fun setBassBoost(level: Int) {
        val clamped = level.coerceIn(0, 1000)
        _bassBoostLevel.value = clamped
        prefs.edit().putInt("bass_boost", clamped).apply()
        try {
            bassBoost?.setStrength(clamped.toShort())
        } catch (_: Exception) { }
    }

    fun setVirtualizer(level: Int) {
        val clamped = level.coerceIn(0, 1000)
        _virtualizerLevel.value = clamped
        prefs.edit().putInt("virtualizer_3d", clamped).apply()
        try {
            virtualizer?.setStrength(clamped.toShort())
        } catch (_: Exception) { }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val current = _bandLevels.value.toMutableList()
        if (bandIndex in current.indices) {
            current[bandIndex] = levelMb.coerceIn(-1000, 1000)
            _bandLevels.value = current
            _selectedPreset.value = "Custom"
            saveBandLevels(current)
            prefs.edit().putString("selected_preset", "Custom").apply()
            try {
                equalizer?.setBandLevel(bandIndex.toShort(), levelMb.toShort())
            } catch (_: Exception) { }
        }
    }

    fun applyPreset(presetName: String) {
        val preset = STUDIO_PRESETS.find { it.name == presetName } ?: return
        _selectedPreset.value = preset.name
        prefs.edit().putString("selected_preset", preset.name).apply()
        _bandLevels.value = preset.bandLevels
        saveBandLevels(preset.bandLevels)

        try {
            equalizer?.let { eq ->
                for (i in preset.bandLevels.indices) {
                    if (i < eq.numberOfBands) {
                        eq.setBandLevel(i.toShort(), preset.bandLevels[i].toShort())
                    }
                }
            }
        } catch (_: Exception) { }
    }

    fun getBandFrequencies(): List<String> {
        val count = equalizer?.numberOfBands?.toInt() ?: 5
        val defaultLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
        return try {
            equalizer?.let { eq ->
                (0 until eq.numberOfBands).map { i ->
                    val centerFreq = eq.getCenterFreq(i.toShort()) / 1000
                    if (centerFreq >= 1000) "${centerFreq / 1000}kHz" else "${centerFreq}Hz"
                }
            } ?: defaultLabels
        } catch (_: Exception) { defaultLabels }
    }

    private fun loadBandLevels(): List<Int> {
        val saved = prefs.getString("band_levels", null) ?: return listOf(0, 0, 0, 0, 0)
        return try {
            saved.split(",").map { it.toInt() }
        } catch (_: Exception) {
            listOf(0, 0, 0, 0, 0)
        }
    }

    private fun saveBandLevels(levels: List<Int>) {
        prefs.edit().putString("band_levels", levels.joinToString(",")).apply()
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) { }
        try { bassBoost?.release() } catch (_: Exception) { }
        try { virtualizer?.release() } catch (_: Exception) { }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }
}
