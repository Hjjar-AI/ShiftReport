package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.theme.UiSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------- Section + field helpers ----------------

@Composable
internal fun SectionTitle(text: String, complete: Boolean? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = buildString {
                append(text)
                when (complete) {
                    true -> append("  ✓")
                    false -> Unit
                    null -> Unit
                }
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = UiSpacing.small, vertical = UiSpacing.tiny).semantics { heading() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    supportingText: String?,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$label: ${value?.toString() ?: "اختر"}",
                style = MaterialTheme.typography.bodyMedium
            )
            supportingText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("إلغاء") }
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
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder != null) Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        minLines = 1,
        maxLines = 4,
        textStyle = textStyle,
        isError = isError
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
    onEditItem: (Int, String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onAddSeparator: () -> Unit,
    onAddDate: ((LocalDate) -> Unit)?,
    textStyle: TextStyle,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$label${if (label == "التحاليل") "" else " *"}",
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

        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("إضافة إلى $label") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 2,
            textStyle = textStyle,
            isError = isError
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onAdd,
                enabled = draft.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("إضافة") }
            OutlinedButton(
                onClick = onAddSeparator,
                enabled = items.isNotEmpty() && items.last() != FIELD_SEPARATOR,
                modifier = Modifier.weight(1f)
            ) { Text("فاصل") }
            if (onAddDate != null) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) { Text("تاريخ") }
            }
        }
    }

    if (showDatePicker && onAddDate != null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        onAddDate(date)
                    }
                    showDatePicker = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
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
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "إعادة ترتيب العنصر ${index + 1}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("نقل لأعلى") },
                enabled = index > 0,
                onClick = {
                    onMove(index, index - 1)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("نقل لأسفل") },
                enabled = index < itemCount - 1,
                onClick = {
                    onMove(index, index + 1)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("نقل للأعلى تماماً") },
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DragHandleButton(index = index, itemCount = itemCount, onMove = onMove)
        OutlinedTextField(
            value = item,
            onValueChange = { newValue -> onEditItem(index, newValue) },
            modifier = Modifier.weight(1f),
            minLines = 1,
            maxLines = 3,
            textStyle = textStyle
        )
        IconButton(onClick = { onRemove(index) }) {
            Icon(Icons.Default.Close, contentDescription = "حذف العنصر")
        }
    }
}

@Composable
private fun SeparatorRow(
    index: Int,
    itemCount: Int,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DragHandleButton(index = index, itemCount = itemCount, onMove = onMove)
        Divider(modifier = Modifier.weight(1f))
        IconButton(onClick = { onRemove(index) }) {
            Icon(Icons.Default.Close, contentDescription = "حذف الفاصل")
        }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DragHandleButton(index = index, itemCount = itemCount, onMove = onMove)
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = date.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        IconButton(onClick = { onRemove(index) }) {
            Icon(Icons.Default.Close, contentDescription = "حذف التاريخ")
        }
    }
}

// ---------------- Doctor dropdown ----------------

@Composable
internal fun DoctorDropdown(
    label: String,
    undefinedLabel: String = "غير محدد",
    selectedId: String?,
    doctors: List<Doctor>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = doctors.firstOrNull { it.id == selectedId }?.fullName
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedName?.let { "$label: $it" } ?: undefinedLabel,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(undefinedLabel) },
                onClick = { onSelected(null); expanded = false }
            )
            doctors.forEach { doctor ->
                DropdownMenuItem(
                    text = { Text(doctor.fullName) },
                    onClick = { onSelected(doctor.id); expanded = false }
                )
            }
        }
    }
}
