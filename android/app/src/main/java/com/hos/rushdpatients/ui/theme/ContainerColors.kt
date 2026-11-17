package com.hos.rushdpatients.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ContainerColors(val container: Color, val content: Color)
enum class Tone { NEUTRAL, INFO, SUCCESS, WARNING, URGENT, PENDING, CONFLICT }

@Composable
fun toneColors(tone: Tone): ContainerColors {
    val scheme = MaterialTheme.colorScheme
    val clinical = LocalClinicalColors.current
    return when (tone) {
        Tone.CONFLICT -> ContainerColors(clinical.conflictContainer, clinical.onConflictContainer)
        Tone.NEUTRAL -> ContainerColors(scheme.surfaceVariant, scheme.onSurfaceVariant)
        Tone.INFO -> ContainerColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        Tone.SUCCESS -> ContainerColors(clinical.successContainer, clinical.onSuccessContainer)
        Tone.WARNING -> ContainerColors(clinical.warningContainer, clinical.onWarningContainer)
        Tone.PENDING -> ContainerColors(clinical.pendingContainer, clinical.onPendingContainer)
        Tone.URGENT -> ContainerColors(clinical.urgentContainer, clinical.onUrgentContainer)
    }
}

/** Solid accents share the same semantic roles as their containers. */
@Composable
fun toneAccent(tone: Tone): Color {
    val clinical = LocalClinicalColors.current
    return when (tone) {
        Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        Tone.INFO -> MaterialTheme.colorScheme.primary
        Tone.SUCCESS -> clinical.success
        Tone.WARNING -> clinical.warning
        Tone.URGENT -> clinical.urgent
        Tone.PENDING -> clinical.pending
        Tone.CONFLICT -> clinical.conflict
    }
}
