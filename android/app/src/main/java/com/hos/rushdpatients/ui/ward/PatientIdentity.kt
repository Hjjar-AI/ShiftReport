package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.ui.components.relativeFreshness
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

internal object PatientIdentityText {
    fun admission(uiText: UiStrings, patient: Patient) = uiText(R.string.ui_patient_identity_003, uiNumber(patient.admittanceNumber))
    fun personal(uiText: UiStrings, patient: Patient): String = buildString {
        append(if (patient.gender == Gender.MALE) uiText(R.string.ui_male) else uiText(R.string.ui_female))
        patient.age?.let { append(uiText(R.string.ui_patient_identity_004, uiNumber(it))) }
    }
    fun admissionDetails(uiText: UiStrings, patient: Patient): String = buildList {
        if (patient.admittanceNumber.isNotBlank()) add(uiText(R.string.ui_patient_identity_005, uiNumber(patient.admittanceNumber)))
        patient.admittanceDate?.let { date ->
            add(uiText(R.string.ui_patient_identity_006, uiNumber(date)))
            patient.admittanceDays?.let { add(uiText(R.string.ui_patient_identity_007, uiNumber(it))) }
        }
    }.joinToString(" · ")
    fun details(uiText: UiStrings, patient: Patient) = listOf(personal(uiText, patient), admissionDetails(uiText, patient)).filter(String::isNotBlank).joinToString(" · ")
}

@Composable
internal fun PatientName(name: String, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium) {
    Text(name, modifier = modifier.semantics { heading() }, style = style, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatientIdentityHeading(patient: Patient, modifier: Modifier = Modifier, pinned: Boolean = false,
    showFreshness: Boolean = false, nameStyle: TextStyle = MaterialTheme.typography.titleMedium,
    showPersonal: Boolean = false) {
    val uiText = rememberUiStrings()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        PatientName(patient.name, style = nameStyle)
        FlowRow(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            if (pinned) Icon(Icons.Filled.Bookmark, contentDescription = uiText(R.string.ui_patient_identity_001), modifier = Modifier.size(UiSize.iconSmall))
            if (patient.isPriority) Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Icon(Icons.Filled.Star, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(UiSize.iconSmall))
                Text(uiText(R.string.ui_patient_identity_002), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            val metadata = if (showPersonal) PatientIdentityText.details(uiText, patient) else PatientIdentityText.admission(uiText, patient)
            Text(metadata + if (showFreshness) " · ${relativeFreshness(uiText, patient.updatedAt)}" else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
