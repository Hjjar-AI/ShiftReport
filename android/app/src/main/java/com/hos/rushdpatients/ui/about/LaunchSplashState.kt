package com.hos.rushdpatients.ui.about

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay

/** Monotonic launch deadline and visibility survive recreation; touch never pauses them. */
internal class LaunchSplashState(private val showing: MutableState<Boolean>, val deadline: Long) {
    private var held = false
    val visible: Boolean get() = showing.value
    fun press() { held = true }
    fun release() { held = false; tick() }
    // Retained for debug callers; the splash no longer displays a countdown.
    val remainingSeconds: Int get() =
        (((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999L) / 1000L).toInt()
    fun skip() { showing.value = false }
    internal fun tick(): Long {
        val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        if (remaining == 0L && !held) showing.value = false
        return remaining
    }
}

@Composable
internal fun rememberLaunchSplashState(): LaunchSplashState {
    val showing = rememberSaveable { mutableStateOf(true) }
    val deadline by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime() + 5000L) }
    val state = remember(showing, deadline) { LaunchSplashState(showing, deadline) }
    LaunchedEffect(state, state.visible) {
        while (state.visible) {
            val remaining = state.tick()
            if (!state.visible) break
            if (remaining == 0L) break // Release will dismiss an expired hold.
            delay(remaining)
        }
    }
    return state
}
