package com.hos.rushdpatients.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Card density changes internal rhythm, never typography or action target sizes. */
data class PatientCardDensity(val padding: PaddingValues, val sectionGap: Dp)

object UiPatientCard {
    val compact = PatientCardDensity(
        PaddingValues(horizontal = UiSpacing.small, vertical = UiSpacing.tiny), UiSpacing.small)
    val normal = PatientCardDensity(
        PaddingValues(horizontal = UiSpacing.medium, vertical = UiSpacing.small), UiSpacing.medium)
    val listGap = UiSpacing.medium
    fun density(compact: Boolean): PatientCardDensity = if (compact) this.compact else normal
}

/** Neutral surfaces and interactive emphasis; clinical severity has its own palette. */
object UiBorder {
    val normalWidth = 1.dp
    val selectedWidth = 2.dp
    val focusedWidth = 2.dp

    @Composable
    fun stroke(selected: Boolean = false, focused: Boolean = false): BorderStroke = BorderStroke(
        when { focused -> focusedWidth; selected -> selectedWidth; else -> normalWidth },
        if (selected || focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
}

object UiElevation {
    val flat = 0.dp
    val navigation = 2.dp
    val actionBar = 3.dp
}

object UiOpacity {
    const val enabled = 1f
    const val disabledContent = .38f
    const val disabledContainer = .12f
    fun content(enabled: Boolean): Float = if (enabled) this.enabled else disabledContent
}

object UiMotion {
    const val expandMillis = 160
    const val collapseMillis = 130
    const val highlightMillis = 220
}

/** Caps for scrollable bodies; available parent space can always make them smaller. */
object UiDialog {
    val compactBodyMaxHeight = 260.dp
    val bodyMaxHeight = 420.dp
    val reviewBodyMaxHeight = 440.dp
    val sheetMaxHeight = 640.dp
    const val availableHeightFraction = .85f
}

/** Purpose-specific thresholds stay independent even when their current values match. */
object UiLayout {
    val fieldPairMinWidth = 360.dp
    val patientGridMinWidth = 360.dp
    val patientCardColumnsMinWidth = 440.dp
    val patientFormMaxWidth = 840.dp
    val navigationBarMaxWidth = 480.dp
    val navigationRailWidth = 80.dp
    val toolbarActionsMinWidth = 240.dp
    val foldClearance = UiSpacing.screen
    val foldPaneMinWidth = 360.dp
    val paneMinHeight = 360.dp
    val splitMinWidth = 760.dp
    val primaryPaneMinWidth = 320.dp
    val supportingPaneMinWidth = 360.dp
    val mediumSupportMinWidth = 520.dp
    val mediumSupportMinHeight = 640.dp
    val mediumSupportHeight = 240.dp
}
