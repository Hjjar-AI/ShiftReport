package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hos.rushdpatients.ui.theme.UiElevation
import com.hos.rushdpatients.ui.theme.UiSpacing

/** RTL is provided inside the popup composition, before measuring its content or actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        // A Dialog has its own Android/Compose root. An activity-side provider alone
        // cannot pin the direction used by content inside that new root.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                modifier = modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = UiElevation.overlay
            ) {
                Column(Modifier.padding(UiSpacing.pageSection),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.largeSection)) {
                    icon?.let {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { it() }
                    }
                    title?.let {
                        Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
                            contentAlignment = Alignment.TopStart) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(
                                textDirection = TextDirection.ContentOrRtl)) { it() }
                        }
                    }
                    text?.let {
                        // Measure fixed title/actions first; long bodies keep the remaining viewport.
                        Box(Modifier.weight(1f, fill = false).fillMaxWidth(),
                            contentAlignment = Alignment.TopStart) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = TextDirection.ContentOrRtl)) { it() }
                            }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
