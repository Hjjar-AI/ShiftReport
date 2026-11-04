package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppChoiceRow
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppPickerField
import com.hos.rushdpatients.ui.components.LocalFieldEnabled
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import com.hos.rushdpatients.data.model.Doctor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------- Section + field helpers ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    supportingText: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    isError: Boolean = false,
    errorMessage: String? = null,
    onClear: (() -> Unit)? = null
) {
    val uiText = rememberUiStrings()
    var open by remember { mutableStateOf(false) }
    AppPickerField(
        value = value?.toString().orEmpty(), label = label,
        placeholder = uiText(R.string.ui_patient_form_fields_001), icon = AppIcons.CalendarMonth, valueTextDirection = TextDirection.Ltr,
        onClick = { open = true }, modifier = modifier,
        supportingText = supportingText, enabled = enabled, isError = isError, errorMessage = errorMessage
    )
    if (open && enabled) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                AppTextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(uiText(R.string.ui_patient_form_fields_002)) }
            },
            dismissButton = {
                Row {
                    if (onClear != null && value != null) AppTextButton(onClick = {
                        onClear(); open = false
                    }) { Text(uiText(R.string.ui_patient_clear_date)) }
                    AppTextButton(onClick = { open = false }) { Text(uiText(R.string.ui_cancel)) }
                }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
internal fun MultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    textStyle: TextStyle,
    isError: Boolean = false,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    errorMessage: String? = null
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder != null) Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        minLines = 1,
        maxLines = 4,
        textStyle = textStyle,
        isError = isError, errorMessage = errorMessage
    )
}

@Composable
internal fun DoctorDropdown(
    label: String,
    undefinedLabel: String = androidx.compose.ui.res.stringResource(R.string.ui_unspecified),
    selectedId: String?,
    doctors: List<Doctor>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    isError: Boolean = false,
    supportingText: String? = null,
    registry: List<Doctor> = doctors,
    errorMessage: String? = null
) {
    val uiText = rememberUiStrings()
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selectedName = doctors.firstOrNull { it.id == selectedId }?.fullName
    AppPickerField(value = selectedName.orEmpty(), label = label, placeholder = undefinedLabel,
        onClick = { query = ""; expanded = true }, modifier = modifier.fillMaxWidth(),
        enabled = enabled, isError = isError, errorMessage = errorMessage, supportingText = supportingText)
    if (expanded && enabled) AppAlertDialog(onDismissRequest = { expanded = false },
        title = { Text(label) }, text = {
            val matching = doctors.filter { !it.isDeleted && ArabicSearchNormalizer.matches(query, it.fullName) }
            DoctorSelectionList(
                query = query, onQueryChange = { query = it },
                groups = doctorChoiceGroups(uiText, matching, registry).map { (team, members) ->
                    DoctorSelectionGroup(team, members)
                },
                selectedIds = setOfNotNull(selectedId), enabled = enabled,
                onSelected = { doctor, _ -> onSelected(doctor.id); expanded = false },
                emptyText = uiText(R.string.ui_shift_doctor_picker_006),
                modifier = Modifier.appDialogBody(),
                leadingContent = {
                    AppChoiceRow(label = undefinedLabel, selected = selectedId == null, enabled = enabled,
                        onClick = { onSelected(null); expanded = false })
                }
            )
        }, confirmButton = { AppTextButton(onClick = { expanded = false }) { Text(uiText(R.string.ui_back)) } })
}
