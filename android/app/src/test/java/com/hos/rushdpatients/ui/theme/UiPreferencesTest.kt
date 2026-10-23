package com.hos.rushdpatients.ui.theme

import org.junit.Assert.*
import org.junit.Test

class UiPreferencesTest {
    @Test fun absentPreferencesUseCurrentSafeDefaults() {
        val defaults = UiPreferences.decode(null)
        assertEquals("current", defaults.profile)
        assertEquals(UiDensity.NORMAL, defaults.density)
        assertFalse(defaults.autoHideNavigation)
        assertFalse(defaults.reducedMotion)
        assertTrue(defaults.compactAddAction)
    }
    @Test fun malformedPreferencesFallBackWithoutThrowing() {
        for (raw in listOf("", "null", "[]", "{", "false", "{\"density\":42}"))
            assertEquals(raw, UiPreferences(), UiPreferences.decode(raw))
    }
    @Test fun unsupportedEnumValueFallsBackToCompleteDefaults() {
        assertEquals(UiPreferences(), UiPreferences.decode("{\"density\":\"UNSUPPORTED\",\"reducedMotion\":true}"))
    }
    @Test fun unknownFieldsAreIgnoredWithoutLosingSupportedValues() {
        assertEquals(UiPreferences(reducedMotion = true), UiPreferences.decode("{\"futureOption\":17,\"reducedMotion\":true}"))
    }
    @Test fun olderPartialPreferenceObjectsKeepDefaultsForMissingFields() {
        assertEquals(UiPreferences(density = UiDensity.COMPACT), UiPreferences.decode("{\"density\":\"COMPACT\"}"))
    }
    @Test fun allDensityControlAndBehaviorCombinationsRoundTrip() {
        for (density in UiDensity.entries) for (size in UiControlSize.entries) for (mask in 0..15) {
            val preferences = UiPreferences(profile = "quiet", density = density, controlSize = size,
                reducedMotion = mask and 1 != 0, autoHideNavigation = mask and 2 != 0,
                compactAddAction = mask and 4 != 0, expandPatientHeader = mask and 8 != 0)
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
            assertEquals(AppAppearance.LIGHT, AppAppearance.fromSetting(value))
            assertEquals(AppThemePreset.VANILLA, AppThemePreset.fromSetting(value))
            assertEquals(AppFontScale.NORMAL, AppFontScale.fromSetting(value))
        }
    }
    @Test fun fontSizesArePositiveAndStrictlyIncreasing() {
        assertTrue(AppFontScale.entries.all { it.multiplier > 0f })
        assertTrue(AppFontScale.entries.zipWithNext().all { (a, b) -> a.multiplier < b.multiplier })
    }
}
