package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.config.ProjectProvisioningManager
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.data.repository.DoctorMutationRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.domain.auth.BiometricHelper
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.backup.EncryptedBackupManager
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.export.PendingExportStore
import com.hos.rushdpatients.domain.export.PatientCsvExporter
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfRuleWeight
import com.hos.rushdpatients.pdf.PdfRuleContrast
import com.hos.rushdpatients.pdf.PdfRowShading
import com.hos.rushdpatients.pdf.PdfPresentationPreset
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.sync.EmptyStartCoordinator
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppThemePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val uiText: UiStrings,
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val doctorRepository: DoctorRepository,
    private val doctorMutations: DoctorMutationRepository,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val syncStateRepository: SyncStateRepository,
    private val sessionManager: SessionManager,
    private val syncService: SyncService,
    private val emptyStart: EmptyStartCoordinator,
    private val csvExporter: PatientCsvExporter,
    private val biometricHelper: BiometricHelper,
    private val encryptedBackupManager: EncryptedBackupManager,
    private val projectConfigStore: ProjectConfigStore,
    private val provisioningManager: ProjectProvisioningManager,
    private val adminAuthorizer: AdminAuthorizer,
    private val pendingExports: PendingExportStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(
        backupBusy = savedStateHandle.get<String>(PENDING_BACKUP_ID) != null,
        provisioningBusy = savedStateHandle.get<String>(PENDING_PROVISIONING_ID) != null
    ))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    private val preferences = SettingsPreferences(uiText, _state, viewModelScope, settingsRepository)
    private val dataOperations = SettingsDataOperations(uiText, _state, viewModelScope, context,
        syncService, shiftRepository, csvExporter, ::format)
    private val backupOperations = SettingsBackupOperations(uiText, _state, viewModelScope,
        encryptedBackupManager, savedStateHandle, pendingExports, settingsRepository, ::refreshStorageHealth)
    private val projectOperations = SettingsProjectOperations(uiText, _state, viewModelScope,
        emptyStart, sessionManager, doctorMutations, provisioningManager, projectConfigStore,
        adminAuthorizer, savedStateHandle, ::load, dataOperations::invalidateReview)

    init {
        viewModelScope.launch {
            syncService.observeDownloadUndo().collect { available ->
                _state.update { it.copy(canUndoDownload = available) }
            }
        }

        load()
        observeSyncState()
        observeSupervisors()
    }

    private fun observeSupervisors() {
        viewModelScope.launch {
            combine(sessionManager.sessionFlow, doctorRepository.observeAll()) { session, doctors ->
                val isAdmin = session?.isAdmin == true && doctors.any { doctor ->
                    doctor.id == session.doctorId && doctor.isAdmin && !doctor.isDeleted
                }
                Triple(isAdmin, isAdmin && doctors.any { it.id == session?.doctorId && it.isPermanentAdmin && !it.isDeleted },
                    if (isAdmin) doctors.filter { doctor ->
                        doctor.clinicalRole == ClinicalRole.SUPERVISOR && !doctor.isDeleted
                    } else emptyList())
            }.collect { (isAdmin, permanent, supervisors) ->
                _state.update {
                    it.copy(isAdmin = isAdmin, supervisors = supervisors,
                        isPermanentAdmin = permanent)
                }
            }
        }
    }

    fun prepareEmptyStart() = projectOperations.prepareEmptyStart()

    fun dismissEmptyStart() = projectOperations.dismissEmptyStart()

    fun confirmEmptyStart() = projectOperations.confirmEmptyStart()

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
                            session.isAdmin -> uiText(R.string.ui_ward_drawer_004)
                            else -> uiText(R.string.ui_settings_view_model_001)
                        },
                        reportAsPdf = settingsRepository.isReportAsPdf(),
                        reportWardName = settingsRepository.get(AppConstants.SETTING_REPORT_WARD_NAME).orEmpty(),
                        saveAndNext = settingsRepository.getBoolean(AppConstants.SETTING_SAVE_AND_NEXT, false),
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
                        pdfRuleWeight = PdfRuleWeight.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_RULE_WEIGHT)),
                        pdfRuleContrast = PdfRuleContrast.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_RULE_CONTRAST)),
                        pdfRowShading = PdfRowShading.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_ROW_SHADING)),
                        pdfDarkMode = settingsRepository.getBoolean(
                            AppConstants.SETTING_PDF_DARK_MODE,
                            false
                        ),
                        pdfSeparateBySupervisor = settingsRepository.getBoolean(
                            AppConstants.SETTING_PDF_SEPARATE_BY_SUPERVISOR,
                            false
                        ),
                        appearance = AppAppearance.fromSetting(settingsRepository.get(AppConstants.SETTING_APP_APPEARANCE)),
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
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_settings_view_model_002)) }
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

    fun setReportAsPdf(value: Boolean) = preferences.setReportAsPdf(value)

    fun setPdfStyle(value: PdfStyle) = preferences.setPdfStyle(value)

    fun setPdfPatientCardStyle(value: PatientCardStyle) = preferences.setPdfPatientCardStyle(value)

    fun setPdfOrientation(value: PdfOrientation) = preferences.setPdfOrientation(value)

    fun setPdfPaperSize(value: PdfPaperSize) = preferences.setPdfPaperSize(value)

    fun setPdfColorPreset(value: PdfColorPreset) = preferences.setPdfColorPreset(value)

    fun setPdfRuleWeight(value: PdfRuleWeight) = preferences.setPdfRuleWeight(value)

    fun setPdfRuleContrast(value: PdfRuleContrast) = preferences.setPdfRuleContrast(value)

    fun setPdfRowShading(value: PdfRowShading) = preferences.setPdfRowShading(value)

    fun setPdfPresentationPreset(value: PdfPresentationPreset) = preferences.setPdfPresentationPreset(value)

    fun setPdfDarkMode(value: Boolean) = preferences.setPdfDarkMode(value)

    fun setPdfSeparateBySupervisor(value: Boolean) = preferences.setPdfSeparateBySupervisor(value)

    fun setAppearance(value: AppAppearance) = preferences.setAppearance(value)

    fun setAppTheme(value: AppThemePreset) = preferences.setAppTheme(value)

    fun setFontScale(value: AppFontScale) = preferences.setFontScale(value)

    fun setPatientDetailsExpanded(value: Boolean) = preferences.setPatientDetailsExpanded(value)

    fun setPatientTwoColumn(value: Boolean) = preferences.setPatientTwoColumn(value)

    fun setPatientCompactDensity(value: Boolean) = preferences.setPatientCompactDensity(value)

    fun setSaveAndNext(value: Boolean) = preferences.setSaveAndNext(value)

    fun setReportWardName(value: String) = preferences.setReportWardName(value)

    fun setGuidedRollover(value: Boolean) = preferences.setGuidedRollover(value)

    fun setSupervisorGroupChatId(expected: Doctor, rawValue: String) = projectOperations.setSupervisorGroupChatId(expected, rawValue)

    fun setBiometric(value: Boolean) = preferences.setBiometric(value)

    fun setAutoLockMinutes(value: Int) = preferences.setAutoLockMinutes(value)

    fun bringLatest() = dataOperations.bringLatest()

    fun undoLastDownload() = dataOperations.undoLastDownload()

    fun uploadCurrent() = dataOperations.uploadCurrent()

    fun cancelPublicationReview() = dataOperations.cancelPublicationReview()

    fun confirmPublication() = dataOperations.confirmPublication()

    fun exportCsv() = dataOperations.exportCsv()

    fun prepareEncryptedBackup(passphrase: String) = backupOperations.prepareEncryptedBackup(passphrase)

    fun backupPickerLaunched() = backupOperations.backupPickerLaunched()

    fun backupPickerFailed() = backupOperations.backupPickerFailed()

    fun exportEncryptedBackup(uri: Uri?) = backupOperations.exportEncryptedBackup(uri)

    fun prepareProjectProvisioning(passphrase: String, share: Boolean = false, saveToDownloads: Boolean = false) = projectOperations.prepareProjectProvisioning(passphrase, share, saveToDownloads)

    fun provisioningPickerLaunched() = projectOperations.provisioningPickerLaunched()

    fun provisioningShareHandled(failed: Boolean) = projectOperations.provisioningShareHandled(failed)

    fun provisioningPickerFailed() = projectOperations.provisioningPickerFailed()

    fun exportProjectProvisioning(uri: Uri?) = projectOperations.exportProjectProvisioning(uri)

    fun restoreEncryptedBackup(uri: Uri, password: String) = backupOperations.restoreEncryptedBackup(uri, password)

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun retryLastAction() {
        when (_state.value.retryAction) {
            SettingsRetryAction.FETCH -> bringLatest()
            SettingsRetryAction.UPLOAD -> uploadCurrent()
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
