package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Presentation only: merge decisions and stale-draft actions remain owned by their dialogs. */
@Composable
fun AppValueComparison(title: String, firstLabel: String, firstValue: String, secondLabel: String, secondValue: String) {
    val uiText = rememberUiStrings()
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(uiText(R.string.ui_app_value_comparison_002, firstLabel, firstValue.ifBlank { uiText(R.string.ui_app_value_comparison_001) }), style = MaterialTheme.typography.bodyMedium)
        Text(uiText(R.string.ui_app_value_comparison_002, secondLabel, secondValue.ifBlank { uiText(R.string.ui_app_value_comparison_001) }), style = MaterialTheme.typography.bodyMedium)
    }
}
