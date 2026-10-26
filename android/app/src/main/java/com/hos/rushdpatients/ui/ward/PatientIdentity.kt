package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.util.ShiftDate
import java.time.LocalDate
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

internal object PatientIdentityText {
    fun admission(uiText: UiStrings, patient: Patient) = uiText(R.string.ui_patient_identity_003, uiNumber(patient.admittanceNumber))
    fun personal(uiText: UiStrings, patient: Patient, dayLabel: LocalDate = ShiftDate.current()): String = buildString {
        append(if (patient.gender == Gender.MALE) uiText(R.string.ui_male) else uiText(R.string.ui_female))
        patient.ageOn(dayLabel)?.let { append(uiText(R.string.ui_patient_identity_004, uiNumber(it))) }
    }
    fun admissionDetails(uiText: UiStrings, patient: Patient, dayLabel: LocalDate = ShiftDate.current()): String = buildList {
        if (patient.admittanceNumber.isNotBlank()) add(uiText(R.string.ui_patient_identity_005, uiNumber(patient.admittanceNumber)))
        patient.admittanceDate?.let { date ->
            add(uiText(R.string.ui_patient_identity_006, uiNumber(date)))
            patient.admittanceDaysOn(dayLabel)?.let { add(uiText(R.string.ui_patient_identity_007, uiNumber(it))) }
        }
    }.joinToString(" · ")
    fun details(uiText: UiStrings, patient: Patient, dayLabel: LocalDate = ShiftDate.current()) = listOf(personal(uiText, patient, dayLabel), admissionDetails(uiText, patient, dayLabel)).filter(String::isNotBlank).joinToString(" · ")
}

@Composable
internal fun PatientName(name: String, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium) {
    Text(name, modifier = modifier.semantics { heading() }, style = style, fontWeight = com.hos.rushdpatients.ui.theme.UiTypography.emphasizedWeight,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatientIdentityHeading(patient: Patient, modifier: Modifier = Modifier, pinned: Boolean = false,
    nameStyle: TextStyle = MaterialTheme.typography.titleMedium,
    showPersonal: Boolean = false, showOrdinal: Boolean = false, showDemographics: Boolean = false) {
    val uiText = rememberUiStrings()
    val dayLabel = LocalWardDayLabel.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        PatientName(if (showOrdinal) "${uiNumber(patient.sortOrder)} · ${patient.displayName}" else patient.displayName, style = nameStyle)
        FlowRow(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            if (pinned) Icon(AppIcons.Bookmark, contentDescription = uiText(R.string.ui_patient_identity_001), modifier = Modifier.size(UiSize.iconSmall))
            if (patient.isPriority) Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Icon(AppIcons.Star, contentDescription = null,
                    tint = com.hos.rushdpatients.ui.theme.LocalClinicalColors.current.warning, modifier = Modifier.size(UiSize.iconSmall))
                Text(uiText(R.string.ui_patient_identity_002), style = MaterialTheme.typography.labelMedium,
                    color = com.hos.rushdpatients.ui.theme.LocalClinicalColors.current.warning)
            }
            val metadata = if (showPersonal) PatientIdentityText.details(uiText, patient, dayLabel) else listOfNotNull(
                PatientIdentityText.admission(uiText, patient),
                PatientIdentityText.personal(uiText, patient, dayLabel).takeIf { showDemographics }
            ).filter(String::isNotBlank).joinToString(" · ")
            Text(metadata,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
