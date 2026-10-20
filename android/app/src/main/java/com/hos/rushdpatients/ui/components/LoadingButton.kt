package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LoadingButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false
) {
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        AppActionContent(text, loading)
    }
    if (outlined) AppOutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled && !loading, content = content)
    else AppButton(onClick = onClick, modifier = modifier, enabled = enabled && !loading, content = content)
}
