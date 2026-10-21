package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppDialogConfirmAction
import androidx.compose.runtime.CompositionLocalProvider
import com.hos.rushdpatients.ui.components.LocalFieldEnabled
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.hos.rushdpatients.ui.components.AppDialogDismissAction
import com.hos.rushdpatients.ui.components.AppDialogHeader
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.theme.UiDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.domain.sort.SortDirection
import com.hos.rushdpatients.domain.sort.SortField
import com.hos.rushdpatients.domain.sort.SortLevel
import com.hos.rushdpatients.domain.sort.SortSpec

/** Standalone host retained for the in-memory demo; the ward embeds the same editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    initial: SortSpec,
    initialRevision: Long,
    saving: Boolean = false,
    onApply: (SortSpec, Long, (Shift?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().appDialogBody(UiDialog.sheetMaxHeight).padding(UiSpacing.screen)) {
            AppDialogHeader(uiText(R.string.ui_sort_sheet_001))
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                PatientSortOptions(initial, initialRevision, saving, onApply)
            }
            AppDialogDismissAction(onClick = onDismiss, enabled = !saving)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatientSortOptions(
    initial: SortSpec,
    initialRevision: Long,
    saving: Boolean = false,
    onApply: (SortSpec, Long, (Shift?) -> Unit) -> Unit
) {
    val uiText = rememberUiStrings()
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

    CompositionLocalProvider(LocalFieldEnabled provides !saving) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = UiSpacing.tiny),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                if (rejected != null) {
                    AppTextButton(onClick = { reviewing = true }, enabled = !saving) {
                        Text(uiText(R.string.ui_sort_sheet_002))
                    }
                }
                levels.forEachIndexed { index, level ->
                    Text(uiText(R.string.ui_sort_sheet_003) + when (index) {
                        0 -> uiText(R.string.ui_sort_sheet_004)
                        1 -> uiText(R.string.ui_sort_sheet_005)
                        2 -> uiText(R.string.ui_sort_sheet_006)
                        else -> uiText(R.string.ui_sort_sheet_007)
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
                        }) { Text(uiText(R.string.ui_patient_task_editor_007)) }
                    }
                }

                if (levels.size < 4) {
                    AppOutlinedButton(
                        onClick = {
                            levels = levels + SortLevel(SortField.NAME, SortDirection.ASC)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(uiText(R.string.ui_sort_sheet_008)) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                AppDialogConfirmAction(text = uiText(R.string.ui_sort_sheet_009), loading = saving,
                    onClick = { apply(SortSpec(levels)) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f)
                )
                AppOutlinedButton(
                    onClick = { apply(SortSpec.DEFAULT) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f)
                ) { Text(uiText(R.string.ui_sort_sheet_010)) }
            }
            AppTextButton(
                onClick = { apply(SortSpec.EMPTY) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) { Text(uiText(R.string.ui_sort_sheet_011)) }
            AppTextButton(onClick = { apply(SortSpec.WORKBOOK) }, enabled = !saving,
                modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_sort_sheet_012)) }
        }
    }
    if (reviewing) {
        val saved = latest
        val draft = rejected ?: SortSpec.EMPTY
        val savedSpec = SortSpecCodec.decode(saved?.sortSpecJson)
        StaleEditReviewDialog(
            differences = listOf(Triple(uiText(R.string.ui_sort_sheet_001), sortReviewLabel(uiText, draft), sortReviewLabel(uiText, savedSpec))),
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
    val uiText = rememberUiStrings()
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        AppOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(fieldLabel(uiText, selected))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortField.values().forEach { f ->
                DropdownMenuItem(
                    enabled = LocalFieldEnabled.current,
                    text = { Text(fieldLabel(uiText, f)) },
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
    val uiText = rememberUiStrings()
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        AppOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selected == SortDirection.ASC) uiText(R.string.ui_sort_sheet_013) else uiText(R.string.ui_sort_sheet_014))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                    enabled = LocalFieldEnabled.current,
                text = { Text(uiText(R.string.ui_sort_sheet_013)) },
                onClick = { onSelected(SortDirection.ASC); expanded = false }
            )
            DropdownMenuItem(
                    enabled = LocalFieldEnabled.current,
                text = { Text(uiText(R.string.ui_sort_sheet_014)) },
                onClick = { onSelected(SortDirection.DESC); expanded = false }
            )
        }
    }
}

internal fun fieldLabel(uiText: UiStrings, f: SortField): String = when (f) {
    SortField.NAME -> uiText(R.string.ui_edit_patient_dialog_009)
    SortField.GENDER -> uiText(R.string.ui_patient_demographic_fields_010)
    SortField.DIAGNOSIS -> uiText(R.string.ui_patient_card_024)
    SortField.DAYS_OF_ADMITTANCE -> uiText(R.string.ui_sort_sheet_015)
    SortField.SUPERVISOR -> uiText(R.string.ui_patient_form_dialog_017)
}

internal fun sortReviewLabel(uiText: UiStrings, spec: SortSpec): String =
    if (spec.levels.isEmpty()) uiText(R.string.ui_sort_sheet_011) else spec.levels.joinToString(" ← ") {
        uiText(R.string.ui_edit_patient_dialog_014, fieldLabel(uiText, it.field), if (it.direction == SortDirection.ASC) uiText(R.string.ui_sort_sheet_013) else uiText(R.string.ui_sort_sheet_014))
    }
