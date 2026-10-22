package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hos.rushdpatients.ui.theme.LocalUiPreferences

/** Section jumps respect local reduced motion; ordinary user scrolling is unaffected. */
@Composable
fun rememberUiScrollTo(scroll: ScrollState): suspend (Int) -> Unit {
    val reduced = LocalUiPreferences.current.reducedMotion
    return remember(scroll, reduced) {
        { value ->
            if (reduced) scroll.scrollTo(value) else scroll.animateScrollTo(value)
            Unit
        }
    }
}
