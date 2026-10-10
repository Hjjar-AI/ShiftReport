package com.hos.rushdpatients.ui.components

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
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    contentPadding: PaddingValues = UiPadding.content,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = UiSpacing.touchTarget),
        enabled = enabled, shape = MaterialTheme.shapes.small,
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
        enabled = enabled, shape = MaterialTheme.shapes.small,
        contentPadding = UiPadding.content,
        content = content
    )
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = UiPadding.content,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick, modifier = modifier.heightIn(min = UiSpacing.touchTarget),
        enabled = enabled, shape = MaterialTheme.shapes.small, colors = colors,
        contentPadding = contentPadding, content = content
    )
}

/** Neutral section surfaces; callers retain semantic colors and selection borders. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier, shape = MaterialTheme.shapes.medium,
        colors = colors, elevation = elevation, border = border, content = content
    )
}
