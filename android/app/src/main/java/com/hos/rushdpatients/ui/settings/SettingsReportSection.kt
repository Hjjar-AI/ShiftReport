package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppPdfStylePicker
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import com.hos.rushdpatients.ui.components.AppDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.ui.components.AppPdfAppearanceControls
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsReportContent(state: SettingsUiState, viewModel: SettingsViewModel, advancedPdf: Boolean, onToggleAdvanced: () -> Unit) {
    val uiText = rememberUiStrings()
    SettingsSubheading(uiText(R.string.ui_settings_report_section_001))
    AppToggleRow(
        title = uiText(R.string.ui_settings_report_section_002),
        subtitle = uiText(R.string.ui_settings_report_section_003),
        checked = state.reportAsPdf,
        onCheckedChange = viewModel::setReportAsPdf
    )

    AppPdfStylePicker(selected = state.pdfStyle, onSelected = viewModel::setPdfStyle)
    AppTextButton(onClick = onToggleAdvanced,
        modifier = Modifier.heightIn(min = UiSpacing.touchTarget).semantics {
            stateDescription = if (advancedPdf) uiText(R.string.ui_patient_form_dialog_023) else uiText(R.string.ui_patient_form_dialog_024)
        }) { Text(if (advancedPdf) uiText(R.string.ui_settings_report_section_004) else uiText(R.string.ui_settings_report_section_005)) }
    if (advancedPdf) {
        if (state.pdfStyle != PdfStyle.CARDS) {
            Text(uiText(R.string.ui_settings_report_section_007), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                PdfPaperSize.entries.forEach { option ->
                    AppFilterChip(
                        selected = state.pdfPaperSize == option,
                        onClick = { viewModel.setPdfPaperSize(option) },
                        label = { Text(option.uiLabel(uiText)) }
                    )
                }
            }
        }

        AppDivider()
        SettingsSubheading(uiText(R.string.ui_settings_report_section_008))
        Text(uiText(R.string.ui_settings_report_section_009), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            PdfColorPreset.entries.forEach { option ->
                AppFilterChip(
                    selected = state.pdfColorPreset == option,
                    onClick = { viewModel.setPdfColorPreset(option) },
                    label = { Text(option.uiLabel(uiText)) }
                )
            }
        }

        AppPdfAppearanceControls(
            shading = state.pdfRowShading,
            onShading = viewModel::setPdfRowShading,
            showShading = state.pdfStyle != PdfStyle.CARDS
        )


    }
}
