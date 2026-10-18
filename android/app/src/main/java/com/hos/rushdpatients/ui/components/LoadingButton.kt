package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = LocalContentColor.current,
                    modifier = Modifier.size(UiSize.iconSmall),
                    strokeWidth = UiSize.progressStroke
                )
            }
            Text(text)
        }
    }
    if (outlined) AppOutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled && !loading, content = content)
    else AppButton(onClick = onClick, modifier = modifier, enabled = enabled && !loading, content = content)
}
