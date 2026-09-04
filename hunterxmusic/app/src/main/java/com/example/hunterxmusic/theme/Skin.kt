package com.example.hunterxmusic.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

/**
 * CyroSonic skins as an ENUM.
 *
 * HISTORY: this was a sealed class with `object` singletons. R8 full-mode
 * (AGP 9 default) horizontal class merging produced NULL INSTANCE fields for
 * those objects in release builds — the Settings screen then crashed with
 * "Skin.getId() on a null object reference" the moment LazyColumn prefetch
 * composed the Themes section mid-scroll. Kotlin enums are immune: their
 * constants are real enum instances protected by values()/valueOf() semantics
 * that R8 never breaks (see the enum keep rule in proguard-rules.pro).
 */
enum class Skin(
    val id: String,
    val displayName: String,
    val description: String,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val card: Color,
    val navIndicator: Color,
    val accent: Color,
    val gradient: List<Color>,
    val isLightSkin: Boolean = false
) {
    MIDNIGHT(
        id = "midnight",
        displayName = "Midnight",
        description = "Pure AMOLED black • classic",
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0C),
        surfaceVariant = Color(0xFF141417),
        card = Color(0xFF111114),
        navIndicator = Color(0xFF18181B),
        accent = Color(0xFFFFFFFF),
        gradient = listOf(Color(0xFF000000), Color(0xFF0A0A0C), Color(0xFF000000))
    ),
    OCEAN(
        id = "ocean",
        displayName = "Ocean",
        description = "Deep navy • calm & focused",
        background = Color(0xFF070E1E),
        surface = Color(0xFF0F1E3A),
        surfaceVariant = Color(0xFF16264A),
        card = Color(0xFF0D1B36),
        navIndicator = Color(0xFF15294E),
        accent = Color(0xFF7DD3FC),
        gradient = listOf(Color(0xFF070E1E), Color(0xFF0A1A33), Color(0xFF070E1E))
    ),
    SUNSET(
        id = "sunset",
        displayName = "Sunset",
        description = "Warm wine • intimate",
        background = Color(0xFF1A0F14),
        surface = Color(0xFF2A1420),
        surfaceVariant = Color(0xFF3A1A2E),
        card = Color(0xFF24111C),
        navIndicator = Color(0xFF2E1426),
        accent = Color(0xFFFF7E7E),
        gradient = listOf(Color(0xFF1A0F14), Color(0xFF2D1424), Color(0xFF1A0F14))
    ),
    FOREST(
        id = "forest",
        displayName = "Forest",
        description = "Deep emerald • organic chill",
        background = Color(0xFF0B1A13),
        surface = Color(0xFF132A1D),
        surfaceVariant = Color(0xFF1B3A26),
        card = Color(0xFF0F2418),
        navIndicator = Color(0xFF173022),
        accent = Color(0xFF6EE7B7),
        gradient = listOf(Color(0xFF0B1A13), Color(0xFF132A1D), Color(0xFF0B1A13))
    ),
    VOID(
        id = "void",
        displayName = "Void",
        description = "Black + violet haze • cosmic",
        background = Color(0xFF0F0B18),
        surface = Color(0xFF1A1430),
        surfaceVariant = Color(0xFF241E3D),
        card = Color(0xFF16102A),
        navIndicator = Color(0xFF1E1738),
        accent = Color(0xFFA5B4FC),
        gradient = listOf(Color(0xFF0F0B18), Color(0xFF1A1430), Color(0xFF0F0B18))
    ),
    LIGHT(
        id = "light",
        displayName = "Light",
        description = "Clean white • airy & minimal",
        background = Color(0xFFF8F8FA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F1F3),
        card = Color(0xFFFFFFFF),
        navIndicator = Color(0xFFE4E4E7),
        accent = Color(0xFF18181B),
        gradient = listOf(Color(0xFFF8F8FA), Color(0xFFFFFFFF), Color(0xFFF8F8FA)),
        isLightSkin = true
    );

    fun backgroundBrush(): Brush = Brush.verticalGradient(gradient)

    companion object {
        val all: List<Skin> = entries.toList()

        fun fromId(id: String): Skin =
            all.firstOrNull { it.id == id } ?: MIDNIGHT
    }
}