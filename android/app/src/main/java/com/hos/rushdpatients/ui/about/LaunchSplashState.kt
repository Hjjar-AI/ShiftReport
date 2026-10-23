package com.hos.rushdpatients.ui.about

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay

/** Monotonic launch deadline and visibility survive recreation; a physical hold does not. */
internal class LaunchSplashState(private val showing: MutableState<Boolean>, val deadline: Long) {
    val visible: Boolean get() = showing.value
    var held by mutableStateOf(false)
        private set
    var remainingSeconds by mutableIntStateOf(secondsRemaining())
        internal set
    private fun secondsRemaining() = (((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999L) / 1000L).toInt()
    fun setHeld(value: Boolean) {
        held = value
        if (!value && SystemClock.elapsedRealtime() >= deadline) showing.value = false
    }
    fun skip() { showing.value = false }
    internal fun tick(): Long {
        val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        remainingSeconds = ((remaining + 999L) / 1000L).toInt()
        if (remaining == 0L && !held) showing.value = false
        return remaining
    }
}

@Composable
internal fun rememberLaunchSplashState(): LaunchSplashState {
    val showing = rememberSaveable { mutableStateOf(true) }
    val deadline by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime() + 5000L) }
    val state = remember(showing, deadline) { LaunchSplashState(showing, deadline) }
    LaunchedEffect(state, state.visible, state.held) {
        while (state.visible) {
            val remaining = state.tick()
            if (!state.visible) break
            delay(if (remaining == 0L) 250L else minOf(remaining, 250L))
        }
    }
    return state
}
