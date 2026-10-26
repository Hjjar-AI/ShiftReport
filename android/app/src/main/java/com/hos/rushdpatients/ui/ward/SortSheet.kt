package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppSheet
import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.rememberModalBottomSheetState
import com.hos.rushdpatients.ui.components.AppDialogDismissAction
import com.hos.rushdpatients.ui.components.AppDialogHeader
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.theme.UiDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.SortDirection
import com.hos.rushdpatients.domain.sort.SortField
import com.hos.rushdpatients.domain.sort.SortLevel
import com.hos.rushdpatients.domain.sort.SortSpec

/** Standalone host retained for the in-memory demo; the ward embeds the same editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    initial: SortSpec,
    saving: Boolean = false,
    onApply: (SortSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().appDialogBody(UiDialog.sheetMaxHeight).padding(UiSpacing.screen)) {
            AppDialogHeader(uiText(R.string.ui_sort_sheet_001))
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                PatientSortOptions(initial, saving, onApply)
            }
            AppDialogDismissAction(onClick = onDismiss, enabled = !saving)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatientSortOptions(
    initial: SortSpec,
    saving: Boolean = false,
    onApply: (SortSpec) -> Unit
) {
    val uiText = rememberUiStrings()
    val presets = listOf(
        R.string.ui_sort_sheet_011 to SortSpec.EMPTY,
        R.string.sort_preset_name to SortSpec.DEFAULT,
        R.string.sort_preset_admission to SortSpec(listOf(
            SortLevel(SortField.DAYS_OF_ADMITTANCE, SortDirection.DESC), SortLevel(SortField.NAME))),
        R.string.ui_sort_sheet_012 to SortSpec.WORKBOOK
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        presets.forEach { (label, spec) ->
            com.hos.rushdpatients.ui.components.AppFilterChip(
                selected = initial == spec, enabled = !saving,
                onClick = { onApply(spec) },
                label = { Text(uiText(label)) })
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
