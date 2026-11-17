package com.hos.rushdpatients.ui.doctors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.ward.WardFormDialog

/** The workspace owns the reviewed snapshot and publication/fetch callbacks. */
@Composable
internal fun DoctorRegistryReviewDialog(
    action: String,
    pendingSummary: String?,
    registryReview: List<Doctor>,
    reviewCurrent: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    WardFormDialog(onDismissRequest = onDismiss,
        title = { AppDialogHeader(if (action == "publish") uiText(R.string.ui_doctors_screen_006) else uiText(R.string.ui_doctors_screen_005)) },
        text = { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppText(if (action == "publish") uiText(R.string.ui_doctors_screen_027)
                else uiText(R.string.ui_doctors_screen_028))
            if (action == "publish") {
                pendingSummary?.let { AppText(it) }
                AppText(registryReview.joinToString("\n") { it.fullName })
                if (!reviewCurrent) AppText(uiText(R.string.ui_doctors_screen_029))
            }
        } }, confirmButton = { AppDialogConfirmAction(
            text = if (action == "publish") uiText(R.string.ui_doctors_screen_030) else uiText(R.string.ui_doctors_screen_031),
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && (action != "publish" || reviewCurrent), onClick = onConfirm) },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText(uiText(R.string.ui_cancel)) } })
}

@Composable
internal fun DoctorImportDialogs(
    state: DoctorsUiState,
    busy: Boolean,
    onDismissImportError: () -> Unit,
    onDismissDoctorImport: () -> Unit,
    onConfirmDoctorImport: () -> Unit
) {
    val uiText = rememberUiStrings()
    if (state.importing) {
        AppAlertDialog(onDismissRequest = {}, title = { AppText(uiText(R.string.ui_doctors_screen_024)) },
            text = { AppLoadingState(uiText(R.string.ui_doctors_screen_025), size = com.hos.rushdpatients.ui.components.AppLoadingSize.SECTION) },
            confirmButton = {})
    }
    state.importError?.let { message ->
        AppAlertDialog(onDismissRequest = onDismissImportError,
            title = { AppText(uiText(R.string.ui_doctors_screen_026)) }, text = { AppText(message) },
            confirmButton = { AppTextButton(onClick = onDismissImportError) { AppText(uiText(R.string.ui_settings_screen_006)) } })
    }

    state.importPreview?.takeUnless { state.importing }?.let { preview ->
        WardFormDialog(
            onDismissRequest = onDismissDoctorImport,
            title = { AppDialogHeader(uiText(R.string.ui_doctors_screen_032)) },
            text = {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    AppText(uiText(R.string.ui_doctors_screen_033, preview.activeCount))
                    listOf(uiText(R.string.ui_doctors_screen_034) to preview.added, uiText(R.string.ui_doctors_screen_035) to preview.changed,
                        uiText(R.string.ui_doctors_screen_036) to preview.unchanged,
                        uiText(R.string.ui_doctors_screen_037) to preview.retained).forEach { (label, names) ->
                        AppText("$label (${names.size}): " + names.joinToString(uiText(R.string.ui_patient_card_002)).ifBlank { uiText(R.string.ui_doctors_screen_038) })
                    }
                    AppText(uiText(R.string.ui_doctors_screen_039, preview.adminCount))
                    AppText(uiText(R.string.ui_doctors_screen_040, preview.deletedCount))
                    if (preview.newWithoutPinCount > 0) {
                        AppText(
                            uiText(R.string.ui_doctors_screen_041, preview.newWithoutPinCount),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    AppText(
                        uiText(R.string.ui_doctors_screen_042),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                AppDialogConfirmAction(text = uiText(R.string.ui_doctors_screen_043),
                    onClick = onConfirmDoctorImport, enabled = !busy, modifier = Modifier.fillMaxWidth())
            },
            dismissButton = {
                AppTextButton(onClick = onDismissDoctorImport, enabled = !busy) { AppText(uiText(R.string.ui_cancel)) }
            }
        )
    }
}
