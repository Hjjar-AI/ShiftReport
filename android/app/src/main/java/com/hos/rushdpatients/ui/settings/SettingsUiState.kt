package com.hos.rushdpatients.ui.settings

import android.net.Uri
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfRowShading
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppThemePreset

enum class SettingsRetryAction { FETCH, UPLOAD, EXPORT }
enum class SettingsSyncRecovery { DOCTORS, PATIENTS }

data class SettingsUiState(
    val publicationHistory: List<com.hos.rushdpatients.sync.PublicationJournalEntry> = emptyList(),
    val publicationHistoryLoading: Boolean = false,
    val publicationHistoryError: String? = null,
    val doctorName: String = "",
    val role: String = "",
    val isAdmin: Boolean = false,
    val isPermanentAdmin: Boolean = false,
    val emptyStartBusy: Boolean = false,
    val emptyStartReview: String? = null,
    val emptyStartResult: String? = null,
    val reportAsPdf: Boolean = true,
    val reportWardName: String = "",
    val pdfStyle: PdfStyle = PdfStyle.CARDS,
    val pdfPatientCardStyle: PatientCardStyle = PatientCardStyle.BADGE_HEADER,
    val pdfPaperSize: PdfPaperSize = PdfPaperSize.A5,
    val pdfColorPreset: PdfColorPreset = PdfColorPreset.TEAL,
    val pdfRowShading: PdfRowShading = PdfRowShading.AUTO,
    val uiPreferences: com.hos.rushdpatients.ui.theme.UiPreferences = com.hos.rushdpatients.ui.theme.UiPreferences(),
    val appearance: AppAppearance = AppAppearance.LIGHT,
    val appTheme: AppThemePreset = AppThemePreset.VANILLA,
    val fontScale: AppFontScale = AppFontScale.NORMAL,
    val patientDetailsExpanded: Boolean = false,
    val patientTwoColumn: Boolean = false,
    val patientCompactDensity: Boolean = false,
    val supervisors: List<Doctor> = emptyList(),
    val savingSupervisorGroupId: String? = null,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val lastCsvSync: String? = null,
    val lastDoctorsSync: String? = null,
    val online: Boolean = false,
    val patientsPending: Boolean? = null,
    val doctorsPending: Boolean? = null,
    val syncIssue: String? = null,
    val syncRecovery: SettingsSyncRecovery? = null,
    val syncRetryAction: SettingsRetryAction? = null,
    val syncing: Boolean = false,
    val canUndoDownload: Boolean = false,
    val publicationReviewText: String? = null,
    val rollbackReviewText: String? = null,
    val olderRemoteConfirmation: Boolean = false,
    val fetchChoiceRequired: Boolean = false,
    val exportingCsv: Boolean = false,
    val backupBusy: Boolean = false,
    val backupReady: Boolean = false,
    val provisioningBusy: Boolean = false,
    val provisioningReady: Boolean = false,
    val provisioningShareUri: Uri? = null,
    val databaseSize: String = "—",
    val totalStoredPatients: Int = 0,
    val deletedPatients: Int = 0,
    val storedShifts: Int = 0,
    val lastEncryptedBackup: String? = null,
    val lastOperation: String? = null,
    val deviceId: String = "",
    val retryAction: SettingsRetryAction? = null,
    val snackbar: String? = null
)
