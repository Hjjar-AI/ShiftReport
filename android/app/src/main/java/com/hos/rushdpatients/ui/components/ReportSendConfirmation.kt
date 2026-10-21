package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun ReportSendConfirmation(destinations: List<String>, format: String, warningCount: Int,
    enabled: Boolean, unavailableReason: String? = null, onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إرسال التقرير") }, text = {
        Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            unavailableReason?.let { AppNotice(it, kind = NoticeKind.WARNING) }
            Text("الصيغة: $format")
            Text("الوجهة:")
            destinations.forEach { Text("• $it") }
            Text("ستُحدّث بيانات المناوبة المنشورة أولاً، ثم يُرسل التقرير من النسخة المقبولة بعد التحقق من مطابقتها للمراجعة. إذا فشل الإرسال بعد النشر، تبقى البيانات منشورة؛ راجع حالة العملية قبل الإعادة.")
            AppNotice("تأكيد الإرسال ينشر النسخة المحلية الكاملة للمناوبة مكان المنشورة، بما فيها الحذف والترتيب وأطباء المناوبة؛ آخر نشر ناجح هو المعتمد.", kind = NoticeKind.WARNING)
            Text("اختلاف نسخة المرضى أو وجود تعديلات أخرى في سجل الأطباء لا يمنع التأكيد. تغييرات حسابات الأطباء نفسها لا تُنشر بهذا الإجراء؛ يجب أن يكون الأطباء المسندون في المناوبة منشورين بتصنيف صالح.")
            if (warningCount > 0) AppNotice("توجد ${uiNumber(warningCount)} ملاحظات جاهزية غير مانعة.", kind = NoticeKind.WARNING)
        }
    }, confirmButton = { AppButton(onClick = { onConfirm(true) }, enabled = enabled) { Text("نشر نسختي وإرسال التقرير") } },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text("إلغاء") } })
}
