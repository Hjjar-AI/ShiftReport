package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppThemePreset

enum class SettingsRetryAction { FETCH, UPLOAD, FORCE_UPLOAD, EXPORT }

data class SettingsUiState(
    val doctorName: String = "",
    val role: String = "",
    val autoSync: Boolean = false,
    val syncWifiOnly: Boolean = false,
    val reportAsPdf: Boolean = false,
    val pdfStyle: PdfStyle = PdfStyle.CLASSIC,
    val pdfPatientCardStyle: PatientCardStyle = PatientCardStyle.BADGE_HEADER,
    val pdfOrientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val pdfPaperSize: PdfPaperSize = PdfPaperSize.A4,
    val pdfColorPreset: PdfColorPreset = PdfColorPreset.TEAL,
    val pdfDarkMode: Boolean = false,
    val pdfSeparateBySupervisor: Boolean = false,
    val appTheme: AppThemePreset = AppThemePreset.SYSTEM,
    val fontScale: AppFontScale = AppFontScale.NORMAL,
    val patientDetailsExpanded: Boolean = false,
    val patientTwoColumn: Boolean = false,
    val patientCompactDensity: Boolean = false,
    val guidedRollover: Boolean = false,
    val supervisors: List<Doctor> = emptyList(),
    val savingSupervisorGroupId: String? = null,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val lastCsvSync: String? = null,
    val lastDoctorsSync: String? = null,
    val syncing: Boolean = false,
    val exportingCsv: Boolean = false,
    val backupBusy: Boolean = false,
    val provisioningBusy: Boolean = false,
    val provisioningReady: Boolean = false,
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
