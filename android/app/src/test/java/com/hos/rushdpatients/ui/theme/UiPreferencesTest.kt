package com.hos.rushdpatients.ui.theme

import org.junit.Assert.*
import org.junit.Test

class UiPreferencesTest {
    @Test fun absentPreferencesUseCurrentSafeDefaults() {
        val defaults = UiPreferences.decode(null)
        assertEquals(UiDensity.NORMAL, defaults.density)
        assertFalse(defaults.reducedMotion)
        assertEquals(UiControlSize.COMFORTABLE, defaults.controlSize)
    }
    @Test fun malformedPreferencesFallBackWithoutThrowing() {
        for (raw in listOf("", "null", "[]", "{", "false", "{\"density\":42}"))
            assertEquals(raw, UiPreferences(), UiPreferences.decode(raw))
    }
    @Test fun unsupportedEnumValueFallsBackToCompleteDefaults() {
        assertEquals(UiPreferences(), UiPreferences.decode("{\"density\":\"UNSUPPORTED\",\"reducedMotion\":true}"))
    }
    @Test fun unknownFieldsRejectThePreferenceObject() {
        assertEquals(UiPreferences(), UiPreferences.decode("{\"futureOption\":17,\"reducedMotion\":true}"))
    }
    @Test fun partialCurrentPreferenceObjectsKeepDefaultsForMissingFields() {
        assertEquals(UiPreferences(density = UiDensity.COMPACT), UiPreferences.decode("{\"density\":\"COMPACT\"}"))
    }
    @Test fun allDensityControlAndBehaviorCombinationsRoundTrip() {
        for (density in UiDensity.entries) for (size in UiControlSize.entries) for (motion in listOf(false, true)) {
            val preferences = UiPreferences(density = density, controlSize = size, reducedMotion = motion)
            assertEquals(preferences, UiPreferences.decode(preferences.encode()))
        }
    }
    @Test fun knownAppearanceAccentAndFontIdsRestoreIndependently() {
        for (value in AppAppearance.entries) assertEquals(value, AppAppearance.fromSetting(value.name))
        for (value in AppThemePreset.entries) assertEquals(value, AppThemePreset.fromSetting(value.name))
        for (value in AppFontScale.entries) assertEquals(value, AppFontScale.fromSetting(value.name))
    }
    @Test fun unsupportedAppearanceAccentAndFontValuesUseDocumentedDefaults() {
        for (value in listOf(null, "", "unsupported")) {
            assertEquals(AppAppearance.SYSTEM, AppAppearance.fromSetting(value))
            assertEquals(AppThemePreset.VANILLA, AppThemePreset.fromSetting(value))
            assertEquals(AppFontScale.NORMAL, AppFontScale.fromSetting(value))
        }
    }
    @Test fun fontSizesArePositiveAndStrictlyIncreasing() {
        assertTrue(AppFontScale.entries.all { it.multiplier > 0f })
        assertTrue(AppFontScale.entries.zipWithNext().all { (a, b) -> a.multiplier < b.multiplier })
    }
}
