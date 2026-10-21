package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
internal fun SettingsSection(
    title: String,
    showHeader: Boolean,
    summary: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    onPositioned: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth().onGloballyPositioned { onPositioned(it.positionInParent().y.roundToInt()) }) {
        Column(Modifier.fillMaxWidth().padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            if (showHeader) com.hos.rushdpatients.ui.components.AppSectionHeader(title = title,
                expanded = if (onToggle == null) null else expanded, onToggle = onToggle, summary = if (expanded) null else summary)
            if (expanded) {
                if (showHeader) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}

@Composable
internal fun SettingsSubheading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun SupervisorGroupEditor(
    supervisor: Doctor,
    saving: Boolean,
    enabled: Boolean,
    onSave: (Doctor, String) -> Unit
) {
    var baseline by remember(supervisor.id) { mutableStateOf(supervisor) }
    var chatId by remember(supervisor.id) {
        mutableStateOf(supervisor.supervisorGroupChatId?.toString().orEmpty())
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UiSpacing.micro),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)
    ) {
        Text(supervisor.fullName, style = MaterialTheme.typography.bodyMedium)
        AppTextField(
            value = chatId,
            onValueChange = { value ->
                chatId = value.filter { it.isDigit() || it == '-' }
            },
            label = { Text("معرف مجموعة تليجرام") },
            supportingText = { Text("-1001234567890") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        if (supervisor != baseline) {
            AppNotice("تغيّر سجل المشرف؛ راجع النسخة المحفوظة قبل الحفظ", kind = com.hos.rushdpatients.ui.components.NoticeKind.WARNING)
            AppTextButton(onClick = {
                baseline = supervisor
                chatId = supervisor.supervisorGroupChatId?.toString().orEmpty()
            }, enabled = enabled) { Text("تحميل النسخة المحفوظة") }
        }
        LoadingButton(
            outlined = true,
            text = if (chatId.isBlank()) "إزالة المعرف" else "حفظ",
            loading = saving,
            onClick = { onSave(baseline, chatId) },
            enabled = enabled && supervisor == baseline && chatId.trim() != supervisor.supervisorGroupChatId?.toString().orEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal enum class SettingsSectionId(
    val title: String,
    val workspace: SettingsWorkspace,
    val adminOnly: Boolean = false
) {
    APPEARANCE("المظهر والخط", SettingsWorkspace.APPEARANCE),
    PATIENT_CARDS("بطاقات المرضى", SettingsWorkspace.APPEARANCE),
    WORKFLOW("الجولات والمناوبات", SettingsWorkspace.WORKFLOW),
    REPORT("التقارير وملفات PDF", SettingsWorkspace.REPORTS),
    SYNC("جلب البيانات ونشرها", SettingsWorkspace.DATA),
    FILES("ملفات المرضى", SettingsWorkspace.FILES),
    BACKUP("النسخ الاحتياطي والاستعادة", SettingsWorkspace.BACKUP),
    CONNECTION("اتصال المشروع", SettingsWorkspace.PROJECT),
    INVITATIONS("ضم جهاز جديد", SettingsWorkspace.INVITATIONS, true),
    DESTINATIONS("مجموعات تقارير المشرفين", SettingsWorkspace.DESTINATIONS, true),
    RESET("بداية فارغة", SettingsWorkspace.RESET, true),
    SECURITY("الحساب وقفل التطبيق", SettingsWorkspace.SECURITY),
    STORAGE("التخزين ومعلومات الجهاز", SettingsWorkspace.DIAGNOSTICS)
}

internal fun SettingsSectionId.summary(state: SettingsUiState): String = when (this) {
    SettingsSectionId.APPEARANCE -> "${state.appearance.arabicLabel} · ${state.appTheme.arabicLabel} · خط ${state.fontScale.arabicLabel}"
    SettingsSectionId.PATIENT_CARDS -> listOf(
        if (state.patientDetailsExpanded) "التفاصيل موسعة" else "التفاصيل مطوية",
        if (state.patientTwoColumn) "عمودان عند توفر المساحة" else "عمود واحد",
        if (state.patientCompactDensity) "مسافات مضغوطة" else "مسافات عادية"
    ).joinToString(" · ")
    SettingsSectionId.WORKFLOW -> "حفظ والتالي: " + (if (state.saveAndNext) "مفعل" else "متوقف") +
        " · اقتراح نسخ المناوبة: " + if (state.guidedRollover) "مفعل" else "متوقف"
    SettingsSectionId.REPORT -> if (state.reportAsPdf) "PDF · ${state.pdfStyle.arabicLabel}" else "إرسال كنص"
    SettingsSectionId.SYNC -> "جلب نسخة كاملة · مراجعة ونشر · تراجع عن الجلب"
    SettingsSectionId.FILES -> "تصدير CSV · استيراد مرضى"
    SettingsSectionId.BACKUP -> "نسخة مشفرة · استعادة نسخة محفوظة"
    SettingsSectionId.CONNECTION -> "إعادة الاتصال بالمشروع الحالي"
    SettingsSectionId.INVITATIONS -> "ملف انضمام محمي بكلمة مرور"
    SettingsSectionId.DESTINATIONS -> "وجهة تقرير كل مشرف"
    SettingsSectionId.RESET -> "المدير الدائم فقط · مراجعة وتأكيد"
    SettingsSectionId.SECURITY -> "قفل تلقائي: ${state.autoLockMinutes} د · " + if (state.biometricEnabled) "البصمة مفعلة" else "البصمة متوقفة"
    SettingsSectionId.STORAGE -> "${state.databaseSize} · ${state.storedShifts} مناوبة محفوظة"
}
