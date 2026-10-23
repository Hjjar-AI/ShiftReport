package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Consumes only downward scroll left over at the top. Release requests review, never a fetch. */
@Composable
internal fun WardPullToFetch(
    enabled: Boolean, onRequest: () -> Unit, content: @Composable BoxScope.() -> Unit
) {
    val uiText = rememberUiStrings()
    val threshold = with(LocalDensity.current) { UiLayout.fetchPullDistance.toPx() }
    var distance by remember(enabled, threshold) { mutableStateOf(0f) }
    val request by rememberUpdatedState(onRequest)
    val connection = remember(enabled, threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || source != NestedScrollSource.Drag || distance <= 0f || available.y >= 0f) return Offset.Zero
                val consumed = minOf(-available.y, distance * 2)
                distance = (distance - consumed / 2).coerceAtLeast(0f)
                return Offset(0f, -consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || source != NestedScrollSource.Drag || available.y <= 0f) return Offset.Zero
                distance = (distance + available.y / 2).coerceAtMost(threshold * 1.5f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val pulled = distance > 0f
                val ready = enabled && distance >= threshold
                distance = 0f
                if (ready) request()
                return if (pulled) Velocity(0f, available.y) else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                distance = 0f
                return Velocity.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        content()
        if (enabled && distance > 0f) Surface(
            Modifier.align(Alignment.TopCenter).padding(UiPadding.content).semantics { liveRegion = LiveRegionMode.Polite },
            shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Row(Modifier.padding(UiPadding.button), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Icon(AppIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(UiSize.iconSmall))
                Text(uiText(if (distance >= threshold) R.string.ui_fetch_pull_release else R.string.ui_fetch_pull_hint),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
