package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> Snackbar(data, shape = MaterialTheme.shapes.medium) }
}

@Composable
fun AppMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded, onDismissRequest, modifier, content = content)
}

@Composable
fun AppMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true) {
    DropdownMenuItem(text, onClick, modifier.heightIn(min = UiSpacing.touchTarget),
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, enabled = enabled && LocalFieldEnabled.current)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(), content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest, modifier, sheetState = sheetState, content = content)
}
