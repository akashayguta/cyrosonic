package com.example.hunterxmusic.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hunterxmusic.core.audio.AudioFxManager
import com.example.hunterxmusic.core.audio.STUDIO_PRESETS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    audioFxManager: AudioFxManager,
    onDismiss: () -> Unit
) {
    val isEnabled by audioFxManager.isEnabled.collectAsState()
    val bassBoost by audioFxManager.bassBoostLevel.collectAsState()
    val virtualizer by audioFxManager.virtualizerLevel.collectAsState()
    val selectedPreset by audioFxManager.selectedPreset.collectAsState()
    val bandLevels by audioFxManager.bandLevels.collectAsState()
    val frequencies = remember { audioFxManager.getBandFrequencies() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0B18),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7C3AED).copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Studio Pro Equalizer & 3D",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "10-Band Parametric • Spatial Virtualizer",
                            color = Color(0xFFA594BD),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { audioFxManager.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFC084FC)
                    )
                )
            }

            // Presets Horizontal Carousel
            Text(
                text = "STUDIO PRESETS",
                color = Color(0xFF8E7C9E),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            ) {
                items(STUDIO_PRESETS) { preset ->
                    val isSelected = selectedPreset == preset.name
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Color(0xFF7C3AED).copy(alpha = 0.35f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFC084FC) else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = isEnabled) { audioFxManager.applyPreset(preset.name) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = preset.name,
                            color = if (isSelected) Color(0xFFE879F9) else Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Dual Knobs: Bass Boost + 3D Spatial Virtualizer
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            ) {
                // Bass Boost Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF191026))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bass Boost", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${(bassBoost / 10)}%", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bassBoost.toFloat(),
                        onValueChange = { audioFxManager.setBassBoost(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFF59E0B),
                            activeTrackColor = Color(0xFFF59E0B),
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }

                // 3D Spatial Virtualizer Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF191026))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SurroundSound, null, tint = Color(0xFF06B6D4), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("3D Spatial", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${(virtualizer / 10)}%", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = virtualizer.toFloat(),
                        onValueChange = { audioFxManager.setVirtualizer(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF06B6D4),
                            activeTrackColor = Color(0xFF06B6D4),
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }

            // 5-Band Sliders
            Text(
                text = "FREQUENCY BANDS",
                color = Color(0xFF8E7C9E),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF150D22))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                bandLevels.forEachIndexed { index, levelMb ->
                    val label = frequencies.getOrElse(index) { "Band $index" }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${if (levelMb > 0) "+" else ""}${levelMb / 100}dB",
                            color = Color(0xFFC084FC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Band Slider
                        Slider(
                            value = levelMb.toFloat(),
                            onValueChange = { audioFxManager.setBandLevel(index, it.toInt()) },
                            valueRange = -1000f..1000f,
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFC084FC),
                                activeTrackColor = Color(0xFF7C3AED),
                                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .height(120.dp)
                                .graphicsLayer {
                                    rotationZ = 270f
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = Color(0xFFA594BD),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
