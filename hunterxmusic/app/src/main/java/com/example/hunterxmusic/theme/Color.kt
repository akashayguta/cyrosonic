package com.example.hunterxmusic.theme

import androidx.compose.ui.graphics.Color

// Pure Pitch Black / AMOLED Dark Palette (Masculine, Sleek, Minimalist)
// Now dynamic via ThemeManager — keeps backward compat while allowing skins & light mode
val HunterBackground: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFFF8F8FA) else com.example.hunterxmusic.data.local.ThemeManager.currentSkin.background
val HunterSurface: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFFFFFFFF) else com.example.hunterxmusic.data.local.ThemeManager.currentSkin.surface
val HunterSurfaceVariant: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFFF1F1F3) else com.example.hunterxmusic.data.local.ThemeManager.currentSkin.surfaceVariant
val HunterCard: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFFFFFFFF) else com.example.hunterxmusic.data.local.ThemeManager.currentSkin.card

// Minimalist Refined Accents (Crisp White / Platinum Silver / Subtle Ice Slate)
val AeroCyan: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFF18181B) else Color(0xFFFFFFFF)
val AeroBlue: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFF3F3F46) else Color(0xFFE4E4E7)
val AeroPurple: Color get() = Color(0xFFA1A1AA)
val AeroPink: Color get() = Color(0xFFFF3366)
val AeroGold: Color get() = Color(0xFFE5B869)
val AeroEmerald: Color get() = Color(0xFF10B981)
val AeroDarkGlass: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFFF4F4F5) else Color(0xFF0E0E12)
val AeroBorderGlass: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0x1A000000) else Color(0x1FFFFFFF)

// Accent colors
val HunterAccent: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFF18181B) else com.example.hunterxmusic.data.local.ThemeManager.currentSkin.accent
val HunterAccentDim: Color get() = Color(0xFFA1A1AA)
val HunterBrand: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFF18181B) else Color(0xFFFFFFFF)
val HunterBrandDim: Color get() = Color(0xFF71717A)

// Text
val HunterTextPrimary: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFF18181B) else Color(0xFFFFFFFF)
val HunterTextSecondary: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFF52525B) else Color(0xFFA1A1AA)
val HunterTextHint: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFFA1A1AA) else Color(0xFF52525B)

// Nav
val HunterNavSelected: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFF18181B) else Color(0xFFFFFFFF)
val HunterNavUnselected: Color get() = Color(0xFF52525B)
val HunterNavIndicator: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFFE4E4E7) else com.example.hunterxmusic.data.local.ThemeManager.currentSkin.navIndicator

// Player
val HunterPlayerBar: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFFF4F4F5) else Color(0xFF0A0A0D)
val HunterSeekActive: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFF18181B) else Color(0xFFFFFFFF)
val HunterSeekInactive: Color get() = if (com.example.hunterxmusic.data.local.ThemeManager.isLight) Color(0xFFE4E4E7) else Color(0xFF27272A)

// Status
val HunterError = Color(0xFFEF4444)
val HunterLiked = Color(0xFFFF3366)

// Legacy
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
