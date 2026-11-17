package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Match settings section cards while keeping the retained form state in its owner. */
@Composable
internal fun PatientFormSection(content: @Composable ColumnScope.() -> Unit) {
    com.hos.rushdpatients.ui.components.AppSectionContainer(content = content)
}
