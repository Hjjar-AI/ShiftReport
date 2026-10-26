package com.hos.rushdpatients.ui.components


import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.hos.rushdpatients.pdf.PdfStyle

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppPdfStylePicker(selected: PdfStyle, onSelected: (PdfStyle) -> Unit, enabled: Boolean = true) {
    val uiText = rememberUiStrings()
    AppChoiceChips(PdfStyle.entries.toList(), selected, onSelected, { it.uiLabel(uiText) },
        title = uiText(R.string.ui_app_pdf_style_picker_001), enabled = enabled)
    Text(when (selected) {
        PdfStyle.CARDS -> uiText(R.string.ui_app_pdf_style_picker_002)
        PdfStyle.CLASSIC -> uiText(R.string.ui_app_pdf_style_picker_003)
    },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
