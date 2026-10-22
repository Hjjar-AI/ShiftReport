package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.hos.rushdpatients.ui.theme.*

/** Informational badges have no click semantics; filter chips retain selected state and 48dp targets. */
@Composable
fun AppChip(text: String, colors: ContainerColors = toneColors(Tone.NEUTRAL), outlined: Boolean = false) {
    Surface(color = colors.container, contentColor = colors.content,
        border = if (outlined) UiBorder.stroke() else null, shape = MaterialTheme.shapes.small) {
        Text(text, Modifier.padding(UiPadding.listItem), style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterChip(selected = selected, onClick = onClick, modifier = modifier.heightIn(min = UiSpacing.touchTarget), enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) })
}
