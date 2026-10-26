package com.hos.rushdpatients.ui.components

import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Divider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant)
}
