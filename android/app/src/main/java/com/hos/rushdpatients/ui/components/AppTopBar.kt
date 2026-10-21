package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = uiText(R.string.ui_patient_form_fields_019),
                modifier = Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 180f else 0f))
        }
    }, actions = actions)
}
