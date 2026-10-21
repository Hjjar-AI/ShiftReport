package com.hos.rushdpatients.ui.connection

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.components.AppBusyIndicator
import com.hos.rushdpatients.ui.components.PasswordField
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** File recovery is available before login; manual token replacement still requires live admin authority. */
@Composable
fun ProjectConnectionAction(
    isAdmin: Boolean = false,
    onApplied: () -> Unit = {},
    viewModel: ProjectConnectionViewModel = hiltViewModel()
) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    // Drop the open credential form and its secret when admin access changes.
    var open by remember(isAdmin) { mutableStateOf(false) }
    var manual by remember(isAdmin) { mutableStateOf(false) }
    var selectedFile by remember(isAdmin) { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember(selectedFile) { mutableStateOf<String?>(null) }
    var secret by remember(isAdmin) { mutableStateOf("") }
    var pickerError by remember(isAdmin) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFile = uri
            secret = ""
        }
    }
    LaunchedEffect(state.applied) { if (state.applied) onApplied() }
    LaunchedEffect(selectedFile) {
        selectedFileName = selectedFile?.let { viewModel.readDisplayName(it) }
    }

    AppOutlinedButton(onClick = {
        viewModel.reset()
        manual = false
        selectedFile = null
        secret = ""
        pickerError = null
        open = true
    }, modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_project_connection_action_001)) }

    if (open) {
        val dismiss = {
            if (!state.busy) {
                open = false
                selectedFile = null
                secret = ""
                viewModel.reset()
            }
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(if (manual) uiText(R.string.ui_project_connection_action_002) else uiText(R.string.ui_project_connection_action_001)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                ) {
                    when {
                        state.reviewProject != null -> {
                            Text(uiText(R.string.ui_project_connection_action_003, state.reviewProject))
                            Text(uiText(R.string.ui_project_connection_action_004))
                        }
                        state.applied -> Unit
                        else -> {
                            Text(uiText(R.string.ui_project_connection_action_005))
                            if (manual) {
                                Text(uiText(R.string.ui_project_connection_action_006))
                                AppTextButton(onClick = {
                                    try {
                                        uriHandler.openUri("https://t.me/BotFather")
                                    } catch (_: Exception) {
                                        pickerError = uiText(R.string.ui_project_connection_action_007)
                                    }
                                }, enabled = !state.busy) { Text(uiText(R.string.ui_project_connection_action_008)) }
                            } else {
                                AppOutlinedButton(onClick = {
                                    pickerError = null
                                    try {
                                        picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                                    } catch (_: Exception) {
                                        pickerError = uiText(R.string.ui_project_connection_action_009)
                                    }
                                }, enabled = !state.busy) {
                                    Text(if (selectedFile == null) uiText(R.string.ui_project_connection_action_010) else uiText(R.string.ui_project_connection_action_011))
                                }
                                if (selectedFile != null) Text(
                                    uiText(R.string.ui_project_connection_action_013, selectedFileName ?: uiText(R.string.ui_project_connection_action_012)),
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            PasswordField(
                                value = secret,
                                onValueChange = { secret = it },
                                label = if (manual) uiText(R.string.ui_project_connection_action_014) else uiText(R.string.ui_project_connection_action_015),
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isAdmin) AppTextButton(onClick = {
                                manual = !manual
                                selectedFile = null
                                secret = ""
                                pickerError = null
                                viewModel.reset()
                            }, enabled = !state.busy) {
                                Text(if (manual) uiText(R.string.ui_project_connection_action_016) else uiText(R.string.ui_project_connection_action_017))
                            }
                        }
                    }
                    if (state.busy) AppBusyIndicator(size = UiSize.loadingIndicator, color = MaterialTheme.colorScheme.primary)
                    (state.message ?: pickerError)?.let {
                        AppNotice(it, kind = if (state.applied) NoticeKind.SUCCESS else NoticeKind.ERROR)
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = !state.busy && (state.applied || state.reviewProject != null ||
                        (manual && secret.isNotBlank()) ||
                        (!manual && selectedFile != null && secret.length >= 10)),
                    onClick = {
                        when {
                            state.applied -> dismiss()
                            state.reviewProject != null -> viewModel.confirm()
                            manual -> { viewModel.replaceToken(secret); secret = "" }
                            else -> selectedFile?.let { viewModel.importFile(it, secret); secret = "" }
                        }
                    }
                ) {
                    Text(when {
                        state.applied -> uiText(R.string.ui_done)
                        state.reviewProject != null -> uiText(R.string.ui_project_connection_action_018)
                        else -> uiText(R.string.ui_project_connection_action_019)
                    })
                }
            },
            dismissButton = {
                if (!state.applied) AppTextButton(onClick = dismiss, enabled = !state.busy) { Text(uiText(R.string.ui_cancel)) }
            }
        )
    }
}
