package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiOpacity
import com.hos.rushdpatients.ui.theme.UiElevation
import com.hos.rushdpatients.ui.theme.UiBorder
import com.hos.rushdpatients.ui.theme.UiPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ExperimentalMaterial3Api
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

enum class AppButtonStyle { PRIMARY, TONAL, OUTLINED, TEXT, DESTRUCTIVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    contentPadding: PaddingValues = UiPadding.button,
    style: AppButtonStyle = AppButtonStyle.PRIMARY,
    colors: ButtonColors? = null,
    size: com.hos.rushdpatients.ui.theme.UiControlSize = com.hos.rushdpatients.ui.theme.LocalUiPreferences.current.controlSize,
    content: @Composable RowScope.() -> Unit
) {
    val active = enabled && LocalFieldEnabled.current
    val shape = MaterialTheme.shapes.small
    val target = modifier.minimumInteractiveComponentSize().heightIn(min = size.visualHeight(UiSpacing.touchTarget))
    when (style) {
        AppButtonStyle.OUTLINED -> OutlinedButton(onClick, target, active, shape = shape,
            colors = colors ?: ButtonDefaults.outlinedButtonColors(), contentPadding = contentPadding, content = content)
        AppButtonStyle.TEXT -> TextButton(onClick, target, active, shape = shape,
            colors = colors ?: ButtonDefaults.textButtonColors(), contentPadding = contentPadding, content = content)
        else -> Button(onClick, target, active, shape = shape,
            colors = colors ?: when (style) {
                AppButtonStyle.TONAL -> ButtonDefaults.filledTonalButtonColors()
                AppButtonStyle.DESTRUCTIVE -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                else -> ButtonDefaults.buttonColors()
            }, contentPadding = contentPadding, content = content)
    }
}

@Composable
fun AppOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current, content: @Composable RowScope.() -> Unit) =
    AppButton(onClick, modifier, enabled, style = AppButtonStyle.OUTLINED, content = content)

@Composable
fun AppTextButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    colors: ButtonColors = ButtonDefaults.textButtonColors(
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.disabledContent)),
    contentPadding: PaddingValues = UiPadding.content, content: @Composable RowScope.() -> Unit) =
    AppButton(onClick, modifier, enabled, contentPadding, AppButtonStyle.TEXT, colors, content = content)

/** Neutral section surfaces; callers retain semantic colors and selection borders. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = UiElevation.flat),
    border: BorderStroke? = UiBorder.cardStroke(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier, shape = MaterialTheme.shapes.medium,
        colors = colors, elevation = elevation, border = border, content = content
    )
}
