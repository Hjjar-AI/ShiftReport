package com.hos.rushdpatients.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*

/** Screens retain action policy and own message consumption; cancellation never consumes a message. */
@Composable
fun AppSnackbarEffect(
    message: String?, host: SnackbarHostState, onDismiss: () -> Unit,
    actionLabel: String? = null, onAction: () -> Unit = {}
) {
    LaunchedEffect(message) {
        if (message != null) {
            val consume = onDismiss
            val performAction = onAction
            val result = host.showSnackbar(message, actionLabel = actionLabel)
            consume()
            if (result == SnackbarResult.ActionPerformed) performAction()
        }
    }
}
