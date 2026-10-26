package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Compact chrome grows only when its actual text needs more than the 48dp control height. */
@Composable
fun AppCompactTopBar(title: @Composable () -> Unit, navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = UiSpacing.touchTarget)
            .padding(horizontal = UiSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            navigation()
            Column(Modifier.weight(1f)) { title() }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun AppTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val uiText = rememberUiStrings()
    AppCompactTopBar(title = {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }, navigation = {
        AppIconButton(label = uiText(R.string.ui_back),
            icon = AppIcons.ArrowBack,
            onClick = onBack,
            iconModifier = Modifier,
            directional = true)
    }, actions = actions)
}
