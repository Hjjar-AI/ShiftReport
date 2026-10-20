package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun AppDialogHeader(title: String, supportingText: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Caller-owned validation, stale-state review and publication callbacks remain intact. */
@Composable
fun AppDialogConfirmAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, destructive: Boolean = false) {
    val content: @Composable RowScope.() -> Unit = { AppActionContent(text, loading) }
    if (destructive) AppTextButton(onClick = onClick, enabled = enabled && !loading, modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), content = content)
    else AppButton(onClick = onClick, enabled = enabled && !loading, modifier = modifier, content = content)
}

@Composable
fun AppDialogDismissAction(onClick: () -> Unit, text: String = UiAction.CANCEL.label, enabled: Boolean = true) {
    AppTextButton(onClick = onClick, enabled = enabled) { Text(text) }
}
