package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.theme.UiSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WardWorkflowStrip(state: WardUiState, onNext: () -> Unit, onTasks: () -> Unit) {
    val downloaded = state.lastDownloadAt?.let {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) +
            if (state.lastDownloadMessage != null && state.lastDownloadMessage == state.shift?.csvMessageId) "" else " (لنسخة سابقة)"
    } ?: "غير معروف على هذا الجهاز"
    val unresolved = if (state.deliveryLogUnavailable) "غير متاح" else state.unresolvedDeliveries.toString()
    val action = when {
        state.deliveryLogUnavailable || state.unresolvedDeliveries > 0 -> "سجل التسليم"
        state.syncStatus == PatientSyncStatus.PENDING && !state.isReadOnly -> "مراجعة ونشر"
        else -> "جلب البيانات"
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.small)) {
            Text("مناوبة ${state.shift?.date ?: "—"} · حالة بيانات الجهاز: ${state.syncStatus.arabicLabel}\nآخر تنزيل: $downloaded · تسليم غير مؤكد: $unresolved",
                style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                AppTextButton(onClick = onNext, enabled = state.shift != null && !state.syncing && !state.saving) { Text(action) }
                AppTextButton(onClick = onTasks, enabled = !state.loading && !state.saving && !state.syncing) { Text("مهام الجناح") }
            }
        }
    }
}
