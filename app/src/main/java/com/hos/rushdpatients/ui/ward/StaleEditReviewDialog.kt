package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun StaleEditReviewDialog(
    differences: List<Triple<String, String, String>>,
    available: Boolean,
    onKeepDraft: () -> Unit,
    onUseSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مراجعة تغييرات أحدث") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                Text(if (available)
                    "لم تُحفظ المسودة. راجع الفروق قبل المتابعة؛ الاحتفاظ بالمسودة سيستبدل القيم المحفوظة المعروضة عند الحفظ التالي. لن يتم الحفظ تلقائياً."
                else "السجل لم يعد متاحاً للتعديل. المسودة باقية في المحرر؛ لا يمكن حفظها فوق سجل محذوف أو غير موجود.")
                differences.forEach { (label, draft, saved) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text("المحفوظ الآن: ${saved.ifBlank { "فارغ" }}")
                        Text("مسودتي: ${draft.ifBlank { "فارغ" }}")
                    }
                }
                if (available && differences.isEmpty()) Text("القيم متطابقة؛ تغيّر إصدار السجل فقط.")
                AppTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("العودة دون تغيير المسودة")
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onKeepDraft, enabled = available) { Text("متابعة بمسودتي بعد المراجعة") }
        },
        dismissButton = {
            AppTextButton(onClick = onUseSaved, enabled = available) { Text("استخدام النسخة المحفوظة") }
        }
    )
}
