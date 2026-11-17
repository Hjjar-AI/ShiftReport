package com.hos.rushdpatients.ui.theme

import androidx.compose.ui.unit.dp

object UiOpacity {
    const val emblem = .10f
    const val emblemFaint = .06f
    const val emblemIdle = .07f
    const val splashCreditTint = .06f
    const val amoledLift = .06f
    const val containerSubtle = .35f
    const val containerStrong = .45f
    const val floatingContainer = .75f
    const val cardStrokeBlend = .65f
    const val cardOutlineEmphasis = .10f
    const val conflictTint = .12f
    const val containerSoft = .3f
    const val containerMuted = .5f
    const val enabled = 1f
    const val disabledContent = .38f
    const val disabledContainer = .12f
    fun content(enabled: Boolean): Float = if (enabled) this.enabled else disabledContent
}

