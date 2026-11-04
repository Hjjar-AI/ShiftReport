package com.hos.rushdpatients.ui.settings


import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppPdfStylePicker
import com.hos.rushdpatients.ui.components.AppToggleRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsReportContent(
    reportAsPdf: Boolean, pdfStyle: PdfStyle, appTheme: AppThemePreset,
    onReportAsPdf: (Boolean) -> Unit, onPdfStyle: (PdfStyle) -> Unit
) {
    val uiText = rememberUiStrings()
    SettingsSubheading(uiText(R.string.ui_settings_report_section_001))
    AppToggleRow(
        title = uiText(R.string.ui_settings_report_section_002),
        subtitle = uiText(R.string.ui_settings_report_section_003),
        checked = reportAsPdf,
        onCheckedChange = onReportAsPdf
    )

    AppPdfStylePicker(selected = pdfStyle, onSelected = onPdfStyle)
    Text(uiText(R.string.pdf_uses_app_theme, appTheme.arabicLabel(uiText)),
        style = MaterialTheme.typography.bodySmall)
}
