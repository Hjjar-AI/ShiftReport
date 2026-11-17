package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.*

class AppMetric(val label: String, val count: Int, val tone: Tone = Tone.NEUTRAL, val onClick: () -> Unit)

/** Two equal tiles when each label has room; large text/narrow panes stack without truncation. */
@Composable
fun AppMetricGrid(metrics: List<AppMetric>) {
    AppAdaptiveRow(itemCount = metrics.size, minimumRowWidth = UiLayout.metricPairMinWidth,
        maxColumns = 2, matchHeights = true, rowGap = UiSpacing.controlGap,
        columnGap = UiSpacing.controlGap) { index, cellModifier ->
        val metric = metrics[index]
                        val colors = toneColors(metric.tone)
                        Surface(onClick = metric.onClick, modifier = cellModifier.heightIn(min = UiSpacing.touchTarget),
                            color = colors.container, contentColor = colors.content, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.padding(com.hos.rushdpatients.ui.theme.uiContentPadding()), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                AppText(uiNumber(metric.count), style = MaterialTheme.typography.headlineSmall)
                                AppText(metric.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
    }
}
