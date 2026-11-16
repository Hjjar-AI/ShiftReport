package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.DisplayPreferencesStore
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.ui.theme.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPreferencesTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val state = MutableStateFlow(SettingsUiState())
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val display = mockk<DisplayPreferencesStore>()
    private var committed = ThemePreferences()
    private val attempted = mutableListOf<ThemePreferences>()
    private var beforeCommit: suspend (ThemePreferences) -> Unit = {}

    @Before fun localCosmeticWrites() {
        coEvery { display.update(any()) } coAnswers {
            val selected = firstArg<(ThemePreferences) -> ThemePreferences>()(committed)
            attempted += selected
            beforeCommit(selected)
            committed = selected
        }
    }
    private fun preferences(scope: CoroutineScope) = SettingsPreferences(text, state, scope, settings, display)

    @Test fun uiPreferenceFailureRestoresPriorSelectionAndShowsError() = runTest {
        beforeCommit = { throw IllegalStateException("synthetic disk full") }
        preferences(this).setUiPreferences(UiPreferences(reducedMotion = true)); runCurrent()
        assertEquals(UiPreferences(), state.value.uiPreferences)
        assertEquals(ThemePreferences(), committed)
        assertNotNull(state.value.snackbar)
    }
    @Test fun uiPreferenceWritesAreSerializedAndKeepLatestChoice() = runTest {
        val first = UiPreferences(reducedMotion = true)
        val second = UiPreferences(density = UiDensity.RELAXED)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        beforeCommit = { if (it.uiPreferences == first) { entered.complete(Unit); release.await() } }
        val preferences = preferences(this)
        preferences.setUiPreferences(first); runCurrent()
        assertTrue(entered.isCompleted)
        preferences.setUiPreferences(second); runCurrent()
        assertEquals(UiPreferences(), state.value.uiPreferences)
        assertEquals(listOf(first), attempted.map { it.uiPreferences })
        release.complete(Unit); runCurrent()
        assertEquals(listOf(first, second), attempted.map { it.uiPreferences })
        assertEquals(second, state.value.uiPreferences)
        assertEquals(second, committed.uiPreferences)
    }
    @Test fun olderFailedWriteCannotRollBackNewerChoice() = runTest {
        val first = UiPreferences(reducedMotion = true)
        val second = UiPreferences(density = UiDensity.RELAXED)
        val release = CompletableDeferred<Unit>()
        beforeCommit = { if (it.uiPreferences == first) {
            release.await(); throw IllegalStateException("synthetic old write failure")
        } }
        val preferences = preferences(this)
        preferences.setUiPreferences(first); runCurrent()
        preferences.setUiPreferences(second); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals(second, state.value.uiPreferences)
        assertEquals(second, committed.uiPreferences)
        assertEquals(listOf(first, second), attempted.map { it.uiPreferences })
    }
    @Test fun cancellationOfPreferenceWriteDoesNotShowStorageError() = runTest {
        beforeCommit = { throw CancellationException("synthetic cancel") }
        preferences(this).setUiPreferences(UiPreferences(reducedMotion = true)); runCurrent()
        assertNull(state.value.snackbar)
        assertEquals(ThemePreferences(), committed)
        assertEquals(UiPreferences(), state.value.uiPreferences)
    }
    @Test fun paperChoicePersistsWithoutChangingAppearanceOrShading() = runTest {
        val before = state.value
        preferences(this).setPdfPaperSize(com.hos.rushdpatients.pdf.PdfPaperSize.A4); runCurrent()
        coVerify(exactly = 1) { settings.put(AppConstants.SETTING_PDF_PAPER_SIZE, "A4") }
        assertEquals(com.hos.rushdpatients.pdf.PdfPaperSize.A4, state.value.pdfPaperSize)
        assertEquals(before.appearance, state.value.appearance)
        assertEquals(before.pdfRowShading, state.value.pdfRowShading)
        coVerify(exactly = 0) { display.update(any()) }
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
        preferences.setAppearance(AppAppearance.AMOLED); preferences.setAppTheme(AppThemePreset.COASTAL); runCurrent()
        assertEquals(AppAppearance.AMOLED, state.value.appearance)
        assertEquals(AppThemePreset.COASTAL, state.value.appTheme)
        assertEquals(AppAppearance.AMOLED, committed.appearance)
        assertEquals(AppThemePreset.COASTAL, committed.preset)
        assertEquals(AppAppearance.AMOLED, attempted.last().appearance)
        coVerify(exactly = 0) { settings.put(any(), any()) }
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
        coVerify(exactly = 0) { display.update(any()) }
    }
    @Test fun failedAppearanceThemeAndFontWritesKeepPriorSelectionAndCanRetry() = runTest {
        val preferences = preferences(this)
        val before = state.value
        val font = AppFontScale.EXTRA_LARGE
        beforeCommit = { throw java.io.IOException("synthetic full disk") }
        preferences.setAppearance(AppAppearance.AMOLED)
        preferences.setAppTheme(AppThemePreset.COASTAL)
        preferences.setFontScale(font)
        advanceUntilIdle()
        assertEquals(before.appearance, state.value.appearance)
        assertEquals(before.appTheme, state.value.appTheme)
        assertEquals(before.fontScale, state.value.fontScale)
        assertEquals(ThemePreferences(), committed)
        assertNotNull(state.value.snackbar)
        beforeCommit = {}
        preferences.setAppearance(AppAppearance.AMOLED)
        preferences.setAppTheme(AppThemePreset.COASTAL)
        preferences.setFontScale(font)
        advanceUntilIdle()
        assertEquals(AppAppearance.AMOLED, state.value.appearance)
        assertEquals(AppThemePreset.COASTAL, state.value.appTheme)
        assertEquals(font, state.value.fontScale)
        assertEquals(ThemePreferences(AppThemePreset.COASTAL, AppAppearance.AMOLED, font), committed)
    }
    @Test fun rapidAppearanceThemeAndFontWritesRemainOrderedBehindSuspendedWrite() = runTest {
        val release = CompletableDeferred<Unit>()
        beforeCommit = { if (attempted.size == 1) release.await() }
        val preferences = preferences(this)
        val font = AppFontScale.EXTRA_LARGE
        preferences.setAppearance(AppAppearance.AMOLED); runCurrent()
        preferences.setAppTheme(AppThemePreset.COASTAL)
        preferences.setFontScale(font); runCurrent()
        assertEquals(1, attempted.size)
        assertEquals(AppAppearance.SYSTEM, state.value.appearance)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(
            ThemePreferences(appearance = AppAppearance.AMOLED),
            ThemePreferences(preset = AppThemePreset.COASTAL, appearance = AppAppearance.AMOLED),
            ThemePreferences(preset = AppThemePreset.COASTAL, appearance = AppAppearance.AMOLED, fontScale = font)
        ), attempted)
        assertEquals(font, state.value.fontScale)
    }
    @Test fun `two failed queued choices preserve the last committed preference`() = runTest {
        val first = UiPreferences(reducedMotion = true)
        val second = UiPreferences(density = UiDensity.RELAXED)
        val release = CompletableDeferred<Unit>()
        beforeCommit = { release.await(); throw IllegalStateException("synthetic storage failure") }
        val preferences = preferences(this)
        preferences.setUiPreferences(first); runCurrent()
        preferences.setUiPreferences(second); runCurrent()
        assertEquals(UiPreferences(), state.value.uiPreferences)
        release.complete(Unit); runCurrent()
        assertEquals(UiPreferences(), state.value.uiPreferences)
        assertEquals(ThemePreferences(), committed)
        assertEquals(listOf(first, second), attempted.map { it.uiPreferences })
        assertNotNull(state.value.snackbar)
    }
    @Test fun allCosmeticChoicesUseLocalStorageWithoutEncryptedSettingsWrites() = runTest {
        val preferences = preferences(this)
        preferences.setAppearance(AppAppearance.DARK)
        preferences.setAppTheme(AppThemePreset.COASTAL)
        preferences.setFontScale(AppFontScale.LARGE)
        preferences.setUiPreferences(UiPreferences(reducedMotion = true))
        advanceUntilIdle()
        assertEquals(ThemePreferences(AppThemePreset.COASTAL, AppAppearance.DARK, AppFontScale.LARGE,
            UiPreferences(reducedMotion = true)), committed)
        coVerify(exactly = 4) { display.update(any()) }
        coVerify(exactly = 0) { settings.put(any(), any()) }
    }
}
