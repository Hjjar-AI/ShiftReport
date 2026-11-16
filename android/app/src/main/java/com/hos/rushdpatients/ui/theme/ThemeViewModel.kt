package com.hos.rushdpatients.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** No display defaults are exposed until every saved display preference has been read. */
data class ThemePreferences(
    val preset: AppThemePreset,
    val appearance: AppAppearance,
    val fontScale: AppFontScale,
    val uiPreferences: UiPreferences
)

data class ThemeUiState(val preferences: ThemePreferences? = null, val loadFailed: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val reload = MutableStateFlow(0)

    val state: StateFlow<ThemeUiState> = reload.flatMapLatest {
        combine(
            settingsRepository.observe(AppConstants.SETTING_APP_THEME),
            settingsRepository.observe(AppConstants.SETTING_APP_APPEARANCE),
            settingsRepository.observe(AppConstants.SETTING_FONT_SCALE),
            settingsRepository.observe(AppConstants.SETTING_UI_PREFERENCES)
        ) { preset, appearance, fontScale, uiPreferences ->
            ThemeUiState(preferences = ThemePreferences(
                AppThemePreset.fromSetting(preset), AppAppearance.fromSetting(appearance),
                AppFontScale.fromSetting(fontScale), UiPreferences.decode(uiPreferences)
            ))
        }.onStart { emit(ThemeUiState()) }
            .catch { emit(ThemeUiState(loadFailed = true)) }
        // Retain the loaded bundle during background/resume; do not re-enter loading on resubscription.
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeUiState())

    fun retry() { reload.update { it + 1 } }
}
