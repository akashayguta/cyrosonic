package com.example.hunterxmusic.presentation.party

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.hunterxmusic.data.party.ListeningPartyManager
import com.example.hunterxmusic.data.party.PartyConnectionStatus
import com.example.hunterxmusic.data.player.MusicPlayerManager

@Composable
fun ListeningPartyDialog(
    playerManager: MusicPlayerManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val partyState by ListeningPartyManager.partyState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Host, 1 = Join
    var inputCode by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // Pulse animation for live sync status
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0D111A))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(Color(0xFF00F2FE).copy(alpha = 0.6f), Color(0xFF8B5CF6).copy(alpha = 0.4f))),
                    RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F2FE).copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = Color(0xFF00F2FE),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Listening Party",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (partyState.status == PartyConnectionStatus.CONNECTED && !partyState.roomCode.isNullOrBlank()) {
                    // ACTIVE ROOM VIEW
                    val roomCode = partyState.roomCode ?: ""
                    val isHost = partyState.isHost

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF131A29))
                            .border(1.dp, Color(0xFF00F2FE).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(vertical = 20.dp, horizontal = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isHost) "HOSTING PARTY • LIVE" else "CONNECTED • SYNCED",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = roomCode,
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp
                            )

                            Text(
                                text = "Room Code • ${partyState.listenersCount} Listeners",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Copy Code Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("CyroSonic Room Code", roomCode))
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    Toast.makeText(context, "Room code $roomCode copied!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Code", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Share Link Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6))))
                                .clickable {
                                    val link = "https://cyrosonic.com/party/$roomCode"
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "🎧 Join my live Listening Party on CyroSonic!\nRoom Code: $roomCode\nJoin link: $link"
                                        )
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Invite to Listening Party").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Invite Link", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Leave Party Button
                    TextButton(
                        onClick = {
                            ListeningPartyManager.leaveParty()
                            Toast.makeText(context, "Left Listening Party", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isHost) "End Party for Everyone" else "Leave Party",
                            color = Color(0xFFFF5555),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // HOST / JOIN SELECTION
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 0) Color(0xFF00F2FE) else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "Host Party",
                                color = if (selectedTab == 0) Color.Black else Color(0xFFA1A1AA),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 1) Color(0xFF00F2FE) else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "Join Room",
                                color = if (selectedTab == 1) Color.Black else Color(0xFFA1A1AA),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (selectedTab == 0) {
                        // HOST TAB
                        Text(
                            text = "Broadcast your music live with friends in other cities. ExoPlayer tracks will sync down to the millisecond.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSubmitting) Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                                    else Brush.linearGradient(listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6)))
                                )
                                .clickable(enabled = !isSubmitting) {
                                    isSubmitting = true
                                    ListeningPartyManager.createParty(playerManager) { success, codeOrErr ->
                                        isSubmitting = false
                                        if (success) {
                                            Toast.makeText(context, "Room $codeOrErr created! Share code with friends.", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Error: $codeOrErr", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate 6-Digit Room", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    } else {
                        // JOIN TAB
                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { if (it.length <= 8) inputCode = it.uppercase() },
                            placeholder = { Text("Enter 6-digit Code (e.g. 849201)", color = Color(0xFF64748B), fontSize = 14.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (inputCode.isNotBlank() && !isSubmitting) {
                                    isSubmitting = true
                                    ListeningPartyManager.joinParty(inputCode, playerManager) { success, msg ->
                                        isSubmitting = false
                                        if (!success) Toast.makeText(context, "Failed: $msg", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00F2FE),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color(0xFF131A29),
                                unfocusedContainerColor = Color(0xFF131A29)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSubmitting || inputCode.isBlank()) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f)))
                                    else Brush.linearGradient(listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6)))
                                )
                                .clickable(enabled = !isSubmitting && inputCode.isNotBlank()) {
                                    isSubmitting = true
                                    ListeningPartyManager.joinParty(inputCode, playerManager) { success, msg ->
                                        isSubmitting = false
                                        if (success) {
                                            Toast.makeText(context, "Connected to Listening Party!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed: $msg", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = "Connect to Party",
                                    color = if (inputCode.isBlank()) Color(0xFF64748B) else Color.Black,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
