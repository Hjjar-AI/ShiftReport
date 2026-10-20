package com.hos.rushdpatients.ui.ward

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
    fun admission(patient: Patient) = "رقم القبول الحالي: ${uiNumber(patient.admittanceNumber)}"
    fun personal(patient: Patient): String = buildString {
        append(if (patient.gender == Gender.MALE) "ذكر" else "أنثى")
        patient.age?.let { append(" · ${uiNumber(it)} سنة") }
    }
    fun admissionDetails(patient: Patient): String = buildList {
        if (patient.admittanceNumber.isNotBlank()) add("رقم ${uiNumber(patient.admittanceNumber)}")
        patient.admittanceDate?.let { date ->
            add("دخول ${uiNumber(date)}")
            patient.admittanceDays?.let { add("${uiNumber(it)} يوم") }
        }
    }.joinToString(" · ")
    fun details(patient: Patient) = listOf(personal(patient), admissionDetails(patient)).filter(String::isNotBlank).joinToString(" · ")
}

@Composable
internal fun PatientName(name: String, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium) {
    Text(name, modifier = modifier.semantics { heading() }, style = style, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun PatientIdentityHeading(patient: Patient, modifier: Modifier = Modifier, pinned: Boolean = false,
    showFreshness: Boolean = false, nameStyle: TextStyle = MaterialTheme.typography.titleMedium,
    showPersonal: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        PatientName(patient.name, style = nameStyle)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            if (pinned) Icon(Icons.Filled.Bookmark, contentDescription = "مثبت للعرض", modifier = Modifier.size(UiSize.iconSmall))
            if (patient.isPriority) Icon(Icons.Filled.Star, contentDescription = "مريض ذو أولوية",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(UiSize.iconSmall))
            val metadata = if (showPersonal) PatientIdentityText.details(patient) else PatientIdentityText.admission(patient)
            Text(metadata + if (showFreshness) " · ${relativeFreshness(patient.updatedAt)}" else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
