package com.example.hunterxmusic.presentation.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import coil.compose.AsyncImage
import com.example.hunterxmusic.data.player.PlaybackState
import com.example.hunterxmusic.theme.*

/**
 * Integrated Docked Bottom Mini Player Bar.
 * Designed to feel naturally anchored into the navigation shell rather than
 * floating as a disconnected widget. Supports both mobile single-row layout
 * and tablet/wide-screen spacious multi-control layout with fluid gestures.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onDoubleClickLike: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val track = playbackState.currentTrack ?: return
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    // Smooth continuous progress fraction
    val targetProgress = if (playbackState.durationMs > 0) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "MiniPlayerProgress"
    )

    // Gesture handling: density-aware swipe left for next, right for previous with spring physics
    val swipeThresholdPx = with(LocalDensity.current) { 70.dp.toPx() }
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragAccumX.coerceIn(-140f, 140f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "dragOffset"
    )
    val likeAction = rememberUpdatedState(onDoubleClickLike)
    val tapAction = rememberUpdatedState(onTap)

    // Seamless, dock-anchored container sitting directly above the nav bar
    Surface(
        color = HunterSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                translationX = animatedDragOffset
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))
                ),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumX = 0f },
                    onDragEnd = {
                        if (dragAccumX < -swipeThresholdPx) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNext()
                        } else if (dragAccumX > swipeThresholdPx && onPrevious != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPrevious.invoke()
                        }
                        dragAccumX = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragAccumX += dragAmount
                }
            }
            .nClick(pressedScale = 0.99f, onClick = onTap)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Hairline Top Gradient Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF38BDF8),
                                    Color(0xFF7DD3FC),
                                    Color.White
                                )
                            )
                        )
                )
            }

            // 2. Player Row Content
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                // Artwork thumbnail with subtle breathing & rounded corners
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HunterSurfaceVariant)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .nBreathing(isPlaying = playbackState.isPlaying, maxScale = 1.03f)
                        .pointerInput(track.id) {
                            detectTapGestures(
                                onTap = { tapAction.value() },
                                onDoubleTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    likeAction.value?.invoke()
                                }
                            )
                        }
                ) {
                    AsyncImage(
                        model = track.albumArtUrl,
                        contentDescription = track.title,
                        error = rememberVectorPainter(Icons.Default.MusicNote),
                        fallback = rememberVectorPainter(Icons.Default.MusicNote),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title + Artist with marquee for long titles
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = track.title,
                        color = HunterTextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Spacer(modifier = Modifier.height(1.5.dp))
                    Text(
                        text = track.artist,
                        color = HunterTextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Control Buttons Group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                ) {
                    // Like button
                    if (onDoubleClickLike != null) {
                        IconButton(
                            onClick = onDoubleClickLike,
                            modifier = Modifier.size(34.dp)
                        ) {
                            AnimatedHeartIcon(
                                isLiked = track.isLiked,
                                size = 20.dp
                            )
                        }
                    }

                    // Previous button on wide screens
                    if (isWideScreen && onPrevious != null) {
                        IconButton(
                            onClick = onPrevious,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = HunterTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Play / Pause sleek morphing glass circle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .nClick(pressedScale = 0.90f, onClick = onPlayPause)
                    ) {
                        AnimatedPlayPauseIcon(
                            isPlaying = playbackState.isPlaying,
                            tint = Color.Black,
                            size = 20.dp
                        )
                    }

                    // Skip Next button
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
