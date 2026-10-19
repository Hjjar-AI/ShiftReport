package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun ReportSendConfirmation(destinations: List<String>, format: String, warningCount: Int,
    enabled: Boolean, unavailableReason: String? = null, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إرسال التقرير") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            unavailableReason?.let { AppNotice(it, kind = NoticeKind.WARNING) }
            Text("الصيغة: $format")
            Text("الوجهة:")
            destinations.forEach { Text("• $it") }
            Text("ستُحدّث بيانات المناوبة المنشورة أولاً، ثم يُرسل التقرير من النسخة المقبولة بعد التحقق من مطابقتها للمراجعة. إذا فشل الإرسال بعد النشر، تبقى البيانات منشورة؛ راجع حالة العملية قبل الإعادة.")
            if (warningCount > 0) AppNotice("توجد ${uiNumber(warningCount)} ملاحظات جاهزية غير مانعة.", kind = NoticeKind.WARNING)
        }
    }, confirmButton = { AppButton(onClick = onConfirm, enabled = enabled) { Text("إرسال") } },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text("إلغاء") } })
}
