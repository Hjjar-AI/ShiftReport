package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import com.hos.rushdpatients.sync.PublicationJournalEntry

data class PatientGroup(
    val key: String?,
    val name: String,
    val patients: List<Patient>
)

data class ShiftSnapshotOption(
    val shiftId: String,
    val date: java.time.LocalDate,
    val patientCount: Int
)

enum class PatientSyncStatus(val arabicLabel: String) {
    LOCAL("محفوظ على الجهاز"),
    PENDING("تعديلات لم تُنشر"),
    BACKED_UP("مطابق لآخر نسخة معروفة على هذا الجهاز"),
    SYNCING("جار جلب أو نشر البيانات")
}

enum class WardRetryAction { FETCH_LATEST, FETCH_PREVIOUS, EXPORT_CSV }

data class WardUiState(
    val hospitalName: String = "",
    val loading: Boolean = true,
    val shift: Shift? = null,
    val isReadOnly: Boolean = false,
    val patients: List<Patient> = emptyList(),
    val taskNowEpochMillis: Long = System.currentTimeMillis(),
    val deletedPatients: List<Patient> = emptyList(),
    val patientDraft: PatientDraft? = null,
    val rolloverPatients: List<Patient> = emptyList(),
    val rolloverTarget: Shift? = null,
    val rolloverSourceLabel: String = "",
    val activityLoading: Boolean = false,
    val activityError: String? = null,
    val recentActivity: List<AuditEntryEntity> = emptyList(),
    val publications: List<PublicationJournalEntry> = emptyList(),
    val groupedPatients: List<PatientGroup> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val sortSpec: SortSpec = SortSpec.DEFAULT,
    val groupByMode: GroupByMode = GroupByMode.SUPERVISOR,
    val saving: Boolean = false,
    val syncing: Boolean = false,
    val canUndoDownload: Boolean = false,
    val snapshotChoices: List<ShiftSnapshotOption> = emptyList(),
    val showSnapshotPicker: Boolean = false,
    val synchronization: WardSynchronization = WardSynchronization(),
    val exportingCsv: Boolean = false,
    val patientDetailsExpanded: Boolean = false,
    val twoColumn: Boolean = false,
    val compactCards: Boolean = false,
    val saveAndNext: Boolean = false,
    val showSyncHint: Boolean = false,
    val lastOperation: String? = null,
    val retryAction: WardRetryAction? = null,
    val error: String? = null,
    val snackbar: String? = null
) {
    val syncStatus: PatientSyncStatus get() = synchronization.status(syncing)
    val freshnessLabel: String get() = synchronization.description(syncing, shift?.csvMessageId)
    internal val workflowAction: WardWorkflowAction get() = synchronization.nextAction(isReadOnly)
    val unresolvedDeliveries: Int get() = synchronization.unresolvedDeliveries
    val deliveryLogUnavailable: Boolean get() = synchronization.deliveryLogUnavailable
}
