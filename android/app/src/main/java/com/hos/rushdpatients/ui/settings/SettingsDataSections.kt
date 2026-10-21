package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.sync.CsvSchema

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsSyncContent(state: SettingsUiState, viewModel: SettingsViewModel, showRecovery: Boolean, onToggleRecovery: () -> Unit, onUndoRequested: () -> Unit) {
    SettingsSubheading("جلب البيانات ونشرها")
    Text("التعديلات محفوظة على الجهاز. «مراجعة ونشر» تشارك النسخة المحلية؛ «جلب البيانات» يسترجع النسخة المنشورة بعد التأكيد.")

    state.lastCsvSync?.let {
        Text(
            "آخر اتصال ببيانات المرضى: $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    state.lastOperation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

    LoadingButton(
        text = "جلب أحدث البيانات من تليجرام",
        outlined = true,
        loading = state.syncing,
        enabled = !state.exportingCsv && !state.backupBusy,
        onClick = viewModel::bringLatest,
        modifier = Modifier.fillMaxWidth()
    )
    LoadingButton(
        text = "مراجعة ونشر بيانات المناوبة",
        outlined = true,
        loading = state.syncing,
        enabled = !state.exportingCsv && !state.backupBusy,
        onClick = viewModel::uploadCurrent,
        modifier = Modifier.fillMaxWidth()
    )
    if (state.canUndoDownload) {
        AppTextButton(onClick = onToggleRecovery) {
            Text(if (showRecovery) "إغلاق أدوات الاستعادة" else "استعادة بيانات قبل آخر جلب")
        }
        if (showRecovery) {
            Text("يعيد العرض المحلي قبل آخر جلب؛ لا يغيّر النسخة المنشورة أو سجل التسليم.",
                style = MaterialTheme.typography.bodySmall)
            AppOutlinedButton(onClick = onUndoRequested,
                enabled = !state.syncing && !state.backupBusy,
                modifier = Modifier.fillMaxWidth()) { Text("مراجعة الاستعادة المحلية") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsFilesContent(state: SettingsUiState, viewModel: SettingsViewModel, canManageProject: Boolean, onOpenVbaImport: () -> Unit) {
    SettingsSubheading("ملفات المرضى")
    Text("الاستيراد: اختيار ملف ← مراجعة التغييرات ← تأكيد الحفظ المحلي. النشر إلى المشروع خطوة مستقلة.", style = MaterialTheme.typography.bodySmall)
    LoadingButton(
        text = "حفظ بيانات المرضى CSV",
        outlined = true,
        loading = state.exportingCsv,
        onClick = viewModel::exportCsv,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.syncing
    )
    if (canManageProject) {
        AppOutlinedButton(
            onClick = onOpenVbaImport,
            modifier = Modifier.fillMaxWidth()
        ) { Text("استيراد بيانات من الإكسل") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsBackupContent(state: SettingsUiState, onCreateBackup: () -> Unit, onRestoreBackup: () -> Unit) {
    SettingsSubheading("نسخة مشفرة في ملف")
    Text(
        "لحفظ بيانات المناوبة في ملف أو استعادتها محلياً؛ النشر إلى المشروع خطوة مستقلة.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LoadingButton(
        text = "إنشاء نسخة مشفرة",
        outlined = true,
        loading = state.backupBusy,
        enabled = !state.syncing,
        onClick = onCreateBackup,
        modifier = Modifier.fillMaxWidth()
    )
    AppOutlinedButton(
        onClick = onRestoreBackup,
        enabled = !state.backupBusy && !state.syncing,
        modifier = Modifier.fillMaxWidth()
    ) { Text("استعادة نسخة مشفرة") }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsSubheading("بعد الاستعادة المحلية")
    Text(
        "بعد الاستعادة افتح «جلب البيانات ونشرها» لعرض النسخة المحلية الكاملة ومراجعتها قبل النشر. يشمل الاستبدال الحذف والترتيب وأطباء المناوبة، ويتوقف إذا تغيرت البيانات بعد المراجعة.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsStorageContent(state: SettingsUiState) {
    Text("حجم قاعدة البيانات: ${state.databaseSize}")
    Text("إصدار تنسيق CSV: v${CsvSchema.CURRENT_VERSION}")
    Text("المناوبات المحفوظة: ${state.storedShifts}")
    Text("سجلات المرضى: ${state.totalStoredPatients}")
    Text("في سلة المحذوفات: ${state.deletedPatients}")
    Text("معرّف الجهاز: ${state.deviceId}", style = MaterialTheme.typography.bodySmall)
    Text(
        "آخر نسخة مشفرة: ${state.lastEncryptedBackup ?: "لم تُنشأ نسخة بعد"}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
}
