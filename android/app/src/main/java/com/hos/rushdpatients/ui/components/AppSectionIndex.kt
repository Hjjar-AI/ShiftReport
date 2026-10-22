package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiTypography
import com.hos.rushdpatients.ui.theme.UiOpacity
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Selection follows physical section positions, including the final short section. */
@Composable
fun <T> activeAppSection(sections: List<T>, offsets: Map<T, Int>, scroll: ScrollState): T {
    val threshold = with(LocalDensity.current) { UiSpacing.medium.roundToPx() }
    val active by remember(sections, offsets, scroll, threshold) {
        derivedStateOf {
            if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue) sections.last()
            else sections.filter { (offsets[it] ?: Int.MAX_VALUE) <= scroll.value + threshold }
                .maxByOrNull { offsets[it] ?: Int.MIN_VALUE } ?: sections.first()
        }
    }
    return active
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun <T> AppSectionIndex(sections: List<T>, selected: T, label: (T) -> String,
    onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    centered: Boolean = false, compact: Boolean = false) {
    val requests = remember(sections) { sections.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(selected) { requests[selected]?.bringIntoView() }
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val rowModifier = if (centered) Modifier.horizontalScroll(scroll).widthIn(min = maxWidth)
            else Modifier.fillMaxWidth().horizontalScroll(scroll)
        Row(rowModifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny, if (centered) Alignment.CenterHorizontally else Alignment.Start)) {
            sections.forEach { section ->
                val anchor = Modifier.heightIn(min = UiSpacing.touchTarget).widthIn(min = UiSpacing.touchTarget)
                    .bringIntoViewRequester(requests.getValue(section))
                if (compact) {
                    val active = section == selected
                    Box(anchor.selectable(selected = active, enabled = enabled, role = Role.Tab,
                        onClick = { onSelect(section) }), contentAlignment = Alignment.Center) {
                        Surface(shape = MaterialTheme.shapes.small,
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = (if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                .copy(alpha = UiOpacity.content(enabled))) {
                            Text(label(section), modifier = Modifier.padding(horizontal = UiSpacing.screen, vertical = UiSpacing.tiny),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) UiTypography.emphasizedWeight else FontWeight.Normal, maxLines = 1)
                        }
                    }
                } else FilterChip(selected = section == selected, enabled = enabled, onClick = { onSelect(section) },
                    label = { Text(label(section)) }, modifier = anchor)
            }
        }
    }
}
