package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

enum class AppLoadingSize { INLINE, SECTION, PAGE }

@Composable
fun AppLoadingState(message: String, modifier: Modifier = Modifier, size: AppLoadingSize = AppLoadingSize.PAGE) {
    val padding = when (size) {
        AppLoadingSize.INLINE -> UiSpacing.tiny
        AppLoadingSize.SECTION -> UiSpacing.small
        AppLoadingSize.PAGE -> UiSpacing.screen
    }
    val container = modifier.fillMaxWidth().padding(padding).semantics { liveRegion = LiveRegionMode.Polite }
    val indicator: @Composable () -> Unit = {
        AppBusyIndicator(size = when (size) {
            AppLoadingSize.INLINE -> UiSize.iconSmall
            AppLoadingSize.SECTION -> UiSize.icon
            AppLoadingSize.PAGE -> UiSize.loadingIndicator
        }, color = MaterialTheme.colorScheme.primary)
    }
    if (size == AppLoadingSize.INLINE) {
        Row(container, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
            indicator()
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    } else {
        Column(container, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            indicator()
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
