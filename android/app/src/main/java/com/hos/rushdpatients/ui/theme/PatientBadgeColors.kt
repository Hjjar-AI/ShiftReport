package com.hos.rushdpatients.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hos.rushdpatients.data.model.PatientBadgePriority

data class PatientBadgeColors(val container: Color, val content: Color)

/** Badge severity means the same thing in every editor and patient view. */
@Composable
fun patientBadgeColors(priority: PatientBadgePriority?): PatientBadgeColors {
    val clinical = LocalClinicalColors.current
    return when (priority) {
        PatientBadgePriority.HIGH -> PatientBadgeColors(clinical.urgentContainer, clinical.onUrgentContainer)
        PatientBadgePriority.MEDIUM -> PatientBadgeColors(clinical.warningContainer, clinical.onWarningContainer)
        PatientBadgePriority.LOW, null -> PatientBadgeColors(
            MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
