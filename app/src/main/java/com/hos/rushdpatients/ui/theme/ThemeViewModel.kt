package com.hos.rushdpatients.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {

    val preset: StateFlow<AppThemePreset> = settingsRepository
        .observe(AppConstants.SETTING_APP_THEME)
        .map(AppThemePreset::fromSetting)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemePreset.SYSTEM)

    val fontScale: StateFlow<AppFontScale> = settingsRepository
        .observe(AppConstants.SETTING_FONT_SCALE)
        .map(AppFontScale::fromSetting)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppFontScale.NORMAL)
}