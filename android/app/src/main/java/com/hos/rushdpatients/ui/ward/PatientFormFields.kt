package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIconButton
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppSearchField
import com.hos.rushdpatients.ui.components.AppChoiceRow
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppPickerField
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.LocalFieldEnabled
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import com.hos.rushdpatients.ui.components.AppDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.theme.UiSpacing
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
    errorMessage: String? = null
) {
    val uiText = rememberUiStrings()
    var open by remember { mutableStateOf(false) }
    AppPickerField(
        value = value?.toString().orEmpty(), label = label,
        placeholder = uiText(R.string.ui_patient_form_fields_001), icon = Icons.Filled.CalendarMonth, valueTextDirection = TextDirection.Ltr,
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
                AppTextButton(onClick = { open = false }) { Text(uiText(R.string.ui_cancel)) }
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

// ---------------- Multi-value editor ----------------

internal const val FIELD_SEPARATOR = "---"
private const val DATE_PREFIX = "@date:"

internal fun parseFieldItems(value: String?): List<String> = value.orEmpty()
    .split('\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

private fun isDateMarker(item: String): Boolean = item.startsWith(DATE_PREFIX)

private fun dateMarkerValue(item: String): LocalDate? =
    runCatching { LocalDate.parse(item.removePrefix(DATE_PREFIX)) }.getOrNull()

internal fun dateMarkerFrom(date: LocalDate): String = "$DATE_PREFIX$date"

internal fun <T> reorder(list: List<T>, from: Int, to: Int): List<T> {
    if (from == to) return list
    if (from !in list.indices || to !in list.indices) return list
    val mutable = list.toMutableList()
    val moved = mutable.removeAt(from)
    mutable.add(to, moved)
    return mutable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiValueEditor(
    label: String,
    items: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onReplaceAll: (String) -> Unit,
    onEditItem: (Int, String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onAddSeparator: () -> Unit,
    onAddDate: ((LocalDate) -> Unit)?,
    textStyle: TextStyle,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
    errorMessage: String? = null
) {
    val uiText = rememberUiStrings()
    var tools by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var insertMenu by remember { mutableStateOf(false) }
    val enabled = LocalFieldEnabled.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        if (!tools) MultilineField(
            value = (items + draft).filter(String::isNotBlank).joinToString("\n"),
            onValueChange = onReplaceAll, label = if (label == uiText(R.string.ui_labs)) label else "$label *",
            textStyle = textStyle, isError = isError, errorMessage = errorMessage)
        else {
            Text(
                uiText(R.string.ui_patient_form_fields_003, label, if (label == uiText(R.string.ui_labs)) "" else " *"),
                style = MaterialTheme.typography.labelMedium
            )

            items.forEachIndexed { index, item ->
                when {
                    item == FIELD_SEPARATOR -> SeparatorRow(
                        index = index,
                        itemCount = items.size,
                        onMove = onMove,
                        onRemove = onRemove
                    )
                    isDateMarker(item) -> DateMarkerRow(
                        index = index,
                        itemCount = items.size,
                        date = dateMarkerValue(item) ?: LocalDate.now(),
                        onMove = onMove,
                        onRemove = onRemove
                    )
                    else -> TextItemRow(
                        index = index,
                        itemCount = items.size,
                        item = item,
                        onEditItem = onEditItem,
                        onMove = onMove,
                        onRemove = onRemove,
                        textStyle = textStyle
                    )
                }
            }

            AppTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text(uiText(R.string.ui_patient_form_fields_004, label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 2,
                textStyle = textStyle,
                isError = isError, errorMessage = errorMessage
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                AppButton(onClick = onAdd, enabled = enabled && draft.isNotBlank(),
                    modifier = Modifier.weight(1f)) { Text(uiText(R.string.ui_patient_form_fields_005)) }
                Box {
                    AppIconButton(label = uiText(R.string.ui_patient_form_fields_006, label),
            icon = Icons.Filled.MoreVert,
            onClick = { insertMenu = true },
            enabled = enabled)
                    DropdownMenu(expanded = insertMenu && enabled, onDismissRequest = { insertMenu = false }) {
                        DropdownMenuItem(text = { Text(uiText(R.string.ui_patient_form_fields_007)) },
                            enabled = items.isNotEmpty() && items.last() != FIELD_SEPARATOR,
                            onClick = { insertMenu = false; onAddSeparator() })
                        if (onAddDate != null) DropdownMenuItem(text = { Text(uiText(R.string.ui_patient_form_fields_008)) },
                            onClick = { insertMenu = false; showDatePicker = true })
                    }
                }
            }
        }
        AppTextButton(onClick = { tools = !tools }, enabled = enabled) {
            Text(if (tools) uiText(R.string.ui_patient_form_fields_009) else uiText(R.string.ui_patient_form_fields_010))
        }
    }

    if (showDatePicker && enabled && onAddDate != null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                AppTextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        onAddDate(date)
                    }
                    showDatePicker = false
                }) { Text(uiText(R.string.ui_patient_form_fields_002)) }
            },
            dismissButton = {
                AppTextButton(onClick = { showDatePicker = false }) { Text(uiText(R.string.ui_cancel)) }
            }
        ) { DatePicker(state = state) }
    }
}

// ---------------- Item rows ----------------

@Composable
private fun DragHandleButton(
    index: Int,
    itemCount: Int,
    onMove: (Int, Int) -> Unit
) {
    val uiText = rememberUiStrings()
    var expanded by remember { mutableStateOf(false) }
    val enabled = LocalFieldEnabled.current
    Box {
        AppIconButton(label = uiText(R.string.ui_patient_form_fields_011, index + 1),
            icon = Icons.Filled.DragHandle,
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.size(UiSpacing.touchTarget),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(uiText(R.string.ui_patient_form_fields_012)) },
                enabled = index > 0,
                onClick = {
                    onMove(index, index - 1)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(uiText(R.string.ui_patient_form_fields_013)) },
                enabled = index < itemCount - 1,
                onClick = {
                    onMove(index, index + 1)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(uiText(R.string.ui_patient_form_fields_014)) },
                enabled = index > 0,
                onClick = {
                    onMove(index, 0)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun TextItemRow(
    index: Int,
    itemCount: Int,
    item: String,
    onEditItem: (Int, String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    textStyle: TextStyle
) {
    val uiText = rememberUiStrings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.micro)
    ) {
        DragHandleButton(index = index, itemCount = itemCount, onMove = onMove)
        AppTextField(
            value = item,
            onValueChange = { newValue -> onEditItem(index, newValue) },
            modifier = Modifier.weight(1f),
            minLines = 1,
            maxLines = 3,
            textStyle = textStyle
        )
        AppIconButton(label = uiText(R.string.ui_patient_form_fields_015),
            icon = Icons.Default.Close,
            onClick = { onRemove(index) },
            enabled = LocalFieldEnabled.current)
    }
}

@Composable
private fun SeparatorRow(
    index: Int,
    itemCount: Int,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    val uiText = rememberUiStrings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.micro)
    ) {
        DragHandleButton(index = index, itemCount = itemCount, onMove = onMove)
        AppDivider(modifier = Modifier.weight(1f))
        AppIconButton(label = uiText(R.string.ui_patient_form_fields_016),
            icon = Icons.Default.Close,
            onClick = { onRemove(index) },
            enabled = LocalFieldEnabled.current)
    }
}

@Composable
private fun DateMarkerRow(
    index: Int,
    itemCount: Int,
    date: LocalDate,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    val uiText = rememberUiStrings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.micro)
    ) {
        DragHandleButton(index = index, itemCount = itemCount, onMove = onMove)
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(UiPadding.content),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(UiSize.iconSmall),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = date.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        AppIconButton(label = uiText(R.string.ui_patient_form_fields_017),
            icon = Icons.Default.Close,
            onClick = { onRemove(index) },
            enabled = LocalFieldEnabled.current)
    }
}

// ---------------- Doctor dropdown ----------------

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
    if (expanded && enabled) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(label) }, text = {
            Column(Modifier.appDialogBody(), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                AppSearchField(query = query, onQueryChange = { query = it }, label = uiText(R.string.ui_shift_doctor_picker_003))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    AppChoiceRow(label = undefinedLabel, selected = selectedId == null,
                        onClick = { onSelected(null); expanded = false })
                    val matching = doctors.filter { !it.isDeleted && ArabicSearchNormalizer.matches(query, it.fullName) }
                    if (matching.isEmpty()) EmptyState(uiText(R.string.ui_shift_doctor_picker_006),
                        actionLabel = if (query.isBlank()) null else uiText(R.string.ui_shift_doctor_picker_007), onAction = { query = "" })
                    doctorChoiceGroups(uiText, matching, registry).forEach { (team, members) ->
                        AppSectionHeader(team)
                        members.forEach { doctor ->
                            AppChoiceRow(label = doctor.fullName, selected = selectedId == doctor.id,
                                onClick = { onSelected(doctor.id); expanded = false })
                        }
                    }
                }
            }
        }, confirmButton = { AppTextButton(onClick = { expanded = false }) { Text(uiText(R.string.ui_back)) } })
}
