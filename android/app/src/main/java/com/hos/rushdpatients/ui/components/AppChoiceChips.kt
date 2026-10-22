package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Single-choice presentation only; callers retain the value and all validation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> AppChoiceChips(
    options: List<T>, selected: T?, onSelect: (T) -> Unit, label: (T) -> String,
    modifier: Modifier = Modifier, title: String? = null, enabled: Boolean = true,
    leading: (@Composable (T) -> Unit)? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        title?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        FlowRow(Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.controlGap),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            options.forEach { option ->
                val optionIcon: (@Composable () -> Unit)? = if (leading == null) null else { { leading(option) } }
                AppFilterChip(selected = selected == option, onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                    enabled = enabled,
                    leadingIcon = optionIcon)
            }
        }
    }
}
