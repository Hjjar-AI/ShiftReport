package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppPdfStylePicker
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.ui.components.AppPdfAppearanceControls
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsReportContent(state: SettingsUiState, viewModel: SettingsViewModel, advancedPdf: Boolean, onToggleAdvanced: () -> Unit) {
    SettingsSubheading("الإرسال والتخطيط")
    AppToggleRow(
        title = "إرسال التقرير كملف PDF",
        subtitle = "خيار افتراضي عند معاينة التقرير",
        checked = state.reportAsPdf,
        onCheckedChange = viewModel::setReportAsPdf
    )

    AppPdfStylePicker(selected = state.pdfStyle, onSelected = viewModel::setPdfStyle)
    AppTextButton(onClick = onToggleAdvanced,
        modifier = Modifier.heightIn(min = UiSpacing.touchTarget).semantics {
            stateDescription = if (advancedPdf) "موسع" else "مطوي"
        }) { Text(if (advancedPdf) "إخفاء خيارات PDF المتقدمة" else "خيارات PDF المتقدمة") }
    if (advancedPdf) {
        if (state.pdfStyle != PdfStyle.CARDS) {
            Text("اتجاه الصفحة", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                PdfOrientation.entries.forEach { option ->
                    FilterChip(
                        selected = state.pdfOrientation == option,
                        onClick = { viewModel.setPdfOrientation(option) },
                        label = { Text(option.arabicLabel) }
                    )
                }
            }

            Text("حجم الورق", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                PdfPaperSize.entries.forEach { option ->
                    FilterChip(
                        selected = state.pdfPaperSize == option,
                        onClick = { viewModel.setPdfPaperSize(option) },
                        label = { Text(option.arabicLabel) }
                    )
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsSubheading("ألوان وتوزيع الملف")
        Text("ألوان ملف PDF", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            PdfColorPreset.entries.forEach { option ->
                FilterChip(
                    selected = state.pdfColorPreset == option,
                    onClick = { viewModel.setPdfColorPreset(option) },
                    label = { Text(option.arabicLabel) }
                )
            }
        }

        AppPdfAppearanceControls(
            weight = state.pdfRuleWeight, contrast = state.pdfRuleContrast,
            shading = state.pdfRowShading, dark = state.pdfDarkMode,
            onWeight = viewModel::setPdfRuleWeight,
            onContrast = viewModel::setPdfRuleContrast,
            onShading = viewModel::setPdfRowShading,
            onPreset = viewModel::setPdfPresentationPreset,
            showShading = state.pdfStyle != PdfStyle.CARDS
        )
        AppToggleRow(
            title = "PDF داكن",
            subtitle = "خلفية داكنة ونص فاتح",
            checked = state.pdfDarkMode,
            onCheckedChange = viewModel::setPdfDarkMode
        )
        AppToggleRow(
            title = "ملف PDF لكل مشرف",
            subtitle = "عند الحفظ المحلي",
            checked = state.pdfSeparateBySupervisor,
            onCheckedChange = viewModel::setPdfSeparateBySupervisor
        )
    }
}
