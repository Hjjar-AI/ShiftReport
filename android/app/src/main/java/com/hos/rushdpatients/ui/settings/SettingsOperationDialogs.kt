package com.hos.rushdpatients.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.PasswordField
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Presentation only; the screen retains secrets, picker state and operation callbacks. */
@Composable
internal fun SettingsBackupPasswordDialog(
    exporting: Boolean, backupPassword: String,
    onPasswordChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (exporting) uiText(R.string.ui_settings_screen_014) else uiText(R.string.ui_settings_screen_015))
        },
        text = {
            PasswordField(
                value = backupPassword,
                onValueChange = onPasswordChange,
                label = uiText(R.string.ui_settings_screen_016)
            )
        },
        confirmButton = {
            AppTextButton(
                enabled = backupPassword.length >= 8,
                onClick = onConfirm
            ) { Text(if (exporting) uiText(R.string.ui_settings_screen_017) else uiText(R.string.ui_restore)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(uiText(R.string.ui_cancel)) }
        }
    )
}

@Composable
internal fun SettingsProvisioningDialog(
    provisioningPassphrase: String, busy: Boolean, directDownloads: Boolean,
    onPassphraseChange: (String) -> Unit,
    onShare: () -> Unit, onSave: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(R.string.ui_settings_screen_019)) },
        text = {
            Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(uiText(R.string.ui_settings_screen_020))
                PasswordField(
                    value = provisioningPassphrase,
                    onValueChange = onPassphraseChange,
                    label = uiText(R.string.ui_settings_screen_021)
                )
                Text(if (directDownloads) uiText(R.string.ui_settings_screen_022)
                    else uiText(R.string.ui_settings_screen_023),
                    style = MaterialTheme.typography.bodySmall)
                if (directDownloads) AppTextButton(
                    enabled = provisioningPassphrase.length >= 10 && !busy,
                    onClick = onShare, modifier = Modifier.fillMaxWidth()
                ) { Text(uiText(R.string.ui_settings_screen_024)) }
                AppTextButton(
                    enabled = provisioningPassphrase.length >= 10 && !busy,
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
                ) { Text(uiText(R.string.ui_settings_screen_025)) }
            }
        },
        confirmButton = {
            AppTextButton(
                enabled = provisioningPassphrase.length >= 10 && !busy,
                onClick = onConfirm
            ) { Text(if (directDownloads) uiText(R.string.ui_settings_screen_026) else uiText(R.string.ui_settings_screen_027)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(uiText(R.string.ui_cancel)) }
        }
    )
}

@Composable
internal fun SettingsPublicationReviewDialog(
    reviewText: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(R.string.ui_settings_screen_028)) },
        text = {
            Column(Modifier.appDialogBody(UiDialog.reviewBodyMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(reviewText)
                Text(uiText(R.string.ui_settings_screen_029))
            }
        },
        confirmButton = {
            AppTextButton(onClick = onConfirm, enabled = !busy) {
                Text(uiText(R.string.ui_settings_screen_030))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, enabled = !busy) { Text(uiText(R.string.ui_cancel)) }
        }
    )
}

@Composable
internal fun SettingsBackupRestoreDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val uiText = rememberUiStrings()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(R.string.ui_settings_screen_031)) },
        text = {
            Text(uiText(R.string.ui_settings_screen_032))
        },
        confirmButton = {
            AppTextButton(onClick = onConfirm) { Text(uiText(R.string.ui_settings_screen_033)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(uiText(R.string.ui_cancel)) }
        }
    )
}
