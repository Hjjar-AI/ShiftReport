package com.hos.rushdpatients.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hos.rushdpatients.data.model.PatientBadgePriority


/** Badge severity means the same thing in every editor and patient view. */
@Composable
fun patientBadgeColors(priority: PatientBadgePriority?): ContainerColors {
    val clinical = LocalClinicalColors.current
    return when (priority) {
        PatientBadgePriority.HIGH -> ContainerColors(clinical.urgentContainer, clinical.onUrgentContainer)
        PatientBadgePriority.MEDIUM -> ContainerColors(clinical.warningContainer, clinical.onWarningContainer)
        PatientBadgePriority.LOW, null -> ContainerColors(
            MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
