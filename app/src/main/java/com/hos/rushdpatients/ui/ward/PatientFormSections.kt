package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

internal enum class PatientFormSection(val label: String) {
    CLINICAL("سريري"), LABS("تحاليل"), TASKS("مهام"), PERSONAL("شخصي")
}

/** Scroll shortcuts only: all sections share the existing patient draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatientFormSectionNavigation(
    sections: List<PatientFormSection>, selected: PatientFormSection,
    enabled: Boolean, onSelect: (PatientFormSection) -> Unit
) {
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        sections.forEach { section ->
            FilterChip(selected = section == selected, enabled = enabled,
                onClick = { onSelect(section) }, label = { Text(section.label) },
                modifier = Modifier.heightIn(min = UiSpacing.touchTarget))
        }
    }
}
