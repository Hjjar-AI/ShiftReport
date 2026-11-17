package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiPadding

/** Startup/recovery presentation keeps short-window notices reachable even before a ward loads. */
@Composable
internal fun WardContentHost(state: WardUiState, onReload: () -> Unit, shortWindow: Boolean,
    leadingContent: @Composable () -> Unit, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit) {
    val text = rememberUiStrings()
    val statusContent: @Composable (Modifier) -> Unit = { statusModifier ->
        if (state.loading) AppLoadingState(text(R.string.ui_ward_screen_006), statusModifier)
        else AppNotice(message = text(R.string.ui_ward_screen_007, state.error.orEmpty()),
            kind = NoticeKind.ERROR, modifier = statusModifier.padding(UiPadding.content),
            actionLabel = text(R.string.ui_retry), onAction = onReload,
            actionEnabled = !state.syncing && !state.saving)
    }
    Box(modifier) {
        if (state.shift == null && (state.loading || state.error != null)) {
            if (shortWindow) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                leadingContent()
                statusContent(Modifier)
            } else statusContent(Modifier.align(Alignment.Center))
        } else content()
    }
}
