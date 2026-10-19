package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppPdfStylePicker(selected: PdfStyle, onSelected: (PdfStyle) -> Unit, enabled: Boolean = true) {
    Text("نمط PDF", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        PdfStyle.entries.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelected(option) }, enabled = enabled,
                label = { Text(option.arabicLabel) }, modifier = Modifier.heightIn(min = UiSpacing.touchTarget))
        }
    }
    Text(when (selected) {
        PdfStyle.CARDS -> "بطاقات طولية ملونة بحجم A5 عمودي مع فهرس قابل للضغط؛ اضغط اسم المريض للعودة إلى الفهرس."
        PdfStyle.CLASSIC -> "جدول كلاسيكي مضغوط يحافظ على كامل المحتوى؛ يمكن اختيار حجم الورق والاتجاه."
    },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
