package com.hos.rushdpatients.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.ui.components.AppPalettePicker
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.ui.theme.RushdPatientsTheme
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.ward.PatientCard

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAppearanceSection(
    state: SettingsUiState,
    onAppearance: (AppAppearance) -> Unit,
    onTheme: (AppThemePreset) -> Unit,
    onFont: (AppFontScale) -> Unit
) {
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            Text("المظهر", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                AppAppearance.entries.forEach { option ->
                    FilterChip(selected = state.appearance == option,
                        onClick = { onAppearance(option) }, label = { Text(option.arabicLabel) })
                }
            }
            Text("اللون الأساسي", style = MaterialTheme.typography.labelMedium)
            AppPalettePicker(selected = state.appTheme, onSelected = onTheme)
            Text("حجم الخط", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                AppFontScale.entries.forEach { option ->
                    FilterChip(selected = state.fontScale == option,
                        onClick = { onFont(option) }, label = { Text(option.arabicLabel) })
                }
            }
        }
    }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= UiLayout.splitMinWidth * fontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
                controls(Modifier.weight(1f))
                SettingsPatientCardPreview(state, Modifier.weight(1f))
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            controls(Modifier.fillMaxWidth())
            SettingsPatientCardPreview(state)
        }
    }
}

/** A real card renderer with fixed, memory-only dummy content and no clinical actions. */
@Composable
internal fun SettingsPatientCardPreview(state: SettingsUiState, modifier: Modifier = Modifier) {
    val sample = remember {
        Patient(id = "appearance-preview", name = "شاي بالنعناع", admittanceNumber = "١٢٣",
            initialDiagnosis = "نص تشخيص تجريبي لمعاينة الخط",
            treatmentPlan = "نص تجريبي لخطة العلاج",
            followUp = "نص تجريبي للمتابعة", labs = "Hb: 12.5 g/dL",
            responsibleResidentId = "preview-resident", responsibleSpecialistId = "preview-supervisor")
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        Text("معاينة البطاقة · بيانات تجريبية فقط", style = MaterialTheme.typography.labelLarge)
        Text("تتبع المعاينة المظهر والخط ومسافات البطاقات المختارة.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Font scale comes from the same app-wide density provider as the ward.
        RushdPatientsTheme(preset = state.appTheme, appearance = state.appearance) {
            PatientCard(patient = sample, expanded = true, twoColumn = state.patientTwoColumn,
                compact = state.patientCompactDensity,
                doctorNames = mapOf("preview-resident" to "سنديان", "preview-supervisor" to "صنوبر"),
                readOnly = true, showViewControls = false, onClick = {}, onDelete = {})
        }
    }
}
