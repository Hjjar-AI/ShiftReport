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
    val dismiss = rememberUpdatedState(onDismiss)
    val action = rememberUpdatedState(onAction)
    LaunchedEffect(message) {
        if (message != null) {
            val result = host.showSnackbar(message, actionLabel = actionLabel)
            dismiss.value()
            if (result == SnackbarResult.ActionPerformed) action.value()
        }
    }
}
