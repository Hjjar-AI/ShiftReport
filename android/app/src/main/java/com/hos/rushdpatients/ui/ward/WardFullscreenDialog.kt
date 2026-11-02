package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.window.layout.FoldingFeature
import com.hos.rushdpatients.ui.theme.UiLayout

/** Shared popup direction, safe viewport and hinge avoidance; callers own content and dismissal. */
@Composable
internal fun WardFullscreenDialog(
    title: String,
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    foldingFeature: FoldingFeature? = rememberWardFoldingFeature(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(onDismissRequest = onDismissRequest, properties = properties) {
            val view = LocalView.current
            DisposableEffect(view) {
                view.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
                (view.parent as? DialogWindowProvider)?.window?.decorView?.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
                onDispose { }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Surface(Modifier.fillMaxSize()) {
                    WardAdaptivePanes(
                        enableSplit = false, foldingFeature = foldingFeature,
                        listFraction = UiLayout.defaultListFraction, onListFractionChange = {},
                        modifier = Modifier.fillMaxSize(), primaryTitle = title,
                        primary = {
                            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) { content() }
                        }
                    )
                }
            }
        }
    }
}
