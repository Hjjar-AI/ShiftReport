package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.domain.sort.SortDirection
import com.hos.rushdpatients.domain.sort.SortField
import com.hos.rushdpatients.domain.sort.SortLevel
import com.hos.rushdpatients.domain.sort.SortSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    initial: SortSpec,
    initialRevision: Long,
    saving: Boolean = false,
    onApply: (SortSpec, Long, (Shift?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maximumHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    var levels by remember { mutableStateOf(initial.levels) }

    var revision by remember { mutableStateOf(initialRevision) }
    var rejected by remember { mutableStateOf<SortSpec?>(null) }
    var latest by remember { mutableStateOf<Shift?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    fun apply(spec: SortSpec) {
        onApply(spec, revision) { saved ->
            rejected = spec
            latest = saved
            reviewing = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maximumHeight)
                .padding(UiSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
        ) {
            Text("ترتيب المرضى", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                if (rejected != null) {
                    AppTextButton(onClick = { reviewing = true }, enabled = !saving) {
                        Text("مراجعة الترتيب المرفوض")
                    }
                }
                levels.forEachIndexed { index, level ->
                    Text("المستوى " + when (index) {
                        0 -> "الأول"
                        1 -> "الثاني"
                        2 -> "الثالث"
                        else -> "الرابع"
                    }, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
                    ) {
                        FieldDropdown(
                            selected = level.field,
                            onSelected = { f ->
                                levels = levels.toMutableList().also { it[index] = level.copy(field = f) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        DirectionDropdown(
                            selected = level.direction,
                            onSelected = { d ->
                                levels = levels.toMutableList().also { it[index] = level.copy(direction = d) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AppTextButton(onClick = {
                            levels = levels.toMutableList().also { it.removeAt(index) }
                        }) { Text("حذف") }
                    }
                }

                if (levels.size < 4) {
                    AppOutlinedButton(
                        onClick = {
                            levels = levels + SortLevel(SortField.NAME, SortDirection.ASC)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("إضافة مستوى") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                AppButton(
                    onClick = { apply(SortSpec(levels)) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f)
                ) { Text("تطبيق") }
                AppOutlinedButton(
                    onClick = { apply(SortSpec.DEFAULT) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f)
                ) { Text("افتراضي") }
            }
            AppTextButton(
                onClick = { apply(SortSpec.EMPTY) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) { Text("الترتيب الأصلي") }
        }
    }
    if (reviewing) {
        val saved = latest
        val draft = rejected ?: SortSpec.EMPTY
        val savedSpec = SortSpecCodec.decode(saved?.sortSpecJson)
        StaleEditReviewDialog(
            differences = listOf(Triple("ترتيب المرضى", sortReviewLabel(draft), sortReviewLabel(savedSpec))),
            available = saved != null,
            onKeepDraft = {
                if (saved != null) {
                    levels = draft.levels
                    revision = saved.revision
                    reviewing = false
                }
            },
            onUseSaved = {
                if (saved != null) {
                    levels = savedSpec.levels
                    revision = saved.revision
                    reviewing = false
                }
            },
            onDismiss = { reviewing = false }
        )
    }
}

@Composable
private fun FieldDropdown(
    selected: SortField,
    onSelected: (SortField) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        AppOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(fieldLabel(selected))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortField.values().forEach { f ->
                DropdownMenuItem(
                    text = { Text(fieldLabel(f)) },
                    onClick = { onSelected(f); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun DirectionDropdown(
    selected: SortDirection,
    onSelected: (SortDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        AppOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selected == SortDirection.ASC) "تصاعدي" else "تنازلي")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("تصاعدي") },
                onClick = { onSelected(SortDirection.ASC); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("تنازلي") },
                onClick = { onSelected(SortDirection.DESC); expanded = false }
            )
        }
    }
}

private fun fieldLabel(f: SortField): String = when (f) {
    SortField.NAME -> "الاسم"
    SortField.GENDER -> "الجنس"
    SortField.DIAGNOSIS -> "التشخيص"
    SortField.DAYS_OF_ADMITTANCE -> "أيام الدخول"
    SortField.SUPERVISOR -> "الاختصاصي"
}

private fun sortReviewLabel(spec: SortSpec): String =
    if (spec.levels.isEmpty()) "الترتيب الأصلي" else spec.levels.joinToString(" ← ") {
        "${fieldLabel(it.field)} (${if (it.direction == SortDirection.ASC) "تصاعدي" else "تنازلي"})"
    }
