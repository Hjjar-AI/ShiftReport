package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Bound reading width within the available pane; callers retain scrolling and state. */
@Composable
fun AppReadingPane(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = com.hos.rushdpatients.ui.theme.UiLayout.contentMaxWidth).fillMaxSize(),
            content = content)
    }
}

/** Center short authentication pages while keeping long content reachable above the keyboard. */
@Composable
fun AppCenteredContent(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().imePadding()
            .verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = {
            Column(Modifier.widthIn(max = com.hos.rushdpatients.ui.theme.UiLayout.contentMaxWidth).fillMaxWidth(),
                verticalArrangement = verticalArrangement, horizontalAlignment = Alignment.CenterHorizontally,
                content = content)
        }
    )
}
