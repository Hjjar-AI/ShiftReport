package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiOpacity
import com.hos.rushdpatients.ui.theme.UiElevation
import com.hos.rushdpatients.ui.theme.UiBorder
import androidx.compose.ui.graphics.Color
import com.hos.rushdpatients.ui.theme.UiPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    contentPadding: PaddingValues = UiPadding.content,
    content: @Composable RowScope.() -> Unit
) {
    val colors = appActionColors()
    Button(
        colors = ButtonDefaults.buttonColors(containerColor = colors.container, contentColor = colors.content,
            disabledContainerColor = colors.disabledContainer, disabledContentColor = colors.disabledContent),
        onClick = onClick, modifier = modifier.heightIn(min = UiSpacing.touchTarget),
        enabled = enabled && LocalFieldEnabled.current, shape = MaterialTheme.shapes.small,
        contentPadding = contentPadding, content = content
    )
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = UiSpacing.touchTarget),
        enabled = enabled && LocalFieldEnabled.current, shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.disabledContent)),
        contentPadding = UiPadding.content,
        content = content
    )
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    colors: ButtonColors = ButtonDefaults.textButtonColors(
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.disabledContent)),
    contentPadding: PaddingValues = UiPadding.content,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick, modifier = modifier.heightIn(min = UiSpacing.touchTarget),
        enabled = enabled && LocalFieldEnabled.current, shape = MaterialTheme.shapes.small, colors = colors,
        contentPadding = contentPadding, content = content
    )
}

/** Neutral section surfaces; callers retain semantic colors and selection borders. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = UiElevation.flat),
    border: BorderStroke? = UiBorder.stroke(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier, shape = MaterialTheme.shapes.medium,
        colors = colors, elevation = elevation, border = border, content = content
    )
}

/** Native and gesture actions share the same enabled and disabled palette. */
data class AppActionColors(val container: Color, val content: Color, val disabledContainer: Color, val disabledContent: Color)

@Composable
fun appActionColors() = AppActionColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary,
    MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.disabledContainer), MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.disabledContent))
