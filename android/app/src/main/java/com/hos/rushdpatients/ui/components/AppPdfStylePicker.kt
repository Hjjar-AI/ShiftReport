package com.hos.rushdpatients.ui.components


import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppPdfStylePicker(selected: PdfStyle, onSelected: (PdfStyle) -> Unit, enabled: Boolean = true) {
    val uiText = rememberUiStrings()
    Text(uiText(R.string.ui_app_pdf_style_picker_001), style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        PdfStyle.entries.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelected(option) }, enabled = enabled,
                label = { Text(option.uiLabel(uiText)) }, modifier = Modifier.heightIn(min = UiSpacing.touchTarget))
        }
    }
    Text(when (selected) {
        PdfStyle.CARDS -> uiText(R.string.ui_app_pdf_style_picker_002)
        PdfStyle.CLASSIC -> uiText(R.string.ui_app_pdf_style_picker_003)
    },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
