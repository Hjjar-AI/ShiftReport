package com.hos.rushdpatients.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Card density changes internal rhythm, never typography or action target sizes. */
data class PatientCardDensity(val padding: PaddingValues, val sectionGap: Dp)

object UiPatientCard {
    val compact = PatientCardDensity(
        PaddingValues(horizontal = UiSpacing.small, vertical = UiSpacing.tiny), UiSpacing.small)
    val normal = PatientCardDensity(
        PaddingValues(horizontal = UiSpacing.section, vertical = UiSpacing.screen), UiSpacing.screen)
    val listGap = UiSpacing.screen
    @Composable
    fun density(compact: Boolean): PatientCardDensity {
        val profile = LocalUiStyle.current
        return when (LocalUiPreferences.current.density) {
            UiDensity.COMPACT -> profile.compactDensity
            UiDensity.RELAXED -> UiStyleCatalog.relaxed.normalDensity
            UiDensity.NORMAL -> if (compact) profile.compactDensity else profile.normalDensity
        }
    }

    @Composable
    fun border(selected: Boolean = false): BorderStroke =
        if (selected) UiBorder.stroke(selected = true)
        else UiBorder.cardStroke()
}

/** Neutral surfaces and interactive emphasis; clinical severity has its own palette. */
object UiBorder {
    val hairline = 0.5.dp
    val strong = 1.5.dp
    val normalWidth = 1.dp
    val selectedWidth = 2.dp
    val focusedWidth = 2.dp

    @Composable
    fun cardStroke(): BorderStroke = BorderStroke(LocalUiStyle.current.normalBorder,
        lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, 0.65f))

    @Composable
    fun stroke(selected: Boolean = false, focused: Boolean = false): BorderStroke = BorderStroke(
        when { focused -> focusedWidth; selected -> LocalUiStyle.current.selectedBorder; else -> LocalUiStyle.current.normalBorder },
        if (selected || focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
}

object UiElevation {
    val flat = 0.dp
    val low = 1.dp
    val raised = 2.dp
    val overlay = 6.dp
    val navigation = raised
    val actionBar = 3.dp
}

object UiOpacity {
    const val containerSoft = .3f
    const val containerMuted = .5f
    const val enabled = 1f
    const val disabledContent = .38f
    const val disabledContainer = .12f
    fun content(enabled: Boolean): Float = if (enabled) this.enabled else disabledContent
}

object UiMotion {
    const val placementMillis = 200
    const val fastMillis = 90
    const val indicatorMillis = 160
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
    val splashMaxWidth = 520.dp
    val aboutMaxWidth = 640.dp
    val contentMaxWidth = 720.dp
    val fetchPullDistance = 72.dp
    val roundsSwipeDistance = 64.dp
    val metricPairMinWidth = 280.dp
    const val defaultListFraction = .42f
    const val paneAdjustmentFraction = .05f
    const val expandedWindowWidthDp = 600
    const val expandedWindowMinHeightDp = 480
    val fieldPairMinWidth = 360.dp
    val patientFieldPairMinWidth = 280.dp
    val patientGridMinWidth = 360.dp
    val patientCardColumnsMinWidth = 440.dp
    val emptyWardMaxWidth = 520.dp
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
