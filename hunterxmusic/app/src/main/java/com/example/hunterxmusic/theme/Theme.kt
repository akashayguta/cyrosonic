package com.example.hunterxmusic.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.hunterxmusic.data.local.ThemeManager
import com.example.hunterxmusic.data.local.ThemePrefs

private fun hunterDarkScheme() = darkColorScheme(
    primary = HunterAccent,
    onPrimary = Color.Black,
    primaryContainer = HunterNavIndicator,
    onPrimaryContainer = HunterAccent,
    secondary = HunterBrand,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A3D33),
    onSecondaryContainer = HunterBrand,
    tertiary = Pink80,
    background = HunterBackground,
    onBackground = HunterTextPrimary,
    surface = HunterSurface,
    onSurface = HunterTextPrimary,
    surfaceVariant = HunterSurfaceVariant,
    onSurfaceVariant = HunterTextSecondary,
    outline = Color(0xFF3D3843),
    outlineVariant = Color(0xFF2D2933)
)

private fun hunterLightScheme() = lightColorScheme(
    primary = Color(0xFF18181B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF27272A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = Purple40,
    background = Color(0xFFF8F8FA),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF1F1F3),
    onSurfaceVariant = Color(0xFF52525B),
    outline = Color(0xFFE4E4E7),
    outlineVariant = Color(0xFFF1F1F3)
)

@Composable
fun HUNTERxMUSICTheme(
    content: @Composable () -> Unit,
) {
    val isLight = ThemeManager.isLight
    val scheme = if (isLight) hunterLightScheme() else hunterDarkScheme()
    
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val insets = androidx.core.view.WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = isLight
            insets.isAppearanceLightNavigationBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
