package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Full-screen editor with fixed actions, keeping caller-owned drafts and hinge-safe content. */
@Composable
internal fun WardFormDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    val fold = rememberWardFoldingFeature()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )) {
            val view = LocalView.current
            DisposableEffect(view) {
                // Set native direction before the first layout, matching the Compose content.
                view.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
                (view.parent as? DialogWindowProvider)?.window?.decorView?.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
                onDispose { }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Surface(Modifier.fillMaxSize()) {
                    WardAdaptivePanes(
                        enableSplit = false, foldingFeature = fold,
                        listFraction = .42f, onListFractionChange = {},
                        modifier = Modifier.fillMaxSize(), primaryTitle = "تحرير المريض",
                        primary = {
                            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
                                Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxHeight()) {
                                    Box(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small)) { title() }
                                    Divider()
                                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small)) { text() }
                                    Divider()
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screen, vertical = UiSpacing.tiny),
                                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(Modifier.weight(1f)) { confirmButton() }
                                        dismissButton()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
