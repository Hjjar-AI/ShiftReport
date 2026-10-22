package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.*

class AppMetric(val label: String, val count: Int, val tone: Tone = Tone.NEUTRAL, val onClick: () -> Unit)

/** Two equal tiles when each label has room; large text/narrow panes stack without truncation. */
@Composable
fun AppMetricGrid(metrics: List<AppMetric>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columns = if (maxWidth >= UiLayout.metricPairMinWidth * scale) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
            metrics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
                    row.forEach { metric ->
                        val colors = toneColors(metric.tone)
                        Surface(onClick = metric.onClick, modifier = Modifier.weight(1f).heightIn(min = UiSpacing.touchTarget),
                            color = colors.container, contentColor = colors.content, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.padding(UiPadding.card), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                Text(uiNumber(metric.count), style = MaterialTheme.typography.headlineSmall)
                                Text(metric.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    if (columns == 2 && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
