package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.sort.GroupByMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Uses the owning ViewModel state/scope; repository guards remain unchanged. */
internal class WardPreferences(
    private val _state: MutableStateFlow<WardUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val settingsRepository: SettingsRepository
) {

    fun observePreferences() {
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
            settingsRepository.observe(AppConstants.SETTING_SAVE_AND_NEXT).collect { stored ->
                _state.update { it.copy(saveAndNext = stored?.toBooleanStrictOrNull() ?: false) }
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

    fun togglePatientDetails() = setPatientDetailsExpanded(!_state.value.patientDetailsExpanded)

    fun setPatientDetailsExpanded(expanded: Boolean) {
        _state.update { it.copy(patientDetailsExpanded = expanded) }
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_PATIENT_DETAILS_EXPANDED, expanded)
        }
    }

    fun setSaveAndNext(enabled: Boolean) {
        _state.update { it.copy(saveAndNext = enabled) }
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_SAVE_AND_NEXT, enabled)
        }
    }

    fun setTwoColumn(enabled: Boolean) {
        _state.update { it.copy(twoColumn = enabled) }
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_PATIENT_TWO_COLUMN, enabled)
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

    fun dismissSyncHint() {
        _state.update { it.copy(showSyncHint = false) }
        viewModelScope.launch {
            settingsRepository.putBoolean(AppConstants.SETTING_SYNC_LONG_PRESS_HINT_SHOWN, true)
        }
    }
}
