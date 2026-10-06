package com.hos.rushdpatients.ui.ward

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.repository.StalePatientEditException
import com.hos.rushdpatients.data.repository.StaleShiftEditException
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.WardMutationRepository
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.export.PatientCsvExporter
import com.hos.rushdpatients.domain.patient.PatientValidationError
import com.hos.rushdpatients.domain.patient.PatientValidationResult
import com.hos.rushdpatients.domain.patient.PatientValidator
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.sync.ConflictChoice
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID
import java.time.Instant

@HiltViewModel
class WardViewModel @Inject constructor(
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
    private val json: Json,
    projectConfigStore: ProjectConfigStore
) : ViewModel() {

    private val _state = MutableStateFlow(
        WardUiState(hospitalName = projectConfigStore.current().hospitalName)
    )
    val state: StateFlow<WardUiState> = _state.asStateFlow()
    val online: StateFlow<Boolean> = NetworkStatus.observe(context).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(0), NetworkStatus.isOnline(context)
    )
    private var observationJob: Job? = null
    private var draftSaveJob: Job? = null

    init {
        load()
        observePreferences()
        observeSyncStatus()
        loadDraft()
        monitorEditabilityBoundary()
        observeMergeConflicts()
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

    private fun observeMergeConflicts() {
        viewModelScope.launch {
            syncService.patientConflicts.collect { conflicts ->
                _state.update { it.copy(mergeConflicts = conflicts) }
            }
        }
    }

    fun resolveMergeConflicts(choices: Map<String, ConflictChoice>) {
        if (_state.value.resolvingConflicts) return
        _state.update { it.copy(resolvingConflicts = true) }
        viewModelScope.launch {
            syncService.resolvePatientConflicts(choices)
                .onSuccess {
                    _state.update {
                        it.copy(
                            resolvingConflicts = false,
                            syncStatus = PatientSyncStatus.BACKED_UP,
                            snackbar = "تم دمج الحقول المتعارضة ونشر النسخة الموحدة"
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            resolvingConflicts = false,
                            syncStatus = PatientSyncStatus.CONFLICT,
                            snackbar = error.message ?: "تعذر حسم التعارضات"
                        )
                    }
                }
        }
    }

    fun dismissMergeConflicts() = syncService.dismissPatientConflicts()

    private fun monitorEditabilityBoundary() {
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                _state.update { current ->
                    current.copy(
                        isReadOnly = current.shift?.date?.let { it != ShiftDate.current() } ?: false
                    )
                }
            }
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val draft = settingsRepository.get(AppConstants.SETTING_PATIENT_DRAFT)
                ?.let { runCatching { json.decodeFromString<PatientDraft>(it) }.getOrNull() }
                ?.takeUnless { it.isEmpty }
            _state.update { it.copy(patientDraft = draft) }
        }
    }

    fun saveDraft(draft: PatientDraft) {
        _state.update { it.copy(patientDraft = draft.takeUnless { value -> value.isEmpty }) }
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(350)
            if (draft.isEmpty) settingsRepository.delete(AppConstants.SETTING_PATIENT_DRAFT)
            else settingsRepository.put(
                AppConstants.SETTING_PATIENT_DRAFT,
                json.encodeToString(PatientDraft.serializer(), draft)
            )
        }
    }

    private suspend fun clearDraft() {
        draftSaveJob?.cancel()
        settingsRepository.delete(AppConstants.SETTING_PATIENT_DRAFT)
        _state.update { it.copy(patientDraft = null) }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            combine(
                settingsRepository.observe(AppConstants.SETTING_PATIENTS_SYNC_PENDING),
                syncStateRepository.observe(SyncChannel.CSV)
            ) { pending, sync ->
                (pending?.toBooleanStrictOrNull() ?: false) to sync?.lastSyncedAtEpochMillis
            }.collect { (pending, backedUpAt) ->
                _state.update {
                    it.copy(
                        lastBackedUpAt = backedUpAt,
                        syncStatus = when {
                            it.syncing -> PatientSyncStatus.SYNCING
                            pending -> PatientSyncStatus.PENDING
                            backedUpAt != null -> PatientSyncStatus.BACKED_UP
                            else -> PatientSyncStatus.LOCAL
                        }
                    )
                }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            settingsRepository.observe(AppConstants.SETTING_PATIENT_DETAILS_EXPANDED)
                .collect { stored ->
                    _state.update {
                        it.copy(patientDetailsExpanded = stored?.toBooleanStrictOrNull() ?: false)
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.observe(AppConstants.SETTING_PATIENT_TWO_COLUMN)
                .collect { stored ->
                    _state.update {
                        it.copy(twoColumn = stored?.toBooleanStrictOrNull() ?: false)
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.observe(AppConstants.SETTING_PATIENT_COMPACT_DENSITY)
                .collect { stored ->
                    _state.update {
                        it.copy(compactCards = stored?.toBooleanStrictOrNull() ?: false)
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.observe(AppConstants.SETTING_GROUP_BY_MODE)
                .collect { stored ->
                    val mode = GroupByMode.fromSetting(stored)
                    _state.update { current ->
                        current.copy(
                            groupByMode = mode,
                            groupedPatients = groupPatients(
                                current.patients,
                                current.doctors,
                                mode
                            )
                        )
                    }
                }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val initialShift = shiftRepository.getOrCreateForDate(ShiftDate.current())
                observeShift(initialShift.id)
                if (patientRepository.countForShift(initialShift.id) == 0 &&
                    settingsRepository.getBoolean(AppConstants.SETTING_GUIDED_ROLLOVER, false)
                ) {
                    val previous = shiftRepository.getRecent(12)
                        .firstOrNull { it.date < initialShift.date }
                    val candidates = previous?.let { patientRepository.getForShift(it.id) }.orEmpty()
                    _state.update { it.copy(rolloverPatients = candidates) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "خطأ") }
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
                            _state.update { it.copy(loading = false, error = "الوردية غير موجودة") }
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
                                groupedPatients = groupPatients(sorted, doctors, mode),
                                doctors = doctors,
                                sortSpec = spec,
                                error = null
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "خطأ") }
            }
        }
    }

    fun bringLatestData() = fetchRemoteData(previous = false)

    fun dismissRollover() = _state.update { it.copy(rolloverPatients = emptyList()) }

    private var activityJob: Job? = null

    fun loadLocalActivity() {
        viewModelScope.launch {
            try {
                val activity = auditRepository.getRecent()
                _state.update { it.copy(recentActivity = activity) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(activityError = e.message ?: "تعذر تحميل النشاط المحلي") }
            }
        }
    }

    fun loadRecentActivity() {
        if (activityJob?.isActive == true) return
        activityJob = viewModelScope.launch {
            _state.update { it.copy(activityLoading = true, activityError = null) }
            try {
                val activity = auditRepository.getRecent()
                _state.update { it.copy(recentActivity = activity) }
                val publications = syncService.getPublicationJournal()
                _state.update { it.copy(publications = publications) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(activityError = e.message ?: "تعذر تحديث النشاط والمنشورات") }
            } finally {
                _state.update { it.copy(activityLoading = false) }
            }
        }
    }

    fun applyRollover(decisions: Map<String, RolloverDecision>) {
        val shiftId = editableShiftId() ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            saveAction(onSuccess = {}) {
                val candidates = _state.value.rolloverPatients
                val rolled = candidates.filter {
                    decisions[it.id] in setOf(
                        RolloverDecision.CONTINUE,
                        RolloverDecision.CONTINUE_AND_EDIT,
                        RolloverDecision.REASSIGN
                    )
                }.mapIndexed { index, patient ->
                    val decision = decisions[patient.id] ?: RolloverDecision.SKIP
                    patient.copy(
                        id = UUID.randomUUID().toString(),
                        sortOrder = index + 1,
                        revision = 1,
                        updatedAt = Instant.now(),
                        lastEditedByDoctorId = sessionManager.current()?.doctorId,
                        lastEditedByName = sessionManager.current()?.doctorName,
                        responsibleResidentId = if (decision == RolloverDecision.REASSIGN) null else patient.responsibleResidentId,
                        responsibleSpecialistId = if (decision == RolloverDecision.REASSIGN) null else patient.responsibleSpecialistId
                    ) to decision
                }
                val selected = rolled.map { it.first }
                wardMutations.rollover(
                    selected,
                    shiftId,
                    candidates.map { patient ->
                        patient to (decisions[patient.id] ?: RolloverDecision.SKIP).name.lowercase()
                    },
                    sessionManager.current()
                )
                val reviewPatients = rolled.filter { (_, decision) ->
                    decision == RolloverDecision.CONTINUE_AND_EDIT ||
                        decision == RolloverDecision.REASSIGN
                }.map { it.first }
                _state.update {
                    it.copy(
                        rolloverPatients = emptyList(),
                        rolloverReviewPatients = reviewPatients,
                        snackbar = "تم ترحيل ${selected.size} مريض؛ يحتاج ${reviewPatients.size} إلى مراجعة"
                    )
                }
            }
        }
    }

    fun consumeRolloverReview(patientId: String) {
        _state.update { state ->
            state.copy(rolloverReviewPatients = state.rolloverReviewPatients.filterNot { it.id == patientId })
        }
    }

    fun bringPreviousData() = fetchRemoteData(previous = true)

    fun showDownloadedSnapshots() {
        if (_state.value.syncing) return
        viewModelScope.launch {
            val downloaded = shiftRepository.getRecent(12)
                .filter { it.csvMessageId != null }
            val latestBundleMessageId = downloaded.maxOfOrNull { it.csvMessageId ?: Long.MIN_VALUE }
            val choices = downloaded
                .filter { it.csvMessageId == latestBundleMessageId }
                .sortedByDescending { it.date }
                .take(AppConstants.CSV_BUNDLE_SHIFT_COUNT)
                .map { shift ->
                    ShiftSnapshotOption(
                        shiftId = shift.id,
                        date = shift.date,
                        patientCount = patientRepository.countForShift(shift.id)
                    )
                }
            if (choices.isEmpty()) {
                _state.update {
                    it.copy(snackbar = "نزّل أحدث ملف أولاً لعرض المناوبات المحفوظة فيه")
                }
            } else {
                _state.update { it.copy(snapshotChoices = choices, showSnapshotPicker = true) }
            }
        }
    }

    fun dismissSnapshotPicker() {
        _state.update { it.copy(showSnapshotPicker = false) }
    }

    fun selectDownloadedSnapshot(shiftId: String) {
        val selected = _state.value.snapshotChoices.firstOrNull { it.shiftId == shiftId } ?: return
        observeShift(shiftId)
        _state.update {
            it.copy(
                showSnapshotPicker = false,
                snackbar = "تم عرض مناوبة ${selected.date}"
            )
        }
    }

    fun togglePatientDetails() {
        val expanded = !_state.value.patientDetailsExpanded
        _state.update { it.copy(patientDetailsExpanded = expanded) }
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_PATIENT_DETAILS_EXPANDED, expanded)
        }
    }

    fun setCompactCards(compact: Boolean) {
        _state.update { it.copy(compactCards = compact) }
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_PATIENT_COMPACT_DENSITY, compact)
        }
    }

    fun setGroupByMode(mode: GroupByMode) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_GROUP_BY_MODE, mode.name)
            _state.update { current ->
                current.copy(
                    groupByMode = mode,
                    groupedPatients = groupPatients(current.patients, current.doctors, mode)
                )
            }
        }
    }

    fun exportCsv() {
        if (_state.value.exportingCsv) return
        val shiftId = _state.value.shift?.id
        if (shiftId == null) {
            _state.update { it.copy(snackbar = "لا توجد وردية للتصدير") }
            return
        }
        _state.update { it.copy(exportingCsv = true, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            try {
                when (val result = csvExporter.exportShift(shiftId)) {
                    is PatientCsvExporter.Result.Success -> _state.update {
                        it.copy(
                            exportingCsv = false,
                            snackbar = "تم حفظ ${result.patientCount} مريض في ${result.fileName}",
                            lastOperation = "آخر عملية ناجحة: حفظ ${result.fileName}"
                        )
                    }
                    is PatientCsvExporter.Result.Failure -> _state.update {
                        it.copy(
                            exportingCsv = false,
                            snackbar = result.message,
                            retryAction = WardRetryAction.EXPORT_CSV
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        exportingCsv = false,
                        snackbar = e.message ?: "فشل تصدير CSV",
                        retryAction = WardRetryAction.EXPORT_CSV
                    )
                }
            }
        }
    }

    private fun fetchRemoteData(previous: Boolean) {
        if (_state.value.syncing) return
        val retry = if (previous) WardRetryAction.FETCH_PREVIOUS
        else WardRetryAction.FETCH_LATEST
        if (!NetworkStatus.isOnline(context)) {
            _state.update {
                it.copy(
                    snackbar = "لا يوجد اتصال بالإنترنت — أعد المحاولة عند توفر الشبكة",
                    syncStatus = PatientSyncStatus.PENDING,
                    retryAction = retry
                )
            }
            return
        }
        _state.update {
            it.copy(
                syncing = true,
                syncStatus = PatientSyncStatus.SYNCING,
                snackbar = null,
                retryAction = null
            )
        }
        viewModelScope.launch {
            // Registry refresh merges with pending local edits and routes conflicts to admin review.
            val doctorsResult = syncService.refreshDoctorsFromRemote()
            if (doctorsResult.isFailure) {
                _state.update {
                    it.copy(
                        syncing = false,
                        syncStatus = PatientSyncStatus.PENDING,
                        snackbar = doctorsResult.exceptionOrNull()?.message
                            ?: "فشل جلب سجل الأطباء",
                        retryAction = retry
                    )
                }
                return@launch
            }

            val result = if (previous) {
                syncService.fetchPreviousCsv()
            } else {
                syncService.fetchLatestCsv()
            }
            result.onSuccess { fetched ->
                observeShift(fetched.shiftId)
                val source = if (previous) "النسخة السابقة" else "أحدث نسخة"
                _state.update {
                    it.copy(
                        syncing = false,
                        syncStatus = PatientSyncStatus.BACKED_UP,
                        snackbar = "تم استبدال البيانات المحلية بـ$source",
                        lastOperation = "آخر عملية ناجحة: جلب $source",
                        retryAction = null
                    )
                }
            }.onFailure { error ->
                _state.update {
                    val message = error.message ?: "فشل جلب البيانات"
                    it.copy(
                        syncing = false,
                        syncStatus = if (
                            message.contains("أقدم") || message.contains("تعارض")
                        ) PatientSyncStatus.CONFLICT else PatientSyncStatus.PENDING,
                        snackbar = message,
                        retryAction = retry
                    )
                }
            }
        }
    }

    fun addPatient(patient: Patient, onSuccess: () -> Unit = {}) {
        val shiftId = editableShiftId() ?: return
        viewModelScope.launch {
            if (!validateForSave(patient)) return@launch
            if (patientRepository.countForShift(shiftId) >= AppConstants.MAX_PATIENTS_PER_SHIFT) {
                showError("تم الوصول إلى الحد الأقصى لعدد المرضى")
                return@launch
            }
            saveAction(onSuccess) {
                requireEditableShift(shiftId)
                wardMutations.addPatient(patient, shiftId, sessionManager.current())
                clearDraft()
            }
        }
    }

    fun updatePatient(
        patient: Patient,
        onStale: (Patient?) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val shiftId = editableShiftId() ?: return
        viewModelScope.launch {
            if (!validateForSave(patient)) return@launch
            saveAction(onSuccess, onStale = { error ->
                if (error !is StalePatientEditException) false else {
                    val latest = patientRepository.getById(patient.id)
                    onStale(latest?.takeIf { patientRepository.getShiftId(it.id) == shiftId })
                    true
                }
            }) {
                requireEditableShift(shiftId)
                wardMutations.updatePatient(patient, shiftId, sessionManager.current())
            }
        }
    }

    fun deletePatient(patient: Patient, onSuccess: (Patient) -> Unit = {}) {
        val shiftId = editableShiftId() ?: return
        var deleted: Patient? = null
        viewModelScope.launch {
            saveAction(onSuccess = { deleted?.let(onSuccess) }) {
                requireEditableShift(shiftId)
                deleted = wardMutations.deletePatient(patient, shiftId, sessionManager.current())
            }
        }
    }

    fun loadRecycleBin() {
        val shiftId = _state.value.shift?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(deletedPatients = patientRepository.getSoftDeletedForShift(shiftId)) }
        }
    }

    fun restorePatient(patient: Patient) {
        val shiftId = editableShiftId() ?: return
        viewModelScope.launch {
            saveAction(onSuccess = { loadRecycleBin() }) {
                requireEditableShift(shiftId)
                wardMutations.restorePatient(patient, shiftId, sessionManager.current())
            }
        }
    }

    fun setPriority(patient: Patient, priority: Boolean) {
        val shiftId = editableShiftId() ?: return
        viewModelScope.launch {
            saveAction(onSuccess = {}) {
                requireEditableShift(shiftId)
                wardMutations.updatePatient(
                    patient.copy(isPriority = priority), shiftId, sessionManager.current()
                )
            }
        }
    }

    fun dismissSyncHint() {
        _state.update { it.copy(showSyncHint = false) }
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_SYNC_LONG_PRESS_HINT_SHOWN, true)
        }
    }

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
    ) {
        val shiftId = editableShiftId() ?: return
        val shift = _state.value.shift?.takeIf { it.id == shiftId } ?: return
        val activeIds = _state.value.doctors.filterNot { it.isDeleted }.mapTo(mutableSetOf()) { it.id }
        when {
            ids.size !in AppConstants.MIN_SHIFT_DOCTORS..AppConstants.MAX_SHIFT_DOCTORS -> {
                showError("اختر من ${AppConstants.MIN_SHIFT_DOCTORS} إلى ${AppConstants.MAX_SHIFT_DOCTORS} أطباء")
                return
            }
            ids.distinct().size != ids.size || ids.any { it !in activeIds } -> {
                showError("اختيار أطباء المناوبة غير صالح")
                return
            }
        }
        viewModelScope.launch {
            saveAction(onSuccess, onStale = { error ->
                if (error !is StaleShiftEditException) false else {
                    onStale(shiftRepository.getById(shiftId))
                    true
                }
            }) {
                requireEditableShift(shiftId)
                wardMutations.setShiftDoctors(
                    shiftId = shift.id,
                    doctorIds = ids,
                    expectedRevision = expectedRevision,
                    actor = sessionManager.current()
                )
            }
        }
    }

    fun applySort(
        spec: SortSpec,
        expectedRevision: Long,
        onStale: (Shift?) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        val shiftId = editableShiftId() ?: return
        viewModelScope.launch {
            saveAction(onSuccess, onStale = { error ->
                if (error !is StaleShiftEditException) false else {
                    onStale(shiftRepository.getById(shiftId))
                    true
                }
            }) {
                requireEditableShift(shiftId)
                wardMutations.setShiftSort(
                    shiftId = shiftId,
                    sortSpecJson = SortSpecCodec.encode(spec),
                    expectedRevision = expectedRevision,
                    actor = sessionManager.current()
                )
            }
        }
    }

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    private fun editableShiftId(): String? {
        val shift = _state.value.shift ?: return null
        if (shift.date != ShiftDate.current()) {
            showError("هذه مناوبة محفوظة للعرض والاستعادة فقط ولا يمكن تعديلها")
            return null
        }
        return shift.id
    }

    private suspend fun requireEditableShift(shiftId: String) {
        val shift = shiftRepository.getById(shiftId)
        require(shift != null && shift.date == ShiftDate.current()) {
            "هذه مناوبة محفوظة للعرض والاستعادة فقط ولا يمكن تعديلها"
        }
    }

    private suspend fun validateForSave(patient: Patient): Boolean {
        val result = PatientValidator.validate(patient)
        if (result is PatientValidationResult.Invalid) {
            showError(validationMessage(result))
            return false
        }
        val doctorsById = _state.value.doctors.associateBy { it.id }
        val resident = patient.responsibleResidentId?.let(doctorsById::get)
        val specialist = patient.responsibleSpecialistId?.let(doctorsById::get)
        if (!patient.responsibleResidentId.isNullOrBlank() &&
            (resident == null || !resident.clinicalRole.canBeResident())
        ) {
            showError("اختر طبيباً مصنفاً كمقيم")
            return false
        }
        if (!patient.responsibleSpecialistId.isNullOrBlank() &&
            (specialist == null || !specialist.clinicalRole.canBeSupervisor())
        ) {
            showError("اختر طبيباً مصنفاً كاختصاصي")
            return false
        }
        return true
    }

    private suspend fun saveAction(
        onSuccess: () -> Unit,
        onStale: suspend (Exception) -> Boolean = { false },
        action: suspend () -> Unit
    ) {
        _state.update { it.copy(saving = true) }
        try {
            action()
            _state.update { it.copy(saving = false) }
            onSuccess()
        } catch (e: CancellationException) {
            _state.update { it.copy(saving = false) }
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(saving = false) }
            try {
                if (!onStale(e)) showError(e.message ?: "تعذر حفظ التغييرات")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (reloadError: Exception) {
                showError(reloadError.message ?: "تعذر تحميل أحدث نسخة؛ المسودة باقية")
            }
        }
    }

    private fun showError(message: String) = _state.update { it.copy(snackbar = message) }

    private fun sortPatients(
        patients: List<Patient>,
        doctors: List<Doctor>,
        spec: SortSpec
    ): List<Patient> {
        if (spec.levels.isEmpty()) return patients.sortedWith(
            compareByDescending<Patient> { it.isPriority }.thenBy { it.sortOrder }
        )
        val names = doctors.associate { it.id to it.fullName }
        return patients.sortedWith(
            compareByDescending<Patient> { it.isPriority }
                .then(PatientComparators.forSpec(spec, names))
        )
    }

    private fun groupPatients(
        patients: List<Patient>,
        doctors: List<Doctor>,
        mode: GroupByMode
    ): List<PatientGroup> {
        if (mode == GroupByMode.NONE || patients.isEmpty()) return emptyList()
        val names = doctors.associate { it.id to it.fullName }

        return when (mode) {
            GroupByMode.NONE -> emptyList()

            GroupByMode.SUPERVISOR -> patients
                .groupBy { it.responsibleSpecialistId }
                .map { (id, list) ->
                    PatientGroup(
                        key = id,
                        name = id?.let(names::get) ?: "بدون اختصاصي",
                        patients = list
                    )
                }
                .sortedWith(compareBy({ it.key == null }, { it.name }))

            GroupByMode.RESIDENT -> patients
                .groupBy { it.responsibleResidentId }
                .map { (id, list) ->
                    PatientGroup(
                        key = id,
                        name = id?.let(names::get) ?: "بدون مقيم",
                        patients = list
                    )
                }
                .sortedWith(compareBy({ it.key == null }, { it.name }))

            GroupByMode.DIAGNOSIS -> patients
                .groupBy { it.diagnosisType }
                .map { (type, list) ->
                    PatientGroup(
                        key = type.code,
                        name = type.arabicLabel,
                        patients = list
                    )
                }
                .sortedBy { it.name }

            GroupByMode.GENDER -> patients
                .groupBy { it.gender }
                .map { (gender, list) ->
                    PatientGroup(
                        key = gender.code,
                        name = if (gender == Gender.MALE) "ذكر" else "أنثى",
                        patients = list
                    )
                }
                .sortedBy { it.name }

            GroupByMode.ADMISSION_DATE -> patients
                .groupBy { it.admittanceDate }
                .map { (date, list) ->
                    PatientGroup(
                        key = date?.toString(),
                        name = date?.toString() ?: "بدون تاريخ دخول",
                        patients = list
                    )
                }
                .sortedWith(
                    compareBy<PatientGroup> { it.key == null }
                        .thenByDescending { it.key ?: "" }
                )
        }
    }

    private fun validationMessage(result: PatientValidationResult.Invalid): String =
        when (result.errors.firstOrNull()) {
            PatientValidationError.NAME_EMPTY -> "اسم المريض مطلوب"
            PatientValidationError.ADMITTANCE_NUMBER_EMPTY -> "رقم القبول الحالي مطلوب"
            PatientValidationError.ADMITTANCE_DATE_INVALID -> "تاريخ الدخول غير صحيح"
            PatientValidationError.BIRTH_DATE_INVALID -> "تاريخ الميلاد غير صحيح"
            PatientValidationError.INITIAL_DIAGNOSIS_EMPTY -> "التشخيص الأولي مطلوب"
            PatientValidationError.TREATMENT_PLAN_EMPTY -> "الخطة العلاجية مطلوبة"
            PatientValidationError.FOLLOW_UP_EMPTY -> "المتابعة مطلوبة"
            PatientValidationError.RESIDENT_EQUALS_SUPERVISOR -> "المقيم والاختصاصي يجب أن يكونا مختلفين"
            null -> "بيانات المريض غير صحيحة"
        }
}
