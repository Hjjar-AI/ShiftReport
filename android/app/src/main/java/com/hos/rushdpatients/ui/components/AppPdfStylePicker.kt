package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppPdfStylePicker(selected: PdfStyle, onSelected: (PdfStyle) -> Unit, enabled: Boolean = true) {
    Text("نمط PDF", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        PdfStyle.entries.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelected(option) }, enabled = enabled,
                label = { Text(option.arabicLabel) })
        }
    }
    if (selected == PdfStyle.CARDS) Text("بطاقات طولية ملونة بحجم A5 عمودي مع فهرس قابل للضغط؛ اضغط اسم المريض للعودة إلى الفهرس.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
