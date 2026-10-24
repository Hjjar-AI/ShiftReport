package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.WardMutationRepository
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.export.PatientCsvExporter
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.sync.KnownShiftSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.util.ShiftDate
import com.hos.rushdpatients.util.NetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import com.hos.rushdpatients.domain.report.ReportDeliveryLedger
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WardViewModel @Inject constructor(
    private val uiText: UiStrings,
    @ApplicationContext private val context: Context,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val wardMutations: WardMutationRepository,
    private val doctorRepository: DoctorRepository,
    private val syncService: SyncService,
    private val settingsRepository: SettingsRepository,
    private val csvExporter: PatientCsvExporter,
    private val syncStateRepository: SyncStateRepository,
    private val sessionManager: SessionManager,
    private val auditRepository: AuditRepository,
    private val adminAuthorizer: AdminAuthorizer,
    private val json: Json,
    private val deliveryLedger: ReportDeliveryLedger,
    projectConfigStore: ProjectConfigStore
) : ViewModel() {

    internal val editor = WardEditorState()
    private val _taskNow = MutableStateFlow(System.currentTimeMillis())
    internal val taskNow: StateFlow<Long> = _taskNow.asStateFlow()

    private val _state = MutableStateFlow(
        WardUiState(hospitalName = projectConfigStore.current().hospitalName)
    )
    val state: StateFlow<WardUiState> = _state.asStateFlow()
    val online: StateFlow<Boolean> = NetworkStatus.observe(context).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(0), NetworkStatus.isOnline(context)
    )
    private var observationJob: Job? = null

    private val saveGuard = WardSaveGuard(uiText,
        _state, shiftRepository)

    private val preferences = WardPreferences(uiText,
        _state, viewModelScope, settingsRepository)

    private val patientActions = WardPatientActions(uiText,
        _state, viewModelScope, shiftRepository, patientRepository, wardMutations, settingsRepository, sessionManager, json, saveGuard)

    private val shiftOperations = WardShiftOperations(uiText,
        _state, viewModelScope, context, shiftRepository, patientRepository, wardMutations, syncService, settingsRepository, csvExporter, sessionManager, saveGuard, ::observeShift)

    private val activityOperations = WardActivityOperations(uiText,
        _state, viewModelScope, syncService, auditRepository, adminAuthorizer)

    init {
        viewModelScope.launch {
            syncService.observeDownloadUndo().collect { available -> _state.update { it.copy(canUndoDownload = available) } }
        }
        load()
        observePreferences()
        observeWorkflowStatus()
        loadDraft()
        monitorEditabilityBoundary()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    showSyncHint = !settingsRepository.getBoolean(
                        AppConstants.SETTING_SYNC_LONG_PRESS_HINT_SHOWN,
                        false
                    )
                )
            }
        }
    }

    private fun observeWorkflowStatus() {
        viewModelScope.launch {
            _state.map { it.shift?.id }.distinctUntilChanged().collectLatest { id ->
                _state.update { it.copy(synchronization = WardSynchronization()) }
                if (id == null) return@collectLatest
                val publication = combine(
                    settingsRepository.observe(AppConstants.SETTING_PATIENTS_SYNC_PENDING),
                    syncStateRepository.observe(SyncChannel.CSV),
                    settingsRepository.observe(AppConstants.SETTING_PATIENT_PUBLISHED_AT_PREFIX + id)
                ) { pending, sync, publishedAt ->
                    Triple(pending?.toBooleanStrictOrNull() ?: false, sync?.lastSyncedAtEpochMillis, publishedAt?.toLongOrNull())
                }
                val selectedSnapshot = combine(
                    _state.map { it.shift to it.patients }.distinctUntilChanged(),
                    settingsRepository.observe(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + id)
                ) { current, csv ->
                    withContext(Dispatchers.Default) {
                        val status = KnownShiftSnapshot.compare(current.first, current.second, csv)
                        val before = csv?.let { runCatching { com.hos.rushdpatients.sync.CsvCodec.decode(it) }.getOrNull() }
                        val baseline = before?.patients.orEmpty().associateBy { it.id }
                        val after = current.second.associateBy { it.id }
                        val count = (baseline.keys + after.keys).count {
                            !com.hos.rushdpatients.sync.samePatientContent(baseline[it], after[it]) ||
                                baseline[it]?.sortOrder != after[it]?.sortOrder
                        } + if (before != null && (before.doctorIds != current.first?.doctorIds ||
                            before.sortSpecJson != current.first?.sortSpecJson)) 1 else 0
                        status to count
                    }
                }
                // An unreadable delivery ledger must not hide publication/download metadata.
                val deliveries = deliveryLedger.observe().map { records ->
                    val rows = records.filter { it.shiftId == id }
                    Triple(rows.count { it.status == "UNKNOWN" }, false,
                        rows.filter { it.status == "SENT" }.maxOfOrNull { it.at })
                }.catch { emit(Triple(0, true, null)) }
                combine(
                    publication,
                    settingsRepository.observe(AppConstants.SETTING_PATIENT_DOWNLOAD_AT_PREFIX + id),
                    settingsRepository.observe(AppConstants.SETTING_PATIENT_DOWNLOAD_MESSAGE_PREFIX + id),
                    deliveries,
                    selectedSnapshot
                ) { published, at, message, delivery, selected ->
                    WardSynchronization(pending = published.first, selectedSnapshot = selected.first, pendingCount = selected.second, lastSynchronizedAt = published.second, publishedAt = published.third,
                        downloadedAt = at?.toLongOrNull(), downloadedMessage = message?.toLongOrNull(),
                        unresolvedDeliveries = delivery.first, deliveryLogUnavailable = delivery.second, lastReportAt = delivery.third)
                }.collect { synchronization ->
                    _state.update { current ->
                        if (current.shift?.id == id) current.copy(synchronization = synchronization) else current
                    }
                }
            }
        }
    }

    private fun monitorEditabilityBoundary() {
        viewModelScope.launch {
            while (true) {
                val midnight = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val nextBoundary = minOf(midnight, ShiftDate.endOf(ShiftDate.current()).toEpochMilli())
                delay((nextBoundary - System.currentTimeMillis()).coerceAtLeast(1_000L))
                _taskNow.value = System.currentTimeMillis()
                _state.update { current ->
                    current.copy(isReadOnly = current.shift?.date?.let { it != ShiftDate.current() } ?: false)
                }
            }
        }
    }

    private fun loadDraft() = patientActions.loadDraft()

    fun loadEditDraft(id: String, done: (PatientDraft?) -> Unit) = patientActions.loadEditDraft(id, done)
    fun discardEditDraft(id: String) = patientActions.discardEditDraft(id)
    fun saveEditDraft(id: String, draft: PatientDraft) = patientActions.saveEditDraft(id, draft)

    fun saveDraft(draft: PatientDraft) = patientActions.saveDraft(draft)

    private fun observePreferences() = preferences.observePreferences()

    private var requestedReviewShiftId: String? = null
    fun selectReviewShift(id: String) { requestedReviewShiftId = id; observeShift(id) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val initialShift = shiftRepository.getOrCreateForDate(ShiftDate.current())
                observeShift(requestedReviewShiftId ?: initialShift.id)
                if (requestedReviewShiftId == null && patientRepository.countForShift(initialShift.id) == 0 &&
                    settingsRepository.getBoolean(AppConstants.SETTING_GUIDED_ROLLOVER, false)
                ) {
                    val previous = shiftRepository.getRecent(12)
                        .firstOrNull { it.date < initialShift.date }
                    val candidates = previous?.let { patientRepository.getForShift(it.id) }.orEmpty()
                    val sourceLabel = previous?.let { rolloverSourceLabel(it) }.orEmpty()
                    _state.update { it.copy(rolloverPatients = candidates, rolloverTarget = initialShift,
                        rolloverSourceLabel = sourceLabel) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: uiText(R.string.ui_ward_view_model_001)) }
            }
        }
    }

    private fun observeShift(shiftId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            try {
                combine(
                    shiftRepository.observeById(shiftId),
                    patientRepository.observeForShift(shiftId),
                    doctorRepository.observeAll()
                ) { shift, patients, doctors -> Triple(shift, patients, doctors) }
                    .collect { (shift, patients, doctors) ->
                        if (shift == null) {
                            val replacement = shiftRepository.getByDate(ShiftDate.current())
                            if (replacement != null && replacement.id != shiftId) {
                                _state.update { it.copy(patientDraft = null, deletedPatients = emptyList(),
                                    rolloverPatients = emptyList(), rolloverTarget = null, patients = emptyList(), groupedPatients = emptyList()) }
                                observeShift(replacement.id)
                            } else _state.update { it.copy(loading = false, shift = null, patients = emptyList(),
                                groupedPatients = emptyList(), error = uiText(R.string.ui_ward_shift_operations_005)) }
                            return@collect
                        }
                        val spec = SortSpecCodec.decode(shift.sortSpecJson)
                        val sorted = sortPatients(patients, doctors, spec)
                        val mode = _state.value.groupByMode
                        _state.update {
                            it.copy(
                                loading = false,
                                shift = shift,
                                isReadOnly = shift.date != ShiftDate.current(),
                                patients = sorted,
                                groupedPatients = groupPatients(uiText, sorted, doctors, mode),
                                doctors = doctors,
                                sortSpec = spec,
                                error = null
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: uiText(R.string.ui_ward_view_model_001)) }
            }
        }
    }

    fun replaceAndFetch(allowOlder: Boolean) = shiftOperations.replaceAndFetch(allowOlder)
    fun reviewPublishAndFetch() = shiftOperations.reviewPublishAndFetch()
    fun confirmPublishAndFetch() = shiftOperations.confirmPublishAndFetch()
    fun dismissFetchReview() = shiftOperations.dismissFetchReview()

    fun bringLatestData() = shiftOperations.bringLatestData()

    private suspend fun rolloverSourceLabel(source: com.hos.rushdpatients.data.model.Shift): String = shiftOperations.rolloverSourceLabel(source)

    fun offerPreviousShift() = shiftOperations.offerPreviousShift()

    fun dismissRollover() = shiftOperations.dismissRollover()

    fun loadLocalActivity() = activityOperations.loadLocalActivity()

    fun loadRecentActivity() = activityOperations.loadRecentActivity()

    fun applyRollover(keepAssignments: Boolean) = shiftOperations.applyRollover(keepAssignments)

    fun bringPreviousData() = shiftOperations.bringPreviousData()

    fun showDownloadedSnapshots() = shiftOperations.showDownloadedSnapshots()

    fun dismissSnapshotPicker() = shiftOperations.dismissSnapshotPicker()

    fun selectDownloadedSnapshot(shiftId: String) = shiftOperations.selectDownloadedSnapshot(shiftId)

    fun togglePatientDetails() = preferences.togglePatientDetails()

    fun setPatientDetailsExpanded(expanded: Boolean) = preferences.setPatientDetailsExpanded(expanded)

    fun setSaveAndNext(enabled: Boolean) = preferences.setSaveAndNext(enabled)

    fun setTwoColumn(enabled: Boolean) = preferences.setTwoColumn(enabled)

    fun setCompactCards(compact: Boolean) = preferences.setCompactCards(compact)

    fun setGroupByMode(mode: GroupByMode) = preferences.setGroupByMode(mode)

    fun exportCsv() = shiftOperations.exportCsv()

    fun undoLastDownload() = shiftOperations.undoLastDownload()

    fun addPatient(patient: Patient, position: Int? = null, expectedPatients: List<Patient>? = null,
        expectedShiftRevision: Long? = null, onSuccess: () -> Unit = {}) = patientActions.addPatient(patient, position, expectedPatients, expectedShiftRevision, onSuccess)

    fun movePatientToTop(patient: Patient) = patientActions.movePatientToTop(patient)

    fun updatePatient(
        patient: Patient,
        onStale: (Patient?) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) = patientActions.updatePatient(patient, onStale, onSuccess)

    fun setTaskDone(patient: Patient, taskId: String, done: Boolean, onSuccess: (Patient) -> Unit) =
        patientActions.setTaskDone(patient, taskId, done, onSuccess)

    fun deletePatient(patient: Patient, onSuccess: (Patient) -> Unit = {}) = patientActions.deletePatient(patient, onSuccess)

    fun loadRecycleBin() = patientActions.loadRecycleBin()

    fun restorePatient(patient: Patient) = patientActions.restorePatient(patient)

    fun setPriority(patient: Patient, priority: Boolean) = patientActions.setPriority(patient, priority)

    fun dismissSyncHint() = preferences.dismissSyncHint()

    fun retryLastAction() {
        when (_state.value.retryAction) {
            WardRetryAction.FETCH_LATEST -> bringLatestData()
            WardRetryAction.FETCH_PREVIOUS -> bringPreviousData()
            WardRetryAction.EXPORT_CSV -> exportCsv()
            null -> Unit
        }
    }

    fun setShiftDoctors(
        ids: List<String>,
        expectedRevision: Long,
        onStale: (Shift?) -> Unit,
        onSuccess: () -> Unit = {}
    ) = patientActions.setShiftDoctors(ids, expectedRevision, onStale, onSuccess)

    fun applySort(
        spec: SortSpec,
        expectedRevision: Long,
        onStale: (Shift?) -> Unit,
        onSuccess: () -> Unit = {}
    ) = patientActions.applySort(spec, expectedRevision, onStale, onSuccess)

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    private fun editableShiftId(): String? = saveGuard.editableShiftId()

    private fun showError(message: String) = saveGuard.showError(message)

}
