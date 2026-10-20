package com.hos.rushdpatients.ui.ward

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable

internal enum class PatientFormSection(val label: String) {
    CLINICAL("السريري"), LABS("التحاليل"), TASKS("المهام"), PERSONAL("البيانات")
}

/** Scroll shortcuts only: all sections share the existing patient draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatientFormSectionNavigation(
    sections: List<PatientFormSection>, selected: PatientFormSection,
    enabled: Boolean, onSelect: (PatientFormSection) -> Unit
) {
    com.hos.rushdpatients.ui.components.AppSectionIndex(sections = sections, selected = selected,
        label = { it.label }, onSelect = onSelect, enabled = enabled, centered = true, compact = true)
}
