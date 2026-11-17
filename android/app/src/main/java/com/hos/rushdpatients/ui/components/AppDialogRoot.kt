package com.hos.rushdpatients.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.DialogWindowProvider

/** Invoke inside each popup root before measuring; native RTL is opt-in for fullscreen windows. */
@Composable
fun AppDialogRoot(nativeRtl: Boolean = false, content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view, nativeRtl) {
        if (nativeRtl) {
            view.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            (view.parent as? DialogWindowProvider)?.window?.decorView?.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        }
        onDispose { }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
}
