package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.appAccentColor

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppPalettePicker(selected: AppThemePreset, onSelected: (AppThemePreset) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        AppThemePreset.entries.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelected(option) },
                label = { Text(option.arabicLabel) },
                leadingIcon = {
                    Box(Modifier.size(UiSize.iconSmall).clip(CircleShape).background(appAccentColor(option, dark)))
                })
        }
    }
}
