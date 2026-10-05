package com.hos.rushdpatients.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.domain.auth.BiometricHelper
import com.hos.rushdpatients.domain.backup.EncryptedBackupManager
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.export.PatientCsvExporter
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.sync.AutoSyncScheduler
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.util.ShiftDate
import com.hos.rushdpatients.util.NetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val doctorRepository: DoctorRepository,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val syncStateRepository: SyncStateRepository,
    private val sessionManager: SessionManager,
    private val syncService: SyncService,
    private val csvExporter: PatientCsvExporter,
    private val biometricHelper: BiometricHelper,
    private val encryptedBackupManager: EncryptedBackupManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    init {
        load()
        observeSyncState()
        observeSupervisors()
    }

    private fun observeSupervisors() {
        viewModelScope.launch {
            doctorRepository.observeAll().collect { doctors ->
                _state.update {
                    it.copy(supervisors = doctors.filter { doctor ->
                        doctor.clinicalRole == ClinicalRole.SUPERVISOR && !doctor.isDeleted
                    })
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val session = sessionManager.current()
                val bioAvailable = biometricHelper.isAvailable()
                val bioEnabled = settingsRepository.getBoolean(AppConstants.SETTING_BIOMETRIC_ENABLED, false)
                _state.update {
                    it.copy(
                        doctorName = session?.doctorName ?: "",
                        role = when {
                            session == null -> ""
                            session.isAdmin -> "مدير"
                            else -> "غير مدير"
                        },
                        autoSync = settingsRepository.isAutoSyncEnabled(),
                        syncWifiOnly = settingsRepository.isSyncWifiOnly(),
                        reportAsPdf = settingsRepository.isReportAsPdf(),
                        pdfStyle = PdfStyle.fromSetting(
                            settingsRepository.get(AppConstants.SETTING_PDF_STYLE)
                        ),
                        pdfPatientCardStyle = PatientCardStyle.fromSetting(
                            settingsRepository.get(AppConstants.SETTING_PDF_PATIENT_CARD_STYLE)
                        ),
                        pdfOrientation = PdfOrientation.fromSetting(
                            settingsRepository.get(AppConstants.SETTING_PDF_ORIENTATION)
                        ),
                        pdfPaperSize = PdfPaperSize.fromSetting(
                            settingsRepository.get(AppConstants.SETTING_PDF_PAPER_SIZE)
                        ),
                        pdfColorPreset = PdfColorPreset.fromSetting(
                            settingsRepository.get(AppConstants.SETTING_PDF_COLOR_PRESET)
                        ),
                        pdfDarkMode = settingsRepository.getBoolean(
                            AppConstants.SETTING_PDF_DARK_MODE,
                            false
                        ),
                        pdfSeparateBySupervisor = settingsRepository.getBoolean(
                            AppConstants.SETTING_PDF_SEPARATE_BY_SUPERVISOR,
                            false
                        ),
                        appTheme = AppThemePreset.fromSetting(
                            settingsRepository.get(AppConstants.SETTING_APP_THEME)
                        ),
                        fontScale = AppFontScale.fromSetting(
                            settingsRepository.get(AppConstants.SETTING_FONT_SCALE)
                        ),
                        patientDetailsExpanded = settingsRepository.getBoolean(
                            AppConstants.SETTING_PATIENT_DETAILS_EXPANDED,
                            false
                        ),
                        patientTwoColumn = settingsRepository.getBoolean(
                            AppConstants.SETTING_PATIENT_TWO_COLUMN,
                            false
                        ),
                        patientCompactDensity = settingsRepository.getBoolean(
                            AppConstants.SETTING_PATIENT_COMPACT_DENSITY,
                            false
                        ),
                        guidedRollover = settingsRepository.getBoolean(
                            AppConstants.SETTING_GUIDED_ROLLOVER,
                            false
                        ),
                        biometricAvailable = bioAvailable,
                        biometricEnabled = bioEnabled && bioAvailable,
                        autoLockMinutes = settingsRepository.getAutoLockMinutes(),
                        deviceId = syncService.getOrCreateDeviceId()
                    )
                }
                refreshStorageHealth()
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: "تعذر تحميل الإعدادات") }
            }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncStateRepository.observe(SyncChannel.CSV).collect { sync ->
                _state.update {
                    it.copy(lastCsvSync = sync?.lastSyncedAtEpochMillis?.let(::format))
                }
            }
        }
        viewModelScope.launch {
            syncStateRepository.observe(SyncChannel.DOCTORS).collect { sync ->
                _state.update {
                    it.copy(lastDoctorsSync = sync?.lastSyncedAtEpochMillis?.let(::format))
                }
            }
        }
    }

    fun setAutoSync(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSync(value)
            _state.update { it.copy(autoSync = value) }
            AutoSyncScheduler.configure(context, value, _state.value.syncWifiOnly)
        }
    }

    fun setSyncWifiOnly(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSyncWifiOnly(value)
            _state.update { it.copy(syncWifiOnly = value) }
            AutoSyncScheduler.configure(context, _state.value.autoSync, value)
        }
    }

    fun setReportAsPdf(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReportAsPdf(value)
            _state.update { it.copy(reportAsPdf = value) }
        }
    }

    fun setPdfStyle(value: PdfStyle) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_PDF_STYLE, value.name)
            _state.update { it.copy(pdfStyle = value) }
        }
    }

    fun setPdfPatientCardStyle(value: PatientCardStyle) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_PDF_PATIENT_CARD_STYLE, value.name)
            _state.update { it.copy(pdfPatientCardStyle = value) }
        }
    }

    fun setPdfOrientation(value: PdfOrientation) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_PDF_ORIENTATION, value.name)
            _state.update { it.copy(pdfOrientation = value) }
        }
    }

    fun setPdfPaperSize(value: PdfPaperSize) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_PDF_PAPER_SIZE, value.name)
            _state.update { it.copy(pdfPaperSize = value) }
        }
    }

    fun setPdfColorPreset(value: PdfColorPreset) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_PDF_COLOR_PRESET, value.name)
            _state.update { it.copy(pdfColorPreset = value) }
        }
    }

    fun setPdfDarkMode(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_PDF_DARK_MODE, value)
            _state.update { it.copy(pdfDarkMode = value) }
        }
    }

    fun setPdfSeparateBySupervisor(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_PDF_SEPARATE_BY_SUPERVISOR, value)
            _state.update { it.copy(pdfSeparateBySupervisor = value) }
        }
    }

    fun setAppTheme(value: AppThemePreset) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_APP_THEME, value.name)
            _state.update { it.copy(appTheme = value) }
        }
    }

    fun setFontScale(value: AppFontScale) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_FONT_SCALE, value.name)
            _state.update { it.copy(fontScale = value) }
        }
    }

    fun setPatientDetailsExpanded(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_PATIENT_DETAILS_EXPANDED, value)
            _state.update { it.copy(patientDetailsExpanded = value) }
        }
    }

    fun setPatientTwoColumn(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_PATIENT_TWO_COLUMN, value)
            _state.update { it.copy(patientTwoColumn = value) }
        }
    }

    fun setPatientCompactDensity(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_PATIENT_COMPACT_DENSITY, value)
            _state.update { it.copy(patientCompactDensity = value) }
        }
    }

    fun setGuidedRollover(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_GUIDED_ROLLOVER, value)
            _state.update { it.copy(guidedRollover = value) }
        }
    }

    fun setSupervisorGroupChatId(doctorId: String, rawValue: String) {
        if (_state.value.savingSupervisorGroupId != null) return
        val session = sessionManager.current()
        if (session?.isAdmin != true) {
            _state.update { it.copy(snackbar = "هذه الإعدادات متاحة للمدير فقط") }
            return
        }
        val trimmed = rawValue.trim()
        val chatId = if (trimmed.isEmpty()) null else trimmed.toLongOrNull()
        if (trimmed.isNotEmpty() && (chatId == null || chatId >= 0L)) {
            _state.update {
                it.copy(snackbar = "معرف مجموعة تليجرام يجب أن يكون رقماً سالباً")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(savingSupervisorGroupId = doctorId, snackbar = null) }
            try {
                val doctor = doctorRepository.getById(doctorId)
                    ?: error("المشرف غير موجود")
                require(doctor.clinicalRole == ClinicalRole.SUPERVISOR) {
                    "يمكن ربط مجموعة بالمشرفين فقط"
                }
                doctorRepository.upsert(doctor.copy(supervisorGroupChatId = chatId))
                val syncResult = syncService.uploadCurrentDoctors()
                _state.update {
                    it.copy(
                        savingSupervisorGroupId = null,
                        snackbar = syncResult.fold(
                            onSuccess = { "تم حفظ مجموعة المشرف ومزامنتها" },
                            onFailure = { error ->
                                "تم الحفظ محلياً وتعذرت المزامنة: ${error.message.orEmpty()}"
                            }
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        savingSupervisorGroupId = null,
                        snackbar = e.message ?: "تعذر حفظ معرف المجموعة"
                    )
                }
            }
        }
    }

    fun setBiometric(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_BIOMETRIC_ENABLED, value)
            _state.update { it.copy(biometricEnabled = value) }
        }
    }

    fun setAutoLockMinutes(value: Int) {
        viewModelScope.launch {
            settingsRepository.setAutoLockMinutes(value)
            _state.update { it.copy(autoLockMinutes = value) }
        }
    }

    fun bringLatest() {
        if (_state.value.syncing) return
        if (!NetworkStatus.isOnline(context)) {
            _state.update {
                it.copy(
                    snackbar = "لا يوجد اتصال بالإنترنت — أعد المحاولة لاحقاً",
                    retryAction = SettingsRetryAction.FETCH
                )
            }
            return
        }
        _state.update { it.copy(syncing = true, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            // An explicit fetch is allowed to accept the authoritative remote doctor registry.
            // Background sync remains conservative and will not overwrite a newer registry.
            val doctorsResult = syncService.refreshDoctorsFromRemote()
            if (doctorsResult.isFailure) {
                _state.update {
                    it.copy(
                        syncing = false,
                        snackbar = doctorsResult.exceptionOrNull()?.message
                            ?: "فشل جلب سجل الأطباء",
                        retryAction = SettingsRetryAction.FETCH
                    )
                }
                return@launch
            }
            syncService.fetchLatestCsv()
                .onSuccess { r ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = "تم جلب البيانات: جديد ${r.inserted}، محدّث ${r.updated}",
                            lastOperation = "آخر عملية ناجحة: تنزيل بيانات المرضى",
                            retryAction = null,
                            lastCsvSync = format(Instant.now().toEpochMilli())
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = e.message ?: "فشل جلب البيانات",
                            retryAction = SettingsRetryAction.FETCH
                        )
                    }
                }
        }
    }

    fun uploadCurrent() {
        if (_state.value.syncing) return
        if (!NetworkStatus.isOnline(context)) {
            _state.update {
                it.copy(
                    snackbar = "لا يوجد اتصال بالإنترنت — بقيت البيانات محفوظة محلياً",
                    retryAction = SettingsRetryAction.UPLOAD
                )
            }
            return
        }
        _state.update { it.copy(syncing = true, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            val shiftId = shiftRepository.getByDate(ShiftDate.current())?.id
            if (shiftId == null) {
                _state.update { it.copy(syncing = false, snackbar = "لا توجد وردية لرفعها") }
                return@launch
            }
            val doctorsResult = syncService.uploadCurrentDoctors()
            if (doctorsResult.isFailure) {
                _state.update {
                    it.copy(
                        syncing = false,
                        snackbar = doctorsResult.exceptionOrNull()?.message
                            ?: "فشل رفع سجل الأطباء",
                        retryAction = SettingsRetryAction.UPLOAD
                    )
                }
                return@launch
            }
            syncService.uploadCsv(shiftId)
                .onSuccess { r ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = "تم رفع البيانات (${r.patientCount} مريض)",
                            lastOperation = "آخر عملية ناجحة: رفع ${r.patientCount} مريض",
                            retryAction = null
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = e.message ?: "فشل الرفع",
                            retryAction = SettingsRetryAction.UPLOAD
                        )
                    }
                }
        }
    }

    fun forceUploadCurrent() {
        if (_state.value.syncing) return
        if (!NetworkStatus.isOnline(context)) {
            _state.update { it.copy(snackbar = "لا يوجد اتصال بالإنترنت", retryAction = SettingsRetryAction.FORCE_UPLOAD) }
            return
        }
        _state.update { it.copy(syncing = true, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            val shiftId = shiftRepository.getByDate(ShiftDate.current())?.id
            if (shiftId == null) {
                _state.update { it.copy(syncing = false, snackbar = "لا توجد وردية حالية") }
                return@launch
            }
            syncService.forceUploadCsv(shiftId)
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = "تم فرض النسخة الحالية (${result.patientCount} مريض)",
                            lastOperation = "آخر عملية: فرض النسخة المحلية"
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = error.message ?: "تعذر فرض النسخة الحالية",
                            retryAction = SettingsRetryAction.FORCE_UPLOAD
                        )
                    }
                }
        }
    }

    fun exportCsv() {
        if (_state.value.exportingCsv) return
        _state.update { it.copy(exportingCsv = true, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            try {
                when (val result = csvExporter.exportCurrentShift()) {
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
                            retryAction = SettingsRetryAction.EXPORT
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
                        retryAction = SettingsRetryAction.EXPORT
                    )
                }
            }
        }
    }

    fun exportEncryptedBackup(uri: Uri, password: String) {
        if (_state.value.backupBusy) return
        _state.update { it.copy(backupBusy = true, snackbar = null) }
        viewModelScope.launch {
            runCatching { encryptedBackupManager.exportCurrent(uri, password.toCharArray()) }
                .onSuccess { count ->
                    settingsRepository.putLong(
                        AppConstants.SETTING_LAST_ENCRYPTED_BACKUP_AT,
                        Instant.now().toEpochMilli()
                    )
                    _state.update {
                        it.copy(backupBusy = false, snackbar = "تم حفظ نسخة مشفرة لـ$count مريض")
                    }
                    refreshStorageHealth()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(backupBusy = false, snackbar = error.message ?: "فشل إنشاء النسخة المشفرة")
                    }
                }
        }
    }

    fun restoreEncryptedBackup(uri: Uri, password: String) {
        if (_state.value.backupBusy) return
        _state.update { it.copy(backupBusy = true, snackbar = null) }
        viewModelScope.launch {
            runCatching { encryptedBackupManager.restore(uri, password.toCharArray()) }
                .onSuccess { count ->
                    _state.update {
                        it.copy(backupBusy = false, snackbar = "تمت استعادة $count مريض من النسخة المشفرة")
                    }
                    refreshStorageHealth()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(backupBusy = false, snackbar = error.message ?: "فشلت استعادة النسخة")
                    }
                }
        }
    }

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun retryLastAction() {
        when (_state.value.retryAction) {
            SettingsRetryAction.FETCH -> bringLatest()
            SettingsRetryAction.UPLOAD -> uploadCurrent()
            SettingsRetryAction.FORCE_UPLOAD -> forceUploadCurrent()
            SettingsRetryAction.EXPORT -> exportCsv()
            null -> Unit
        }
    }

    private suspend fun refreshStorageHealth() {
        val allPatients = patientRepository.getAll()
        val deleted = patientRepository.getSoftDeleted().size
        val shifts = shiftRepository.getRecent(1000).size
        val db = context.getDatabasePath(com.hos.rushdpatients.data.db.AppDatabase.DATABASE_NAME)
        val bytes = listOf(db, java.io.File(db.path + "-wal"), java.io.File(db.path + "-shm"))
            .filter { it.exists() }
            .sumOf { it.length() }
        val size = when {
            bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
            else -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
        }
        val backupAt = settingsRepository.getLong(
            AppConstants.SETTING_LAST_ENCRYPTED_BACKUP_AT,
            0L
        ).takeIf { it > 0L }?.let(::format)
        _state.update {
            it.copy(
                databaseSize = size,
                totalStoredPatients = allPatients.size,
                deletedPatients = deleted,
                storedShifts = shifts,
                lastEncryptedBackup = backupAt
            )
        }
    }

    private fun format(ms: Long): String =
        Instant.ofEpochMilli(ms)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(fmt)
}
