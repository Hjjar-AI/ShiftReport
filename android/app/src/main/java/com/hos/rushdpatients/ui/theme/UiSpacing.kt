package com.hos.rushdpatients.ui.theme

import androidx.compose.ui.unit.dp

/** Numeric scale; semantic spacing roles below retain their current values. */
object UiSpace {
    val none = 0.dp
    val xxs = 1.dp
    val xs = 2.dp
    val s = 4.dp
    val halfStep = 6.dp
    val m = 8.dp
    val l = 12.dp
    val xl = 16.dp
    val xxl = 24.dp
    val xxxl = 32.dp // Reserved for larger empty/hero content.
}

/** Shared rhythm; compact layouts reduce spacing, never text size or touch targets. */
object UiSpacing {
    val micro = UiSpace.xxs
    val tiny = UiSpace.xs
    val small = UiSpace.s
    val medium = UiSpace.halfStep
    val screen = UiSpace.m
    val section = UiSpace.l
    val largeSection = UiSpace.xl
    val pageSection = UiSpace.xxl
    val screenGutter = screen
    val cardPadding = screen
    val blockGap = section
    val sectionGap = screen
    val controlGap = small
    val aboutInset = 20.dp
    val touchTarget = 48.dp
}
