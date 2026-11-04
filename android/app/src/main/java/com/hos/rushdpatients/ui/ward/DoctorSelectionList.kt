package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.components.AppCheckRow
import com.hos.rushdpatients.ui.components.AppChoiceRow
import com.hos.rushdpatients.ui.components.AppSearchField
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.theme.UiSpacing

internal data class DoctorSelectionGroup(val heading: String?, val doctors: List<Doctor>)

/** Callers own filtering/grouping, selection drafts and confirmation; this owns presentation. */
@Composable
internal fun DoctorSelectionList(
    query: String, onQueryChange: (String) -> Unit,
    groups: List<DoctorSelectionGroup>, selectedIds: Set<String>,
    onSelected: (Doctor, Boolean) -> Unit, emptyText: String,
    modifier: Modifier = Modifier, multiple: Boolean = false, enabled: Boolean = true,
    fillAvailableHeight: Boolean = false,
    leadingContent: @Composable ColumnScope.() -> Unit = {}
) {
    val uiText = rememberUiStrings()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        AppSearchField(query = query, onQueryChange = onQueryChange,
            label = uiText(R.string.ui_shift_doctor_picker_003), enabled = enabled)
        Column(Modifier.fillMaxWidth().weight(1f, fill = fillAvailableHeight)
            .verticalScroll(rememberScrollState())
            .then(if (multiple) Modifier else Modifier.selectableGroup()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            leadingContent()
            if (groups.all { it.doctors.isEmpty() }) {
                EmptyState(title = emptyText,
                    actionLabel = if (query.isBlank()) null else uiText(R.string.ui_shift_doctor_picker_007),
                    onAction = { onQueryChange("") })
            }
            groups.filter { it.doctors.isNotEmpty() }.forEach { group ->
                group.heading?.let { AppSectionHeader(it) }
                group.doctors.forEach { doctor -> key(doctor.id) {
                    if (multiple) {
                        AppCheckRow(label = doctor.fullName, checked = doctor.id in selectedIds, enabled = enabled,
                            onCheckedChange = { onSelected(doctor, it) })
                    } else {
                        AppChoiceRow(label = doctor.fullName, selected = doctor.id in selectedIds, enabled = enabled,
                            onClick = { onSelected(doctor, true) })
                    }
                } }
            }
        }
    }
}
