package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import com.hos.rushdpatients.sync.PatientFieldConflict
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
    LOCAL("محفوظ محلياً"),
    PENDING("تعديلات غير منشورة"),
    BACKED_UP("منشور على تليجرام"),
    SYNCING("جارٍ التزامن"),
    CONFLICT("تعارض")
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
    val mergeConflicts: List<PatientFieldConflict> = emptyList(),
    val resolvingConflicts: Boolean = false,
    val groupedPatients: List<PatientGroup> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val sortSpec: SortSpec = SortSpec.DEFAULT,
    val groupByMode: GroupByMode = GroupByMode.SUPERVISOR,
    val saving: Boolean = false,
    val syncing: Boolean = false,
    val snapshotChoices: List<ShiftSnapshotOption> = emptyList(),
    val showSnapshotPicker: Boolean = false,
    val syncStatus: PatientSyncStatus = PatientSyncStatus.LOCAL,
    val lastBackedUpAt: Long? = null,
    val lastDownloadAt: Long? = null,
    val lastDownloadMessage: Long? = null,
    val unresolvedDeliveries: Int = 0,
    val deliveryLogUnavailable: Boolean = false,
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
)
