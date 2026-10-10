package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.domain.report.ReportReadiness

@Composable
internal fun WardSupportingPane(
    state: WardUiState,
    freshnessLabel: String,
    online: Boolean,
    isAdmin: Boolean,
    onReviewReport: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val warnings = ReportReadiness.warnings(state.patients)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)) {
        Text(if (isAdmin) "جاهزية التقرير والنشاط" else "جاهزية التقرير", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        Text("${state.patients.size} مريض")
        PatientSyncNotice(state.syncStatus, freshnessLabel, online, state.mergeConflicts.size)
        if (warnings.isEmpty()) Text("لا توجد ملاحظات اكتمال في الفحص الحالي")
        warnings.forEach { Text("• $it") }
        AppButton(onClick = onReviewReport, enabled = state.shift != null, modifier = Modifier.fillMaxWidth()) {
            Text("مراجعة التقرير")
        }
        if (isAdmin) {
            Divider()
            Text("النشاط المحلي الأخير", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            state.activityError?.let { AppNotice(it, kind = NoticeKind.ERROR,
                actionLabel = "فتح مركز النشاط", onAction = onActivity) }
            val recent = state.recentActivity.filter { it.patientId != null }.take(5)
            if (recent.isEmpty()) Text("لا توجد تغييرات محلية مسجلة")
            recent.forEach { entry ->
                ActivityEntry(entry, state.patients.firstOrNull { it.id == entry.patientId }?.name)
                Divider()
            }
            AppTextButton(onClick = onActivity) { Text("فتح مركز النشاط") }
        }
        Text("اختر مريضاً من القائمة لعرض ملفه هنا.", style = MaterialTheme.typography.bodySmall)
    }
}
