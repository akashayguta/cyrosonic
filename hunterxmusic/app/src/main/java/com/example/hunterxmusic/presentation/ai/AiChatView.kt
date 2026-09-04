package com.example.hunterxmusic.presentation.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hunterxmusic.domain.model.AiModel
import com.example.hunterxmusic.domain.repository.ChatMessage
import com.example.hunterxmusic.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiChatView(
    viewModel: AiChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var showModelSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Smooth auto-scroll when new messages arrive or typing indicator appears (zero jitter during typing)
    LaunchedEffect(state.messages.size, state.isLoading) {
        val totalCount = state.messages.size + if (state.isLoading) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    // Cosmic Theme Tokens (Amethyst Purple + Magenta Rose + Neon Amber Gold)
    val bgCosmic = Color(0xFF090610)
    val cardDarkGlass = Color(0xFF130D20)
    val borderVioletGlow = Color(0xFFA855F7).copy(alpha = 0.25f)
    val textAmethyst = Color(0xFFC084FC)
    val accentFuchsia = Color(0xFFE879F9)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgCosmic)
            .nocturneAurora()
    ) {
        // Volumetric ambient glow in background
        com.example.hunterxmusic.theme.LumeaAuraBackdrop(
            modifier = Modifier.fillMaxSize(),
            primaryColor = Color(0xFF7C3AED),
            secondaryColor = Color(0xFFC026D3),
            intensity = 0.35f
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Top Navigation & Model Selector Header (PINNED AT TOP - NO IME PADDING) ──
            Surface(
                color = Color(0xFF0D0818).copy(alpha = 0.96f),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                shadowElevation = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Interactive Model Selector Pill (Amethyst / Rose Gold Glass)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF24143D), Color(0xFF160D27))
                                )
                            )
                            .border(1.dp, borderVioletGlow, RoundedCornerShape(22.dp))
                            .clickable { showModelSheet = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = state.selectedModel.iconEmoji,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = state.selectedModel.displayName,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Switch Model",
                                    tint = textAmethyst,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val pulseAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(900, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "pulseAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.isLoading) Color(0xFFF59E0B)
                                            else Color(0xFF10B981).copy(alpha = pulseAlpha)
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (state.isLoading) "Thinking..." else "Active",
                                    color = if (state.isLoading) Color(0xFFFBBF24) else Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Clear Chat Button
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = state.messages.isNotEmpty(),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Chat",
                            tint = if (state.messages.isNotEmpty()) Color(0xFFF87171) else Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // ── Main Chat Stream / Empty Hero (Fills Screen & Auto-Scrolls) ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.messages.isEmpty()) {
                    EmptyChatHero(
                        selectedModel = state.selectedModel,
                        onPromptClick = { prompt ->
                            viewModel.sendMessage(prompt)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(state.messages, key = { index, msg -> "$index-${msg.timestamp}" }) { _, msg ->
                            ChatMessageBubble(
                                message = msg,
                                onCopyText = { text ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("CyroSonic AI", text))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // WhatsApp / Human-like Typing Indicator
                        if (state.isLoading) {
                            item(key = "typing_indicator") {
                                HumanTypingIndicator(model = state.selectedModel)
                            }
                        }
                    }
                }
            }

            // ── Bottom Message Input Bar ──
            Surface(
                color = Color(0xFF0D0818).copy(alpha = 0.98f),
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        if (WindowInsets.isImeVisible) WindowInsets.ime else WindowInsets.navigationBars
                    ),
                shadowElevation = 12.dp
            ) {
                val canSend = state.inputText.isNotBlank() && !state.isLoading
                val sendScale by animateFloatAsState(
                    targetValue = if (canSend) 1f else 0.88f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "sendScale"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = { viewModel.onInputTextChanged(it) },
                        placeholder = {
                            Text(
                                "Ask anything about music, chords, lyrics...",
                                color = Color(0xFF7A6B94),
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFC084FC).copy(alpha = 0.7f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color(0xFF181028),
                            unfocusedContainerColor = Color(0xFF181028),
                            cursorColor = Color(0xFFE879F9)
                        ),
                        shape = RoundedCornerShape(26.dp),
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Send,
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (canSend) {
                                    viewModel.sendMessage()
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 110.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (canSend) {
                                viewModel.sendMessage()
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                scaleX = sendScale
                                scaleY = sendScale
                            }
                            .clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))
                                ) else Brush.linearGradient(
                                    listOf(Color(0xFF241638), Color(0xFF160E24))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) Color.White else Color(0xFF5E4E73),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // ── Model Selection Bottom Sheet (Violet/Amethyst Theme) ──
    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            containerColor = Color(0xFF130D22),
            scrimColor = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Text(
                        text = "🧠 Select AI Intelligence",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = "Conversation memory is automatically preserved across model switches.",
                    color = Color(0xFFA594BD),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AiModel.entries.forEach { model ->
                    val isSelected = model == state.selectedModel
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) Color(0xFF7C3AED).copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFFC084FC) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                viewModel.selectModel(model)
                                showModelSheet = false
                                Toast.makeText(context, "Switched to ${model.displayName}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF24153E))
                        ) {
                            Text(text = model.iconEmoji, fontSize = 22.sp)
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.displayName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF7C3AED).copy(alpha = 0.35f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = model.tag,
                                        color = Color(0xFFE879F9),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = model.description,
                                color = Color(0xFFA594BD),
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = Color(0xFFE879F9),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Clear Conversation Confirmation Dialog ──
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Chat Memory?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This will reset the active conversation history and memory.", color = Color(0xFFD4C7E8)) },
            containerColor = Color(0xFF1E1430),
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color(0xFFA594BD))
                }
            }
        )
    }
}

/**
 * Empty Chat Screen with Glowing Amethyst AI Core and Smart Prompts.
 */
@Composable
private fun EmptyChatHero(
    selectedModel: AiModel,
    onPromptClick: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Glowing Futuristic Amethyst / Magenta Core Orb
        val infiniteTransition = rememberInfiniteTransition(label = "core")
        val orbScale by infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orbScale"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = orbScale
                    scaleY = orbScale
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFC084FC).copy(alpha = 0.45f),
                            Color(0xFF7C3AED).copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF8B5CF6), Color(0xFFD946EF), Color(0xFFF43F5E))
                        )
                    )
                    .shadow(16.dp, CircleShape)
            ) {
                Text(text = selectedModel.iconEmoji, fontSize = 28.sp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "CyroSonic AI Neural Hub",
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Powered by ${selectedModel.displayName} • ${selectedModel.tag}",
            color = Color(0xFFE879F9),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Quick Suggestion Chips (Obsidian Glass with Purple/Rose Tint)
        val suggestions = listOf(
            "🌙 Recommend songs for a late-night rainy drive",
            "🎹 Explain chord progression of Lo-Fi beats",
            "✍️ Write poetic lyrics about heartbreak in Arijit's style",
            "🎧 Find rare gems similar to Cigarettes After Sex"
        )

        suggestions.forEach { prompt ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF150D24).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .clickable { onPromptClick(prompt) }
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text(
                    text = prompt,
                    color = Color(0xFFE2D9F3),
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF9D8AB8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Luxury WhatsApp / Instagram formatted message bubble (Amethyst/Magenta Rose theme).
 */
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onCopyText: (String) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }
    var showThought by remember { mutableStateOf(false) }

    if (message.isUser) {
        // User Message Bubble (Right-aligned, Electric Violet to Magenta gradient)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = 18.dp,
                                bottomEnd = 4.dp
                            )
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF7C3AED), Color(0xFF9333EA), Color(0xFFC026D3))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp, end = 4.dp)
                ) {
                    Text(
                        text = timeString,
                        color = Color(0xFF8E7C9E),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "✓✓",
                        color = Color(0xFFF472B6),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        // AI Assistant Message Bubble (Left-aligned, Deep Cosmic Glass)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                // Header badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                ) {
                    Text(
                        text = message.modelName ?: "CyroSonic AI",
                        color = Color(0xFFE879F9),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• $timeString",
                        color = Color(0xFF8E7C9E),
                        fontSize = 10.sp
                    )
                }

                // Expandable Thought / Reasoning Box (Cyber Amber Glass)
                if (!message.thoughtProcess.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF28180A).copy(alpha = 0.7f))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable { showThought = !showThought }
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "💭", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showThought) "Hide Thinking Process" else "View Thinking Process",
                                    color = Color(0xFFFCD34D),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (showThought) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFFFCD34D),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        AnimatedVisibility(visible = showThought) {
                            Text(
                                text = message.thoughtProcess,
                                color = Color(0xFFFEF3C7),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                // AI Response Body
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 18.dp
                            )
                        )
                        .background(Color(0xFF140D22))
                        .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.2f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = message.text,
                            color = Color(0xFFF3EEFA),
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom Actions: Copy
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable { onCopyText(message.text) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = Color(0xFFA594BD),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Copy",
                                    color = Color(0xFFA594BD),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Human-like Animated Typing Wave (3 Bouncing Magenta/Violet Dots).
 */
@Composable
private fun HumanTypingIndicator(model: AiModel) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 140, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF140D22))
            .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text = model.iconEmoji, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${model.displayName} is thinking",
            color = Color(0xFFA594BD),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(10.dp))

        // 3 Bouncing Dots
        Box(
            modifier = Modifier
                .offset(y = dot1Offset.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFFE879F9))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .offset(y = dot2Offset.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFFE879F9))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .offset(y = dot3Offset.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFFE879F9))
        )
    }
}
