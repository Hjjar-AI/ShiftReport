package com.hos.rushdpatients.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiSpacing
import java.time.Instant

/** Publication receipts belong to sync; this never loads the retired clinical audit log. */
@Composable
internal fun SettingsPublicationHistory(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (!state.isAdmin) return
    val text = rememberUiStrings()
    var expanded by rememberSaveable { mutableStateOf(false) }
    AppTextButton(onClick = {
        expanded = !expanded
        if (expanded) viewModel.loadPublicationHistory()
    }) { Text(text(R.string.publication_history_title)) }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        if (state.publicationHistoryLoading) Text(text(R.string.ui_ward_activity_screen_004))
        state.publicationHistoryError?.let { error ->
            AppNotice(error, kind = NoticeKind.ERROR, actionLabel = text(R.string.ui_retry),
                onAction = viewModel::loadPublicationHistory, actionEnabled = !state.publicationHistoryLoading)
        }
        if (!state.publicationHistoryLoading && state.publicationHistoryError == null && state.publicationHistory.isEmpty())
            Text(text(R.string.ui_ward_activity_screen_007))
        state.publicationHistory.forEach { publication ->
            Text(text(if (publication.forced) R.string.ui_ward_activity_screen_001 else R.string.ui_ward_activity_screen_002) +
                " · " + uiDateTime(Instant.ofEpochMilli(publication.at)), style = MaterialTheme.typography.bodySmall)
        }
    }
}
