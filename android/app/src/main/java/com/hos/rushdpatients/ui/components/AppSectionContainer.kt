package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.uiContentGap
import com.hos.rushdpatients.ui.theme.uiContentPadding

/** Shared section geometry; callers retain form state, titles and operation guards. */
@Composable
fun AppSectionContainer(modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    header: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier = modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.fillMaxWidth().padding(uiContentPadding()),
            verticalArrangement = Arrangement.spacedBy(uiContentGap())) {
            header?.invoke()
            content()
        }
    }
}

/** Emits existing content in the owner's column; state and animations remain with that owner. */
@Composable
fun ColumnScope.AppCollapsibleSection(expanded: Boolean,
    header: @Composable () -> Unit, summary: @Composable () -> Unit = {},
    divider: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    header()
    summary()
    if (expanded) {
        if (divider) AppDivider()
        content()
    }
}
