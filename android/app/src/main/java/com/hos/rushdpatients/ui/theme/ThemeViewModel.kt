package com.hos.rushdpatients.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.DisplayPreferencesStore
import com.hos.rushdpatients.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Local cosmetics can load before any encrypted repository is constructed. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val displayPreferences: DisplayPreferencesStore
) : ViewModel() {
    val state = displayPreferences.state

    init { retry() }

    fun retry() { viewModelScope.launch { displayPreferences.load() } }

    /** Called only from StorageAccessGate's successful content, once for current saved choices. */
    fun initializeFrom(settings: SettingsRepository) {
        if (!state.value.needsInitialization) return
        viewModelScope.launch {
            displayPreferences.initialize {
                ThemePreferences(
                    preset = AppThemePreset.fromSetting(settings.get(AppConstants.SETTING_APP_THEME)),
                    appearance = AppAppearance.fromSetting(settings.get(AppConstants.SETTING_APP_APPEARANCE)),
                    fontScale = AppFontScale.fromSetting(settings.get(AppConstants.SETTING_FONT_SCALE)),
                    uiPreferences = UiPreferences.decode(settings.get(AppConstants.SETTING_UI_PREFERENCES))
                )
            }
        }
    }
}
