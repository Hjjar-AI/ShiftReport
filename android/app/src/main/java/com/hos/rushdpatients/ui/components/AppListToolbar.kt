package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.uiContentGap

/** Shared search/filter rhythm; data predicates and list ordering stay with the screen. */
@Composable
fun AppListToolbar(query: String, onQueryChange: (String) -> Unit, searchLabel: String,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    filters: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(uiContentGap())) {
        AppSearchField(query, onQueryChange, searchLabel, enabled = enabled)
        filters()
    }
}
