package com.example.hunterxmusic.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.theme.HunterBackground
import com.example.hunterxmusic.theme.HunterBrand
import com.example.hunterxmusic.theme.HunterLiked
import kotlinx.coroutines.launch

@Composable
fun SwipeableTrackRow(
    track: Track,
    onSwipeLeft: () -> Unit, // delete
    onSwipeRight: () -> Unit, // enqueue
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val target = offsetX.value
                            if (target < -120f) {
                                onSwipeLeft()
                            } else if (target > 120f) {
                                onSwipeRight()
                            }
                            offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    }
                )
            }
    ) {
        // Background under the swiped row
        val isSwipingRight = offsetX.value > 0f
        val isSwipingLeft = offsetX.value < 0f
        
        if (isSwipingRight || isSwipingLeft) {
            val bgTint = if (isSwipingRight) HunterBrand.copy(alpha = 0.15f) else HunterLiked.copy(alpha = 0.15f)
            val icon = if (isSwipingRight) Icons.Default.QueueMusic else Icons.Default.Delete
            val iconTint = if (isSwipingRight) HunterBrand else HunterLiked
            val alignment = if (isSwipingRight) Alignment.CenterStart else Alignment.CenterEnd

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgTint)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Foreground content (the row itself)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .background(HunterBackground)
        ) {
            content()
        }
    }
}
