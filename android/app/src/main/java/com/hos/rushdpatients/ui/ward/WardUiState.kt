package com.hos.rushdpatients.ui.ward

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.SortSpec

data class PatientGroup(
    val key: String?,
    val name: String,
    val patients: List<Patient>
)

enum class PatientSyncStatus(@StringRes private val arabicLabelRes: Int) {
    LOCAL(R.string.ui_ward_ui_state_001),
    PENDING(R.string.ui_ward_ui_state_002),
    BACKED_UP(R.string.ui_ward_ui_state_003),
    SYNCING(R.string.ui_ward_ui_state_004);

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}

enum class WardSyncRecovery { PUBLISH_DOCTORS, FETCH_DOCTORS, PUBLISH_PATIENTS }

enum class WardRetryAction { FETCH_LATEST, EXPORT_CSV }

data class PatientDraftIssue(val patientId: String?, val message: String)

data class WardUiState(
    val hospitalName: String = "",
    val loading: Boolean = true,
    val shift: Shift? = null,
    val patients: List<Patient> = emptyList(),
    val deletedPatients: List<Patient> = emptyList(),
    val recycleBinLoading: Boolean = true,
    val recycleBinFailed: Boolean = false,
    val patientDraft: PatientDraft? = null,
    val draftSaveIssue: PatientDraftIssue? = null,
    val groupedPatients: List<PatientGroup> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val sortSpec: SortSpec = SortSpec.DEFAULT,
    val groupByMode: GroupByMode = GroupByMode.SUPERVISOR,
    val saving: Boolean = false,
    val syncing: Boolean = false,
    @StringRes val syncProgressRes: Int = R.string.ui_progress_fetch_patients,
    val canUndoDownload: Boolean = false,
    val synchronization: WardSynchronization = WardSynchronization(),
    val exportingCsv: Boolean = false,
    val patientDetailsExpanded: Boolean = false,
    val twoColumn: Boolean = false,
    val compactCards: Boolean = false,
    val lastOperation: String? = null,
    val retryAction: WardRetryAction? = null,
    val error: String? = null,
    val snackbar: String? = null,
    val syncIssue: String? = null,
    val fetchReviewText: String? = null,
    val olderRemoteConfirmation: Boolean = false,
    val fetchChoiceRequired: Boolean = false,
    val syncRecovery: WardSyncRecovery? = null
) {
    val isReadOnly: Boolean get() = loading || error != null
    val syncStatus: PatientSyncStatus get() = synchronization.status(syncing)
    fun freshnessLabel(uiText: UiStrings): String = synchronization.description(uiText, syncing, shift?.csvMessageId)
    internal val workflowAction: WardWorkflowAction get() = synchronization.nextAction(isReadOnly)
    val unresolvedDeliveries: Int get() = synchronization.unresolvedDeliveries
    val deliveryLogUnavailable: Boolean get() = synchronization.deliveryLogUnavailable
}
