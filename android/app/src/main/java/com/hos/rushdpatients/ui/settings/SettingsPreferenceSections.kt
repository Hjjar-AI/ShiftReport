package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPatientCardsContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSubheading("عرض قائمة المرضى")
    Text(
        "تستخدم شاشة المناوبة بطاقة سريرية موحّدة حتى تبقى الشارات والأولوية واضحة بنفس المعنى للجميع.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    AppToggleRow(
        title = "عرض التفاصيل افتراضياً",
        subtitle = "يمكن تغييره من شريط شاشة المرضى",
        checked = state.patientDetailsExpanded,
        onCheckedChange = viewModel::setPatientDetailsExpanded
    )
    AppToggleRow(
        title = "بطاقة بعمودين",
        subtitle = "تعمل عند توفر عرض كافٍ وتعود لعمود واحد على الشاشة الضيقة",
        checked = state.patientTwoColumn,
        onCheckedChange = viewModel::setPatientTwoColumn
    )
    AppToggleRow(
        title = "عرض مضغوط",
        subtitle = "مسافات أصغر لعرض عدد أكبر من المرضى دون تصغير الخط",
        checked = state.patientCompactDensity,
        onCheckedChange = viewModel::setPatientCompactDensity
    )
    SettingsPatientCardPreview(state)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsWorkflowContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSubheading("أثناء الجولة")
    AppToggleRow(
        title = "حفظ والتالي أثناء الجولة",
        subtitle = "يُظهر زرّاً اختيارياً يحفظ المريض ويفتح التالي حسب التصفية والترتيب الحالي؛ متوقف افتراضياً",
        checked = state.saveAndNext,
        onCheckedChange = viewModel::setSaveAndNext
    )
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsSubheading("عند بدء مناوبة جديدة")
    AppToggleRow(
        title = "اقتراح نسخ المناوبة السابقة كاملة",
        subtitle = "عند بدء مناوبة فارغة، اعرض تأكيد نسخ جميع المرضى والمهام غير المكتملة؛ مستقل عن جلب تليجرام",
        checked = state.guidedRollover,
        onCheckedChange = viewModel::setGuidedRollover
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSecurityContent(state: SettingsUiState, viewModel: SettingsViewModel, onSignOut: () -> Unit) {
    AppToggleRow(
        title = "التحقق بالبصمة",
        subtitle = if (state.biometricAvailable) "استخدم بصمتك لفتح التطبيق"
        else "غير متوفرة على هذا الجهاز",
        checked = state.biometricEnabled,
        onCheckedChange = if (state.biometricAvailable) viewModel::setBiometric else null
    )

    Text("القفل التلقائي", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        listOf(1, 5, 15, 30).forEach { minutes ->
            FilterChip(
                selected = state.autoLockMinutes == minutes,
                onClick = { viewModel.setAutoLockMinutes(minutes) },
                label = { Text("$minutes د") }
            )
        }
    }
    AppOutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
}
