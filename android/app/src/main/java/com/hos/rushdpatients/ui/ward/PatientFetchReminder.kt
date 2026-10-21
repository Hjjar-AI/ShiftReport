package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.theme.UiElevation
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Advisory only: fetching still uses the existing confirmation and local-edit guards. */
@Composable
internal fun PatientFetchReminder(enabled: Boolean, onFetch: () -> Unit, onDismiss: () -> Unit) {
    val uiText = rememberUiStrings()
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = UiElevation.navigation) {
        Row(Modifier.padding(start = UiSpacing.section, end = UiSpacing.small,
            top = UiSpacing.small, bottom = UiSpacing.small),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = UiSpacing.screen)) {
                Icon(Icons.Filled.Send, contentDescription = null,
                    modifier = Modifier.padding(UiSpacing.screen).size(UiSize.iconMedium),
                    tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f).padding(top = UiSpacing.screen)) {
                Text(uiText(R.string.ui_patient_fetch_reminder_001), style = MaterialTheme.typography.titleSmall)
                Text(uiText(R.string.ui_patient_fetch_reminder_002),
                    style = MaterialTheme.typography.bodySmall)
                AppTextButton(onClick = onFetch, enabled = enabled) { Text(uiText(R.string.ui_patient_fetch_reminder_003)) }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(UiSpacing.touchTarget)) {
                Icon(Icons.Filled.Close, contentDescription = uiText(R.string.ui_patient_fetch_reminder_004))
            }
        }
    }
}
