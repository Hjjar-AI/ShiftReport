package com.hos.rushdpatients.ui.theme

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.DisplayPreferencesStore
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.testutil.ViewModelFixture
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest : ViewModelFixture() {
    private val local = MutableStateFlow(ThemeUiState())
    private val display = mockk<DisplayPreferencesStore> {
        every { state } returns local
        coEvery { load() } returns Unit
    }
    private val protectedSettings = mockk<SettingsRepository>()

    @Test fun readyLocalChoicesDoNotReadProtectedSettingsOrInitializeAgain() = runTest {
        val saved = ThemePreferences(AppThemePreset.COASTAL, AppAppearance.AMOLED, AppFontScale.LARGE,
            UiPreferences(reducedMotion = true))
        local.value = ThemeUiState(preferences = saved)
        val model = keep(ThemeViewModel(display))
        model.initializeFrom(protectedSettings)
        runCurrent()
        assertEquals(ThemeUiState(preferences = saved), model.state.value)
        coVerify(exactly = 1) { display.load() }
        coVerify(exactly = 0) { display.initialize(any()) }
        verify { protectedSettings wasNot Called }
    }

    @Test fun firstCopyReadsExactlyTheFourExistingCosmeticValues() = runTest {
        local.value = ThemeUiState(needsInitialization = true)
        coEvery { protectedSettings.get(AppConstants.SETTING_APP_THEME) } returns "COASTAL"
        coEvery { protectedSettings.get(AppConstants.SETTING_APP_APPEARANCE) } returns "AMOLED"
        coEvery { protectedSettings.get(AppConstants.SETTING_FONT_SCALE) } returns "EXTRA_LARGE"
        val ui = UiPreferences(UiDensity.RELAXED, UiControlSize.STANDARD, true)
        coEvery { protectedSettings.get(AppConstants.SETTING_UI_PREFERENCES) } returns ui.encode()
        coEvery { display.initialize(any()) } coAnswers {
            local.value = ThemeUiState(preferences = firstArg<suspend () -> ThemePreferences>()())
        }
        val model = keep(ThemeViewModel(display))
        model.initializeFrom(protectedSettings); runCurrent()
        assertEquals(ThemeUiState(preferences = ThemePreferences(
            AppThemePreset.COASTAL, AppAppearance.AMOLED, AppFontScale.EXTRA_LARGE, ui)), model.state.value)
        coVerifySequence {
            protectedSettings.get(AppConstants.SETTING_APP_THEME)
            protectedSettings.get(AppConstants.SETTING_APP_APPEARANCE)
            protectedSettings.get(AppConstants.SETTING_FONT_SCALE)
            protectedSettings.get(AppConstants.SETTING_UI_PREFERENCES)
        }
        model.initializeFrom(protectedSettings); runCurrent()
        coVerify(exactly = 1) { display.initialize(any()) }
    }

    @Test fun pendingLocalReadNeverPublishesDefaultPreferences() = runTest {
        val release = CompletableDeferred<Unit>()
        val saved = ThemePreferences(appearance = AppAppearance.DARK, fontScale = AppFontScale.EXTRA_LARGE)
        coEvery { display.load() } coAnswers {
            release.await()
            local.value = ThemeUiState(preferences = saved)
        }
        val model = keep(ThemeViewModel(display))
        try {
            runCurrent()
            assertNull(model.state.value.preferences)
            assertFalse(model.state.value.needsInitialization)
            model.initializeFrom(protectedSettings)
            coVerify(exactly = 0) { display.initialize(any()) }
            release.complete(Unit); runCurrent()
            assertEquals(saved, model.state.value.preferences)
            verify { protectedSettings wasNot Called }
        } finally { release.complete(Unit) }
    }

    @Test fun retryCanLoadSavedChoicesAfterLocalReadFailure() = runTest {
        var attempts = 0
        val saved = ThemePreferences(appearance = AppAppearance.AMOLED)
        coEvery { display.load() } coAnswers {
            attempts++
            local.value = if (attempts == 1) ThemeUiState(loadFailed = true)
                else ThemeUiState(preferences = saved)
        }
        val model = keep(ThemeViewModel(display)); runCurrent()
        assertEquals(ThemeUiState(loadFailed = true), model.state.value)
        model.retry(); runCurrent()
        assertEquals(ThemeUiState(preferences = saved), model.state.value)
        coVerify(exactly = 2) { display.load() }
        verify { protectedSettings wasNot Called }
    }
}
