package com.hos.rushdpatients.ui.announcement

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreen(onBack: () -> Unit, viewModel: AnnouncementViewModel = hiltViewModel()) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val busy = state.sending || state.reviewing || state.resolving || state.loading
    var resolution by remember { mutableStateOf<Pair<ReportDeliveryRecord, Boolean>?>(null) }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbar.showSnackbar(it); viewModel.dismissSnackbar() }
    }
    Scaffold(topBar = { AppTopBar(title = uiText(R.string.ui_announcement_screen_001), onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(UiSpacing.screen).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)) {
            Text(uiText(R.string.ui_announcement_screen_002),style = MaterialTheme.typography.bodyMedium)
            if (state.loading) Text(uiText(R.string.ui_announcement_screen_003))
            state.lastMessageId?.let { Text(uiText(R.string.ui_announcement_screen_004, it),style = MaterialTheme.typography.bodySmall) }
            AppTextField(value = state.template,onValueChange = viewModel::onTemplateChange,label = { Text(uiText(R.string.ui_announcement_screen_005)) },
                enabled = !busy,placeholder = { Text(uiText(R.string.ui_announcement_screen_006)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),maxLines = 20)
            LoadingButton(text = uiText(R.string.ui_announcement_screen_007),loading = state.reviewing,onClick = viewModel::review,
                enabled = !busy && !state.loading && !state.ledgerUnavailable && state.deliveryRecords.isEmpty(),modifier = Modifier.fillMaxWidth())
            AppTextButton(onClick = viewModel::refresh,enabled = !busy) { Text(uiText(R.string.ui_announcement_screen_008)) }
            if (state.ledgerUnavailable) Text(uiText(R.string.ui_announcement_screen_009))
            state.deliveryRecords.forEach { row ->
                Text(uiText(R.string.ui_announcement_screen_010))
                AppTextButton(onClick = { resolution = row to true },enabled = !busy) { Text(uiText(R.string.ui_announcement_screen_011)) }
                AppTextButton(onClick = { resolution = row to false },enabled = !busy) { Text(uiText(R.string.ui_announcement_screen_012)) }
            }
        }
    }
    state.reviewText?.let { text ->
        AlertDialog(onDismissRequest = viewModel::dismissReview,title = { Text(if (state.reviewExisting) uiText(R.string.ui_announcement_screen_013) else uiText(R.string.ui_announcement_screen_014)) },
            text = { Text(text,Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { AppTextButton(onClick = viewModel::confirmSend,enabled = !busy) { Text(uiText(R.string.ui_doctors_screen_030)) } },
            dismissButton = { AppTextButton(onClick = viewModel::dismissReview,enabled = !busy) { Text(uiText(R.string.ui_cancel)) } })
    }
    resolution?.let { (row,received) ->
        var messageId by remember(row.id,received) { mutableStateOf("") }
        val parsed = messageId.map { if (Character.isDigit(it)) Character.digit(it,10).toString() else it.toString() }.joinToString("").trim().toLongOrNull()
        AlertDialog(onDismissRequest = { if (!busy) resolution = null },title = { Text(uiText(R.string.ui_announcement_screen_015)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text(if (received) uiText(R.string.ui_announcement_screen_016)
                         else uiText(R.string.ui_announcement_screen_017))
                    if (received) AppTextField(messageId,{ messageId = it },label = { Text(uiText(R.string.ui_announcement_screen_018)) },singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { AppTextButton(onClick = { viewModel.resolve(row,if (received) parsed else null); resolution = null },
                enabled = !busy && (!received || (parsed != null && parsed > 0))) { Text(uiText(R.string.ui_announcement_screen_019)) } },
            dismissButton = { AppTextButton(onClick = { resolution = null },enabled = !busy) { Text(uiText(R.string.ui_cancel)) } })
    }
}
