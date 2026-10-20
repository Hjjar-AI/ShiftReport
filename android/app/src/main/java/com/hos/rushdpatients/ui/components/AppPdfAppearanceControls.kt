package com.hos.rushdpatients.ui.components

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
    weight: PdfRuleWeight, contrast: PdfRuleContrast, shading: PdfRowShading, dark: Boolean,
    onWeight: (PdfRuleWeight) -> Unit, onContrast: (PdfRuleContrast) -> Unit,
    onShading: (PdfRowShading) -> Unit, onPreset: (PdfPresentationPreset) -> Unit,
    enabled: Boolean = true, showShading: Boolean = true
) {
    Text("اختيار سريع", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        PdfPresentationPreset.entries.forEach { preset ->
            FilterChip(selected = weight == preset.weight && contrast == preset.contrast && shading == preset.shading &&
                (!preset.lightOnly || !dark), onClick = { onPreset(preset) }, enabled = enabled,
                label = { Text(preset.arabicLabel) }, modifier = Modifier.heightIn(min = UiSpacing.touchTarget))
        }
    }
    Text("سماكة الفواصل الأفقية", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        PdfRuleWeight.entries.forEach { value ->
            FilterChip(selected = weight == value, onClick = { onWeight(value) }, enabled = enabled,
                label = { Text(value.arabicLabel) }, modifier = Modifier.heightIn(min = UiSpacing.touchTarget))
        }
    }
    Text("وضوح الفواصل", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        PdfRuleContrast.entries.forEach { value ->
            FilterChip(selected = contrast == value, onClick = { onContrast(value) }, enabled = enabled,
                label = { Text(value.arabicLabel) }, modifier = Modifier.heightIn(min = UiSpacing.touchTarget))
        }
    }
    if (showShading) {
        Text("تظليل صفوف الجدول", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            PdfRowShading.entries.forEach { value ->
                FilterChip(selected = shading == value, onClick = { onShading(value) }, enabled = enabled,
                    label = { Text(value.arabicLabel) }, modifier = Modifier.heightIn(min = UiSpacing.touchTarget))
            }
        }
    }
    val palette = PdfPalette.resolve(PdfExportOptions(ruleWeight = weight, ruleContrast = contrast, rowShading = shading, darkMode = dark))
    Canvas(Modifier.fillMaxWidth().height(72.dp).semantics {
        contentDescription = "معاينة الفواصل: ${weight.arabicLabel}، ${contrast.arabicLabel}، ${shading.arabicLabel}"
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
    Text("المعاينة للفواصل؛ التظليل يخص الجداول وتوزيع الفرق. تبقى البطاقات A5 بهوامش ثابتة.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
