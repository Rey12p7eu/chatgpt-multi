
package com.chatgpt.multisession.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@Composable
fun SwipeAccountSwitcher(
    modifier: Modifier = Modifier,
    threshold: Float = 120f,
    onSwipeLeft: () -> Unit, // next
    onSwipeRight: () -> Unit, // previous
    content: @Composable () -> Unit
) {
    var drag by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag) > threshold) {
                            if (drag < 0) onSwipeLeft() else onSwipeRight()
                        }
                        drag = 0f
                    },
                    onHorizontalDrag = { _, amount ->
                        drag += amount
                    }
                )
            }
    ) {
        content()
    }
}
