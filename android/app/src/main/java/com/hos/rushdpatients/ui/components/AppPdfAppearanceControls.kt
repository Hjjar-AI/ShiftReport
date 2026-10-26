package com.hos.rushdpatients.ui.components


import com.hos.rushdpatients.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Local appearance only; the preview represents separators, not clinical content. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppPdfAppearanceControls(
    shading: PdfRowShading,
    onShading: (PdfRowShading) -> Unit,
    enabled: Boolean = true, showShading: Boolean = true
) {
    val uiText = rememberUiStrings()
    val weight = PdfRuleWeight.MEDIUM
    val contrast = PdfRuleContrast.MEDIUM
    if (showShading) AppChoiceChips(PdfRowShading.entries.toList(), shading, onShading, { it.uiLabel(uiText) },
        title = uiText(R.string.ui_app_pdf_appearance_controls_004), enabled = enabled)
    val palette = PdfPalette.resolve(PdfExportOptions(ruleWeight = weight, ruleContrast = contrast, rowShading = shading))
    Canvas(Modifier.fillMaxWidth().height(com.hos.rushdpatients.ui.theme.UiSize.pdfAppearancePreview).semantics {
        contentDescription = uiText(R.string.ui_app_pdf_appearance_controls_005, weight.uiLabel(uiText), contrast.uiLabel(uiText), shading.uiLabel(uiText))
    }) {
        drawRect(Color(palette.pageBackground))
        for (index in 0..2) {
            val top = size.height * index / 3f
            val background = if (!showShading || shading == PdfRowShading.NONE || index % 2 == 0) palette.pageBackground else palette.zebra
            drawRect(Color(background), Offset(0f, top), Size(size.width, size.height / 3f))
            val y = (top + size.height / 3f).coerceAtMost(size.height - palette.ruleWidth.dp.toPx() / 2f)
            drawLine(Color(palette.ruleColor), Offset(0f, y), Offset(size.width, y), strokeWidth = palette.ruleWidth.dp.toPx())
        }
    }
    Text(uiText(R.string.ui_app_pdf_appearance_controls_006),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
