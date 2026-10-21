package com.hos.rushdpatients.ui.report

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing

internal enum class ReportFlowStep(@StringRes private val arabicLabelRes: Int) {
    DOCTORS(R.string.ui_doctors), DESTINATION(R.string.ui_report_flow_step_003), PREVIEW(R.string.ui_report_flow_step_004), SEND(R.string.ui_report_flow_step_005);

    fun previous() = entries[(ordinal - 1).coerceAtLeast(0)]
    fun next() = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}

/** Progress labels wrap at large font sizes; Next/Previous own navigation and its guards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportFlowProgress(current: ReportFlowStep) {
    val uiText = rememberUiStrings()
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screen),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        ReportFlowStep.entries.forEach { step ->
            val active = step == current
            Surface(shape = MaterialTheme.shapes.small,
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    selected = active
                    stateDescription = if (active) uiText(R.string.ui_report_flow_step_001) else uiText(R.string.ui_report_flow_step_002, uiNumber(step.ordinal + 1))
                }) {
                Text("${uiNumber(step.ordinal + 1)} · ${step.arabicLabel(uiText)}",
                    Modifier.padding(UiPadding.content), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
