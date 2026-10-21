package com.hos.rushdpatients.ui.report

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

internal enum class ReportFlowStep(val arabicLabel: String) {
    DOCTORS("الأطباء"), DESTINATION("الوجهة"), PREVIEW("المعاينة"), SEND("الإرسال");

    fun previous() = entries[(ordinal - 1).coerceAtLeast(0)]
    fun next() = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
}

/** Progress labels wrap at large font sizes; Next/Previous own navigation and its guards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportFlowProgress(current: ReportFlowStep) {
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
                    stateDescription = if (active) "الخطوة الحالية" else "خطوة ${uiNumber(step.ordinal + 1)}"
                }) {
                Text("${uiNumber(step.ordinal + 1)} · ${step.arabicLabel}",
                    Modifier.padding(UiPadding.content), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
