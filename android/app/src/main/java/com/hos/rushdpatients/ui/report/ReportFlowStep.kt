package com.hos.rushdpatients.ui.report

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing

internal enum class ReportFlowStep(@StringRes private val arabicLabelRes: Int) {
    DOCTORS(R.string.ui_report_setup_step), PREVIEW(R.string.ui_report_review_send_step);

    fun previous() = entries[(ordinal - 1).coerceAtLeast(0)]
    fun next() = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}

/** The top bar names the stage; this compact, noninteractive indicator announces progress. */
@Composable
internal fun ReportFlowProgress(current: ReportFlowStep) {
    val progress by animateFloatAsState((current.ordinal + 1).toFloat() / ReportFlowStep.entries.size,
        tween(com.hos.rushdpatients.ui.theme.LocalUiStyle.current.indicatorMillis), label = "reportProgress")
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screen, vertical = UiSpacing.tiny),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.weight(1f))
        Text("${uiNumber(current.ordinal + 1)}/${uiNumber(ReportFlowStep.entries.size)}",
            style = MaterialTheme.typography.labelMedium)
    }
}
