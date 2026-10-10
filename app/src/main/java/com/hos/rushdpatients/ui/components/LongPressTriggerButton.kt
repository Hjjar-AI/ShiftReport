package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A button that fires [onClick] on a normal tap and [onHoldTriggered] when the
 * user keeps the pointer down for [holdDurationMs] milliseconds.
 *
 * A faint progress overlay fills the button during the hold so the user knows
 * the gesture is being recognized.
 */
@Composable
fun LongPressTriggerButton(
    text: String,
    holdDurationMs: Long,
    onClick: () -> Unit,
    onHoldTriggered: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val bgColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val fgColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val overlayColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .heightIn(min = UiSpacing.touchTarget)
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .pointerInput(enabled, holdDurationMs) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    progress = 0f
                    var triggered = false
                    val holdJob = scope.launch {
                        val start = System.currentTimeMillis()
                        while (!triggered) {
                            val elapsed = System.currentTimeMillis() - start
                            val p = (elapsed.toFloat() / holdDurationMs).coerceIn(0f, 1f)
                            progress = p
                            if (elapsed >= holdDurationMs) {
                                triggered = true
                                progress = 0f
                                onHoldTriggered()
                                return@launch
                            }
                            delay(50L)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    holdJob.cancel()
                    progress = 0f
                    if (up != null && !triggered) {
                        onClick()
                    }
                }
            }
            .padding(UiPadding.content),
        contentAlignment = Alignment.Center
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(overlayColor.copy(alpha = 0.5f * progress))
            )
        }
        Text(text, color = fgColor, style = MaterialTheme.typography.labelLarge)
    }
}