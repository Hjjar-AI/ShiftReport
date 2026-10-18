package com.hos.rushdpatients.ui.components

import androidx.compose.ui.draw.clip

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Native button activation plus the existing optional timed pointer hold. */
@Composable
fun LongPressTriggerButton(text: String, holdDurationMs: Long, onClick: () -> Unit,
    onHoldTriggered: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val active = enabled && LocalFieldEnabled.current
    var progress by remember { mutableFloatStateOf(0f) }
    val holdAction by rememberUpdatedState(onHoldTriggered)
    Box(modifier) {
        AppButton(onClick = onClick, enabled = active, modifier = Modifier.pointerInput(active, holdDurationMs) {
            if (!active) return@pointerInput
            coroutineScope {
                val gestureScope = this
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var triggered = false
                    val holdJob = gestureScope.launch {
                        val start = SystemClock.uptimeMillis()
                        while (true) {
                            val elapsed = SystemClock.uptimeMillis() - start
                            progress = (elapsed.toFloat() / holdDurationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                            if (elapsed >= holdDurationMs) {
                                triggered = true
                                progress = 0f
                                holdAction()
                                break
                            }
                            delay(50L)
                        }
                    }
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            // Consume release before the native button sees it after a successful hold.
                            if (triggered) event.changes.forEach { it.consume() }
                            val outside = pointer != null && (pointer.position.x < 0 || pointer.position.y < 0 ||
                                pointer.position.x > size.width || pointer.position.y > size.height)
                            if (pointer == null || !pointer.pressed || outside || pointer.isConsumed && !triggered) break
                            if (!triggered && awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) break
                        } while (true)
                    } finally {
                        holdJob.cancel()
                        progress = 0f
                    }
                }
            }
        }) { Text(text) }
        if (progress > 0f) Box(Modifier.matchParentSize().clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = .15f * progress)))
    }
}
