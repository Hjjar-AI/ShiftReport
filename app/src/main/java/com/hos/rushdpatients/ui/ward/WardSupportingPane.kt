package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.domain.report.ReportReadiness

@Composable
internal fun WardSupportingPane(
    state: WardUiState,
    freshnessLabel: String,
    online: Boolean,
    onReviewReport: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val warnings = ReportReadiness.warnings(state.patients)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("جاهزية التقرير والنشاط", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        Text("${state.patients.size} مريض · $freshnessLabel")
        if (!online) Text("غير متصل — تعرض البيانات المحلية", color = MaterialTheme.colorScheme.error)
        if (warnings.isEmpty()) Text("لا توجد ملاحظات اكتمال في الفحص الحالي")
        warnings.forEach { Text("• $it") }
        if (state.mergeConflicts.isNotEmpty()) {
            Text("${state.mergeConflicts.size} تعارضات مزامنة تحتاج المراجعة", color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = onReviewReport, enabled = state.shift != null, modifier = Modifier.fillMaxWidth()) {
            Text("مراجعة التقرير")
        }
        Divider()
        Text("النشاط المحلي الأخير", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        state.activityError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val recent = state.recentActivity.filter { it.patientId != null }.take(5)
        if (recent.isEmpty()) Text("لا توجد تغييرات محلية مسجلة")
        recent.forEach { entry ->
            ActivityEntry(entry, state.patients.firstOrNull { it.id == entry.patientId }?.name)
            Divider()
        }
        TextButton(onClick = onActivity) { Text("فتح مركز النشاط") }
        Text("اختر مريضاً من القائمة لعرض ملفه هنا.", style = MaterialTheme.typography.bodySmall)
    }
}
