package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Presentation only; screen state, retry lifecycle and content remain caller-owned. */
@Composable
fun AppStateHost(loading: Boolean, loadingMessage: String, modifier: Modifier = Modifier,
    error: String? = null, retryLabel: String? = null, onRetry: (() -> Unit)? = null,
    empty: Boolean = false, emptyTitle: String = "", content: @Composable () -> Unit = {}) {
    when {
        loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingState(loadingMessage) }
        error != null -> AppCenteredContent(modifier) {
            AppNotice(error, kind = NoticeKind.ERROR, actionLabel = retryLabel, onAction = onRetry)
        }
        empty -> EmptyState(emptyTitle, modifier = modifier)
        else -> Box(modifier) { content() }
    }
}
