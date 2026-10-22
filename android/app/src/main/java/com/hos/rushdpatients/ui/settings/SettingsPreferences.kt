package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfRuleWeight
import com.hos.rushdpatients.pdf.PdfRuleContrast
import com.hos.rushdpatients.pdf.PdfRowShading
import com.hos.rushdpatients.pdf.PdfPresentationPreset
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares the screen state and ViewModel scope; does not create a second lifecycle. */
internal class SettingsPreferences(
    private val uiText: UiStrings,
    private val _state: MutableStateFlow<SettingsUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val settingsRepository: SettingsRepository
) {

    private val uiPreferencesWrites = Mutex()

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

    fun setPdfRuleWeight(value: PdfRuleWeight) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_PDF_RULE_WEIGHT, value.name)
            _state.update { it.copy(pdfRuleWeight = value) }
        }
    }

    fun setPdfRuleContrast(value: PdfRuleContrast) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_PDF_RULE_CONTRAST, value.name)
            _state.update { it.copy(pdfRuleContrast = value) }
        }
    }

    fun setPdfRowShading(value: PdfRowShading) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_PDF_ROW_SHADING, value.name)
            _state.update { it.copy(pdfRowShading = value) }
        }
    }

    fun setPdfPresentationPreset(value: PdfPresentationPreset) {
        viewModelScope.launch {
            val settings = mutableMapOf(
                AppConstants.SETTING_PDF_RULE_WEIGHT to value.weight.name,
                AppConstants.SETTING_PDF_RULE_CONTRAST to value.contrast.name,
                AppConstants.SETTING_PDF_ROW_SHADING to value.shading.name
            )
            if (value.lightOnly) settings[AppConstants.SETTING_PDF_DARK_MODE] = "false"
            settingsRepository.putAll(settings)
            _state.update { it.copy(pdfRuleWeight = value.weight, pdfRuleContrast = value.contrast,
                pdfRowShading = value.shading, pdfDarkMode = if (value.lightOnly) false else it.pdfDarkMode) }
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

    fun setUiPreferences(value: com.hos.rushdpatients.ui.theme.UiPreferences) {
        val previous = _state.value.uiPreferences
        _state.update { it.copy(uiPreferences = value) }
        viewModelScope.launch {
            try {
                uiPreferencesWrites.withLock {
                    settingsRepository.put(AppConstants.SETTING_UI_PREFERENCES, value.encode())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { current -> current.copy(
                    uiPreferences = if (current.uiPreferences == value) previous else current.uiPreferences,
                    snackbar = uiText(R.string.ui_interface_save_failed)) }
            }
        }
    }

    fun setAppearance(value: AppAppearance) {
        _state.update { it.copy(appearance = value) }
        viewModelScope.launch { settingsRepository.put(AppConstants.SETTING_APP_APPEARANCE, value.name) }
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

    fun setSaveAndNext(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_SAVE_AND_NEXT, value)
            _state.update { it.copy(saveAndNext = value) }
        }
    }

    fun setReportWardName(value: String) {
        viewModelScope.launch {
            settingsRepository.put(AppConstants.SETTING_REPORT_WARD_NAME, value.trim())
            _state.update { it.copy(reportWardName = value.trim(), snackbar = uiText(R.string.ui_settings_preferences_001)) }
        }
    }

    fun setGuidedRollover(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_GUIDED_ROLLOVER, value)
            _state.update { it.copy(guidedRollover = value) }
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
}
