package com.hos.rushdpatients.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Presentation only: the controller still owns the exact artifact and successful viewer opening. */
internal data class ReportPreviewItem(val key: String, val label: String, val reviewed: Boolean)

@Composable
internal fun ReportPreviewList(items: List<ReportPreviewItem>, enabled: Boolean,
    onPreview: (ReportPreviewItem) -> Unit) {
    val uiText = rememberUiStrings()
    AppSection(title = uiText(R.string.ui_report_preview_screen_058)) {
        Text(uiText(R.string.report_preview_progress, uiNumber(items.count { it.reviewed }), uiNumber(items.size)),
            style = MaterialTheme.typography.bodySmall)
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            items.forEach { item ->
                AppOutlinedButton(onClick = { onPreview(item) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(uiText(R.string.report_preview_destination, item.label))
                        if (item.reviewed) Text(uiText(R.string.report_preview_opened),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
