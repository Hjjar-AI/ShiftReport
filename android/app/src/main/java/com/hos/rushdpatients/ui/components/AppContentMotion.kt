package com.hos.rushdpatients.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.ui.theme.LocalUiPreferences
import com.hos.rushdpatients.ui.theme.UiMotion

enum class ContentEntrance { FADE, DETAILS, REPORT_STEP }

/** Animate the current content in place, without composing an outgoing form or action twice. */
@Composable
fun Modifier.appContentEntrance(
    target: Any?,
    entrance: ContentEntrance = ContentEntrance.FADE,
    stepOrder: Int = 0
): Modifier {
    val reduced = LocalUiPreferences.current.reducedMotion
    val previousOrder = remember { intArrayOf(stepOrder) }
    val forward = remember(target, stepOrder) {
        (stepOrder >= previousOrder[0]).also { previousOrder[0] = stepOrder }
    }
    val progress = remember(target, entrance) { Animatable(if (reduced) 1f else 0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val distance = with(LocalDensity.current) {
        when (entrance) {
            ContentEntrance.FADE -> 0.dp
            ContentEntrance.DETAILS -> 8.dp
            ContentEntrance.REPORT_STEP -> 24.dp
        }.toPx()
    }
    LaunchedEffect(progress, reduced) {
        if (reduced) progress.snapTo(1f)
        else progress.animateTo(1f, tween(
            if (entrance == ContentEntrance.REPORT_STEP) UiMotion.reportStepMillis else UiMotion.contentFadeMillis
        ))
    }
    return graphicsLayer {
        val fraction = if (reduced) 1f else progress.value
        alpha = fraction
        translationY = if (entrance == ContentEntrance.DETAILS) distance * (1f - fraction) else 0f
        translationX = if (entrance == ContentEntrance.REPORT_STEP) {
            distance * (1f - fraction) * (if (forward) 1f else -1f) * (if (rtl) -1f else 1f)
        } else 0f
    }
}
