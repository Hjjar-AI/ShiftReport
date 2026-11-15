package com.hos.rushdpatients.ui.report

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.components.rememberUiStrings

/** Previewing the full snapshot does not change which reports are selected for delivery. */
@Composable
internal fun ReportFullPreview(reviewed: Boolean, enabled: Boolean, onPreview: () -> Unit) {
    val uiText = rememberUiStrings()
    AppSection(title = uiText(R.string.report_full_preview_title)) {
        Text(uiText(R.string.report_full_preview_description), style = MaterialTheme.typography.bodySmall)
        AppOutlinedButton(onClick = onPreview, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(uiText(R.string.report_full_preview_action))
        }
        if (reviewed) Text(uiText(R.string.report_preview_opened), style = MaterialTheme.typography.labelSmall)
    }
}
