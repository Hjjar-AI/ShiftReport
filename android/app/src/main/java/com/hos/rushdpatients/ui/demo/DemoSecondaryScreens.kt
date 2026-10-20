package com.hos.rushdpatients.ui.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.domain.report.ReportReadiness
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.settings.SettingsAppearanceSection
import com.hos.rushdpatients.ui.settings.SettingsUiState
import com.hos.rushdpatients.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DemoReportContent(
    state: DemoWardState, onDoctors: () -> Unit,
    onExport: (PdfExportOptions, PdfStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    var style by rememberSaveable { mutableStateOf(PdfStyle.CLASSIC) }
    var orientation by rememberSaveable { mutableStateOf(PdfOrientation.PORTRAIT) }
    var color by rememberSaveable { mutableStateOf(PdfColorPreset.TEAL) }
    var weight by rememberSaveable { mutableStateOf(PdfRuleWeight.MEDIUM) }
    var contrast by rememberSaveable { mutableStateOf(PdfRuleContrast.MEDIUM) }
    var shading by rememberSaveable { mutableStateOf(PdfRowShading.AUTO) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var showContents by rememberSaveable { mutableStateOf(false) }
    val warnings = ReportReadiness.warnings(state.patients)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        AppSection("أطباء المناوبة", colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer),
            titleColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Text(state.shiftDoctors.joinToString("، ") { it.fullName }.ifBlank { "اختر أطباء المناوبة" })
            AppOutlinedButton(onClick = onDoctors, enabled = !state.exporting, modifier = Modifier.fillMaxWidth()) {
                Text("اختيار أطباء المناوبة")
            }
        }
        AppSection("النطاق والوجهة والصيغة") {
            Text("${state.date} · ${uiNumber(state.summary.patientCount)} مريض · ${uiNumber(state.summary.escortCount)} مرافق")
            Text("جميع مرضى المناوبة · حفظ ملف PDF محلي فقط", style = MaterialTheme.typography.bodySmall)
            AppPdfStylePicker(selected = style, onSelected = { style = it }, enabled = !state.exporting)
            Text("لا يوجد نشر أو إرسال في التجربة. تحمل كل صفحة علامة عرض تجريبي.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (warnings.isNotEmpty()) AppSection("ملاحظات جاهزية التقرير") {
            warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        AppTextButton(onClick = { advanced = !advanced }, enabled = !state.exporting) {
            Text(if (advanced) "إخفاء خيارات PDF المتقدمة" else "خيارات PDF المتقدمة")
        }
        if (advanced) AppSection("خيارات PDF المتقدمة") {
            if (style != PdfStyle.CARDS) AppToggleRow("صفحة أفقية",
                checked = orientation == PdfOrientation.LANDSCAPE,
                onCheckedChange = { orientation = if (it) PdfOrientation.LANDSCAPE else PdfOrientation.PORTRAIT },
                enabled = !state.exporting)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                PdfColorPreset.entries.forEach { value ->
                    FilterChip(selected = color == value, onClick = { color = value },
                        enabled = !state.exporting, label = { Text(value.arabicLabel) })
                }
            }
            AppPdfAppearanceControls(weight, contrast, shading, dark = false,
                onWeight = { weight = it }, onContrast = { contrast = it }, onShading = { shading = it },
                onPreset = { weight = it.weight; contrast = it.contrast; shading = it.shading },
                enabled = !state.exporting, showShading = style != PdfStyle.CARDS)
        }
        LoadingButton(text = "حفظ PDF تجريبي", loading = state.exporting,
            enabled = !state.exporting && state.patients.isNotEmpty() && state.shiftDoctorIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(), onClick = {
                onExport(PdfExportOptions(orientation = orientation, colorPreset = color,
                    ruleWeight = weight, ruleContrast = contrast, rowShading = shading), style)
            })
        AppTextButton(onClick = { showContents = !showContents }) {
            Text(if (showContents) "طي محتوى التقرير" else "مراجعة محتوى التقرير")
        }
        if (showContents) state.ordered.forEach { patient ->
            AppSection("${uiNumber(patient.sortOrder)}. ${patient.name}") {
                AppClinicalSection("التشخيص", patient.initialDiagnosis)
                AppClinicalSection("العلاج", patient.treatmentPlan)
                AppClinicalSection("المتابعة", patient.followUp)
                AppClinicalSection("التحاليل", patient.labs, kind = ClinicalTextKind.LABS)
                Text(PatientTasks.summary(patient.tasks, state.names), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun DemoTeamContent(
    state: DemoWardState, onDoctors: () -> Unit, onActor: (String) -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        AppSection("أطباء المناوبة") {
            Text(state.shiftDoctors.joinToString("، ") { it.fullName })
            AppOutlinedButton(onClick = onDoctors, modifier = Modifier.fillMaxWidth()) { Text("تعديل أطباء المناوبة") }
        }
        AppSection("الطبيب الحالي في التجربة") {
            Text("اختيار الطبيب يغيّر «مرضاي» وهوية إتمام المهام في الجلسة فقط.", style = MaterialTheme.typography.bodySmall)
            state.doctors.forEach { doctor ->
                AppChoiceRow(label = "${doctor.fullName} · ${doctor.clinicalRole.arabicLabel}",
                    selected = state.actorId == doctor.id, onClick = { onActor(doctor.id) })
            }
        }
    }
}

@Composable
internal fun DemoPreferencesContent(
    appearance: AppAppearance, onAppearance: (AppAppearance) -> Unit,
    accent: AppThemePreset, onAccent: (AppThemePreset) -> Unit,
    font: AppFontScale, onFont: (AppFontScale) -> Unit,
    expanded: Boolean, twoColumn: Boolean, compact: Boolean,
    saveAndNext: Boolean, onSaveAndNext: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        AppNotice("تفضيلات مؤقتة لهذه التجربة؛ إعدادات مشروعك لا تتغير.")
        AppSection("المظهر والخط") {
            SettingsAppearanceSection(SettingsUiState(appearance = appearance, appTheme = accent,
                fontScale = font, patientDetailsExpanded = expanded,
                patientTwoColumn = twoColumn, patientCompactDensity = compact),
                onAppearance = onAppearance, onTheme = onAccent, onFont = onFont)
        }
        AppSection("الجولات") {
            AppToggleRow("حفظ والتالي أثناء الجولة", checked = saveAndNext, onCheckedChange = onSaveAndNext)
            Text("خيارات البطاقات والترتيب متاحة من «خيارات العرض» في قائمة المرضى.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
