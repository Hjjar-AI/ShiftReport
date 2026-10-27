package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.pdf.PdfPresentationPreset
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.ui.theme.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPreferencesTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val state = MutableStateFlow(SettingsUiState())
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private fun preferences(scope: CoroutineScope) = SettingsPreferences(text, state, scope, settings)
    @Test fun uiPreferenceFailureRestoresPriorSelectionAndShowsError() = runTest {
        coEvery { settings.put(AppConstants.SETTING_UI_PREFERENCES, any()) } throws IllegalStateException("synthetic disk full")
        preferences(this).setUiPreferences(UiPreferences(reducedMotion = true)); runCurrent()
        assertEquals(UiPreferences(), state.value.uiPreferences)
        assertNotNull(state.value.snackbar)
    }
    @Test fun uiPreferenceWritesAreSerializedAndKeepLatestChoice() = runTest {
        val first = UiPreferences(reducedMotion = true)
        val second = first.copy(reducedMotion = false)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        coEvery { settings.put(AppConstants.SETTING_UI_PREFERENCES, first.encode()) } coAnswers {
            entered.complete(Unit); release.await()
        }
        val preferences = preferences(this)
        preferences.setUiPreferences(first); runCurrent()
        assertTrue(entered.isCompleted)
        preferences.setUiPreferences(second); runCurrent()
        assertEquals(second, state.value.uiPreferences)
        coVerify(exactly = 0) { settings.put(AppConstants.SETTING_UI_PREFERENCES, second.encode()) }
        release.complete(Unit); runCurrent()
        coVerifyOrder {
            settings.put(AppConstants.SETTING_UI_PREFERENCES, first.encode())
            settings.put(AppConstants.SETTING_UI_PREFERENCES, second.encode())
        }
        assertEquals(second, state.value.uiPreferences)
    }
    @Test fun olderFailedWriteCannotRollBackNewerChoice() = runTest {
        val first = UiPreferences(reducedMotion = true)
        val second = UiPreferences(reducedMotion = false)
        val release = CompletableDeferred<Unit>()
        coEvery { settings.put(AppConstants.SETTING_UI_PREFERENCES, first.encode()) } coAnswers {
            release.await(); throw IllegalStateException("synthetic old write failure")
        }
        val preferences = preferences(this)
        preferences.setUiPreferences(first); runCurrent()
        preferences.setUiPreferences(second); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals(second, state.value.uiPreferences)
        coVerify(exactly = 1) { settings.put(AppConstants.SETTING_UI_PREFERENCES, second.encode()) }
    }
    @Test fun cancellationOfPreferenceWriteDoesNotShowStorageError() = runTest {
        coEvery { settings.put(AppConstants.SETTING_UI_PREFERENCES, any()) } throws CancellationException("synthetic cancel")
        val selected = UiPreferences(reducedMotion = true)
        preferences(this).setUiPreferences(selected); runCurrent()
        assertNull(state.value.snackbar)
    }
    @Test fun paperChoicePersistsWithoutChangingAppearanceOrShading() = runTest {
        val before = state.value
        preferences(this).setPdfPaperSize(com.hos.rushdpatients.pdf.PdfPaperSize.A4); runCurrent()
        coVerify(exactly = 1) { settings.put(AppConstants.SETTING_PDF_PAPER_SIZE, "A4") }
        assertEquals(com.hos.rushdpatients.pdf.PdfPaperSize.A4, state.value.pdfPaperSize)
        assertEquals(before.appearance, state.value.appearance)
        assertEquals(before.pdfRowShading, state.value.pdfRowShading)
    }
    @Test fun shadingChoicePreservesPaperAndAppAppearance() = runTest {
        val before = state.value
        val shading = com.hos.rushdpatients.pdf.PdfRowShading.entries.first { it != before.pdfRowShading }
        preferences(this).setPdfRowShading(shading); runCurrent()
        coVerify(exactly = 1) { settings.put(AppConstants.SETTING_PDF_ROW_SHADING, shading.name) }
        assertEquals(shading, state.value.pdfRowShading)
        assertEquals(before.pdfPaperSize, state.value.pdfPaperSize)
        assertEquals(before.appearance, state.value.appearance)
    }
    @Test fun appearanceAndAccentPersistIndependently() = runTest {
        val preferences = preferences(this)
        preferences.setAppearance(AppAppearance.AMOLED); preferences.setAppTheme(AppThemePreset.SAGE); runCurrent()
        assertEquals(AppAppearance.AMOLED, state.value.appearance)
        assertEquals(AppThemePreset.SAGE, state.value.appTheme)
        coVerify { settings.put(AppConstants.SETTING_APP_APPEARANCE, "AMOLED") }
        coVerify { settings.put(AppConstants.SETTING_APP_THEME, "SAGE") }
    }
    @Test fun wardNameIsTrimmedConsistentlyInStorageAndScreenState() = runTest {
        preferences(this).setReportWardName("  جناح تجريبي  "); runCurrent()
        coVerify(exactly = 1) { settings.put(AppConstants.SETTING_REPORT_WARD_NAME, "جناح تجريبي") }
        assertEquals("جناح تجريبي", state.value.reportWardName)
        assertNotNull(state.value.snackbar)
    }
    @Test fun biometricAndAutoLockSettingsDoNotAlterClinicalOrAppearanceChoices() = runTest {
        val before = state.value
        val preferences = preferences(this)
        preferences.setBiometric(true); preferences.setAutoLockMinutes(2); runCurrent()
        assertTrue(state.value.biometricEnabled)
        assertEquals(2, state.value.autoLockMinutes)
        assertEquals(before.uiPreferences, state.value.uiPreferences)
        assertEquals(before.appearance, state.value.appearance)
        coVerify { settings.putBoolean(AppConstants.SETTING_BIOMETRIC_ENABLED, true) }
        coVerify { settings.setAutoLockMinutes(2) }
    }
    @Test fun failedAppearanceThemeAndFontWritesKeepPriorSelectionAndCanRetry() = runTest {
        val preferences = preferences(this)
        val before = state.value
        val font = AppFontScale.entries.first { it != before.fontScale }
        coEvery { settings.put(any(), any()) } throws java.io.IOException("synthetic full disk")
        preferences.setAppearance(AppAppearance.AMOLED)
        preferences.setAppTheme(AppThemePreset.SAGE)
        preferences.setFontScale(font)
        advanceUntilIdle()
        assertEquals(before.appearance, state.value.appearance)
        assertEquals(before.appTheme, state.value.appTheme)
        assertEquals(before.fontScale, state.value.fontScale)
        assertNotNull(state.value.snackbar)
        coEvery { settings.put(any(), any()) } returns Unit
        preferences.setAppearance(AppAppearance.AMOLED)
        preferences.setAppTheme(AppThemePreset.SAGE)
        preferences.setFontScale(font)
        advanceUntilIdle()
        assertEquals(AppAppearance.AMOLED, state.value.appearance)
        assertEquals(AppThemePreset.SAGE, state.value.appTheme)
        assertEquals(font, state.value.fontScale)
    }
    @Test fun rapidAppearanceThemeAndFontWritesRemainOrderedBehindSuspendedWrite() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { settings.put(AppConstants.SETTING_APP_APPEARANCE, "AMOLED") } coAnswers { release.await() }
        val preferences = preferences(this)
        val font = AppFontScale.entries.first { it != state.value.fontScale }
        preferences.setAppearance(AppAppearance.AMOLED); runCurrent()
        preferences.setAppTheme(AppThemePreset.SAGE)
        preferences.setFontScale(font); runCurrent()
        coVerify(exactly = 0) { settings.put(AppConstants.SETTING_APP_THEME, any()) }
        coVerify(exactly = 0) { settings.put(AppConstants.SETTING_FONT_SCALE, any()) }
        release.complete(Unit); advanceUntilIdle()
        coVerifyOrder {
            settings.put(AppConstants.SETTING_APP_APPEARANCE, "AMOLED")
            settings.put(AppConstants.SETTING_APP_THEME, "SAGE")
            settings.put(AppConstants.SETTING_FONT_SCALE, font.name)
        }
        assertEquals(font, state.value.fontScale)
    }

}
