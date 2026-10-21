package com.hos.rushdpatients.ui.ward

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.R

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable

internal enum class PatientFormSection(@StringRes private val labelRes: Int) {
    CLINICAL(R.string.ui_patient_form_sections_001), LABS(R.string.ui_labs), TASKS(R.string.ui_tasks), PERSONAL(R.string.ui_project_setup_view_model_023);

    fun label(uiText: UiStrings): String = uiText(labelRes)
}

/** Scroll shortcuts only: all sections share the existing patient draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatientFormSectionNavigation(
    sections: List<PatientFormSection>, selected: PatientFormSection,
    enabled: Boolean, onSelect: (PatientFormSection) -> Unit
) {
    val uiText = rememberUiStrings()
    com.hos.rushdpatients.ui.components.AppSectionIndex(sections = sections, selected = selected,
        label = { it.label(uiText) }, onSelect = onSelect, enabled = enabled, centered = true, compact = true)
}
