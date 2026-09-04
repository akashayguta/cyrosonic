package com.example.hunterxmusic.data.local

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.hunterxmusic.theme.Skin

class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    var skin: Skin
        get() = Skin.fromId(prefs.getString(KEY_SKIN, Skin.MIDNIGHT.id) ?: Skin.MIDNIGHT.id)
        set(value) = prefs.edit().putString(KEY_SKIN, value.id).apply()

    var isLightMode: Boolean
        get() = prefs.getBoolean(KEY_LIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_LIGHT, value).apply()

    var useSystemTheme: Boolean
        get() = prefs.getBoolean(KEY_SYSTEM, false)
        set(value) = prefs.edit().putBoolean(KEY_SYSTEM, value).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC, value).apply()

    var reduceMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        set(value) = prefs.edit().putBoolean(KEY_REDUCE_MOTION, value).apply()

    companion object {
        private const val KEY_SKIN = "skin_id"
        private const val KEY_LIGHT = "is_light"
        private const val KEY_SYSTEM = "use_system"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
    }
}

// Global observable skin state for Compose
object ThemeManager {
    var currentSkin: Skin by mutableStateOf<Skin>(Skin.MIDNIGHT)
    var isLight by mutableStateOf(false)
    var useSystem by mutableStateOf(false)
    var dynamicColor by mutableStateOf(true)
    var reduceMotion by mutableStateOf(false)

    fun load(prefs: ThemePrefs, systemIsDark: Boolean) {
        currentSkin = prefs.skin
        useSystem = prefs.useSystemTheme
        isLight = if (useSystem) !systemIsDark else prefs.isLightMode
        dynamicColor = prefs.dynamicColor
        reduceMotion = prefs.reduceMotion
    }

    fun setReduceMotion(enabled: Boolean, prefs: ThemePrefs) {
        if (reduceMotion == enabled) return
        reduceMotion = enabled
        prefs.reduceMotion = enabled
    }

    fun setDynamicColor(enabled: Boolean, prefs: ThemePrefs) {
        if (dynamicColor == enabled) return
        dynamicColor = enabled
        prefs.dynamicColor = enabled
    }

    fun setSkin(skin: Skin, prefs: ThemePrefs) {
        if (currentSkin.id == skin.id && isLight == skin.isLightSkin) return
        currentSkin = skin
        prefs.skin = skin
        // Auto switch light flag for light skins
        if (skin.isLightSkin) {
            isLight = true
            prefs.isLightMode = true
        }
    }

    fun setLightMode(light: Boolean, prefs: ThemePrefs) {
        if (isLight == light && !useSystem) return
        isLight = light
        prefs.isLightMode = light
        useSystem = false
        prefs.useSystemTheme = false
    }
}
