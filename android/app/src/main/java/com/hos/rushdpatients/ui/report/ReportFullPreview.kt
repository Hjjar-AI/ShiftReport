package com.hos.rushdpatients.ui.report

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.ButtonEmblemContent
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.uiContentGap
import com.hos.rushdpatients.ui.theme.uiContentPadding

/** Previewing the full snapshot does not change which reports are selected for delivery. */
@Composable
internal fun ReportFullPreview(reviewed: Boolean, enabled: Boolean, onPreview: () -> Unit,
    onSortPatients: () -> Unit) {
    val uiText = rememberUiStrings()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(uiContentPadding()),
            verticalArrangement = Arrangement.spacedBy(uiContentGap())) {
            AppOutlinedButton(onClick = onSortPatients, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                ButtonEmblemContent(AppIcons.Sort, enabled) {
                    Text(uiText(R.string.report_sort_patients))
                }
            }
            AppOutlinedButton(onClick = onPreview, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(R.string.report_full_preview_action))
            }
            if (reviewed) Text(uiText(R.string.report_preview_opened), style = MaterialTheme.typography.labelSmall)
        }
    }
}
