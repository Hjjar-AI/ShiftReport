package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.connection.ProjectConnectionAction
import com.hos.rushdpatients.ui.components.LoadingButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsConnectionContent(canManageProject: Boolean) {
    Text(
        "إعادة الاتصال بالمشروع الحالي باستخدام ملف انضمام محمي، أو استبدال رمز البوت بصلاحية المدير.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ProjectConnectionAction(isAdmin = canManageProject)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsResetContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (state.isPermanentAdmin) {
        SettingsSubheading("بداية فارغة للمشروع")
        Text("مسح المرضى والمناوبات فقط مع إبقاء الأطباء والحسابات والاتصال وسجل تليجرام. لا يتطلب جلب بيانات المرضى أو مراجعة تقرير؛ ستظهر رسالة تأكيد المسح.",
            style = MaterialTheme.typography.bodySmall)
        AppOutlinedButton(onClick = viewModel::prepareEmptyStart,
            enabled = !state.emptyStartBusy && !state.syncing && !state.backupBusy && !state.provisioningBusy,
            modifier = Modifier.fillMaxWidth()) { Text("بدء بداية فارغة…", color = MaterialTheme.colorScheme.error) }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsInvitationsContent(state: SettingsUiState, onCreateInvitation: () -> Unit) {
    Text("ضم أجهزة جديدة", style = MaterialTheme.typography.labelMedium)
    Text(
        "أنشئ ملفاً مشفراً يعبئ إعدادات المستشفى والبوت على جهاز العضو. أرسل عبارة المرور إليه عبر قناة منفصلة.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LoadingButton(
        text = "إنشاء ملف انضمام مشفر",
        outlined = true,
        loading = state.provisioningBusy,
        onClick = onCreateInvitation,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsDestinationsContent(state: SettingsUiState, viewModel: SettingsViewModel, onOpenDoctors: () -> Unit) {
    AppOutlinedButton(onClick = onOpenDoctors) { Text("سجل الأطباء — مراجعة ونشر الوجهات المحفوظة") }
    Text("مجموعات المشرفين", style = MaterialTheme.typography.labelMedium)
    Text(
        "معرّفات مجموعات تليجرام لاستقبال تقارير كل مشرف.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (state.supervisors.isEmpty()) {
        Text(
            "لا يوجد مشرفون",
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        state.supervisors.forEach { supervisor ->
            androidx.compose.runtime.key(supervisor.id) {
                SupervisorGroupEditor(
                    supervisor = supervisor,
                    saving = state.savingSupervisorGroupId == supervisor.id,
                    enabled = state.savingSupervisorGroupId == null,
                    onSave = { expected, value ->
                        viewModel.setSupervisorGroupChatId(expected, value)
                    }
                )
            }
        }
    }
}
