package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiLayout

fun Modifier.readingPaneWidth(): Modifier = fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = UiLayout.contentMaxWidth).fillMaxWidth()
