package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Presentation only: merge decisions and stale-draft actions remain owned by their dialogs. */
@Composable
fun AppValueComparison(title: String, firstLabel: String, firstValue: String, secondLabel: String, secondValue: String) {
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text("$firstLabel: ${firstValue.ifBlank { "فارغ" }}", style = MaterialTheme.typography.bodyMedium)
        Text("$secondLabel: ${secondValue.ifBlank { "فارغ" }}", style = MaterialTheme.typography.bodyMedium)
    }
}
