package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AppDestinationCard(title: String, summary: String, icon: ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AppCard(modifier) { AppDestinationRow(title, summary, icon, onClick, enabled = enabled) }
}

@Composable
fun AppClinicalCard(title: String, content: String, emptyText: String = androidx.compose.ui.res.stringResource(com.hos.rushdpatients.R.string.ui_patient_details_screen_009)) {
    AppCard { AppClinicalSection(title, content, Modifier.padding(com.hos.rushdpatients.ui.theme.uiContentPadding()), emptyText = emptyText) }
}

@Composable
fun AppActionCard(title: String, description: String, actionLabel: String,
    onClick: () -> Unit, primary: Boolean = false) {
    AppSection(title) {
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppButton(onClick, Modifier.fillMaxWidth(), style = if (primary) AppButtonStyle.PRIMARY else AppButtonStyle.OUTLINED) {
            Text(actionLabel)
        }
    }
}
