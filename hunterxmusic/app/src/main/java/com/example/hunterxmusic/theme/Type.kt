package com.example.hunterxmusic.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
  )

/**
 * CyroSonic typography - Syne (variable, ExtraBold) for display headings,
 * Inter for crisp data. System fallback is automatic.
 */
val CryoDisplay = FontFamily(
  androidx.compose.ui.text.font.Font(
    com.example.hunterxmusic.R.font.syne_extrabold,
    weight = FontWeight.ExtraBold
  )
)

val CryoText = FontFamily(
  androidx.compose.ui.text.font.Font(
    com.example.hunterxmusic.R.font.inter_semibold,
    weight = FontWeight.SemiBold
  )
)
