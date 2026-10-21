package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = androidx.compose.ui.res.stringResource(R.string.ui_confirm),
    dismissText: String = androidx.compose.ui.res.stringResource(R.string.ui_cancel),
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppDialogHeader(title) },
        text = { Text(message) },
        confirmButton = {
            AppDialogConfirmAction(text = confirmText, onClick = onConfirm, destructive = destructive)
        },
        dismissButton = {
            AppDialogDismissAction(onClick = onDismiss, text = dismissText)
        }
    )
}
