package com.hos.rushdpatients.ui.ward

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
                Text("قبل تعديل بيانات المرضى", style = MaterialTheme.typography.titleSmall)
                Text("اجلب أحدث بيانات الفريق من تليجرام أولاً، ثم ابدأ التعديل. إذا لديك تعديلات لم تُنشر، احتفظ بها قبل الجلب.",
                    style = MaterialTheme.typography.bodySmall)
                AppTextButton(onClick = onFetch, enabled = enabled) { Text("جلب أحدث البيانات") }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(UiSpacing.touchTarget)) {
                Icon(Icons.Filled.Close, contentDescription = "إخفاء تذكير جلب البيانات")
            }
        }
    }
}
