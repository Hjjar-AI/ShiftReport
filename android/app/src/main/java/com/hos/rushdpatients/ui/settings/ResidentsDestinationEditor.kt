package com.hos.rushdpatients.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.config.ProjectConfig
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Connection identifiers stay in memory only, never in saved UI state. */
@Composable
internal fun ResidentsDestinationEditor(state: SettingsUiState, viewModel: SettingsViewModel) {
    val text = rememberUiStrings()
    var expected by remember(state.isAdmin) { mutableStateOf<ProjectConfig?>(null) }
    AppOutlinedButton(onClick = { expected = viewModel.residentsDestinationConfig() },
        enabled = state.isAdmin && !state.savingResidentsDestination && !state.syncing && !state.provisioningBusy,
        modifier = Modifier.fillMaxWidth()) { Text(text(R.string.residents_destination_title)) }
    expected?.let { config ->
        var group by remember(config) { mutableStateOf(config.residentsChatId.takeIf { it != 0L }?.toString().orEmpty()) }
        var topic by remember(config) { mutableStateOf(config.residentsTopicId.takeIf { it != 0L }?.toString().orEmpty()) }
        AppAlertDialog(
            onDismissRequest = { if (!state.savingResidentsDestination) expected = null },
            title = { Text(text(R.string.residents_destination_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text(text(R.string.residents_destination_hint))
                    TelegramIdField(group, { group = it }, label = text(R.string.residents_group_optional), signed = true,
                        enabled = !state.savingResidentsDestination)
                    TelegramIdField(topic, { topic = it }, label = text(R.string.residents_topic_optional),
                        enabled = !state.savingResidentsDestination)
                    Text(text(R.string.residents_destination_redistribute))
                    state.residentsDestinationError?.let { AppNotice(it, kind = NoticeKind.ERROR) }
                }
            },
            confirmButton = {
                LoadingButton(text = text(R.string.ui_save), loading = state.savingResidentsDestination,
                    onClick = { viewModel.saveResidentsDestination(config, group, topic) { expected = null } })
            },
            dismissButton = { AppTextButton(onClick = { expected = null }, enabled = !state.savingResidentsDestination) { Text(text(R.string.ui_cancel)) } }
        )
    }
}
