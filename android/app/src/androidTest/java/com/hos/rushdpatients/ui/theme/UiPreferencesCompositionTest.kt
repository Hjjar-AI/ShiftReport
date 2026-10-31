package com.hos.rushdpatients.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiPreferencesCompositionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun reducedMotionDisablesAllProfileAnimationsWithoutChangingSpacing() {
        var observed: UiStyleProfile? = null
        compose.setContent {
            ProvideUiPreferences(UiPreferences(reducedMotion = true)) {
                val current = LocalUiStyle.current
                SideEffect { observed = current }
            }
        }
        compose.runOnIdle {
            val actual = requireNotNull(observed)
            assertEquals(listOf(0, 0, 0, 0, 0), listOf(actual.expandMillis, actual.collapseMillis,
                actual.highlightMillis, actual.indicatorMillis, actual.placementMillis))
            assertEquals(UiStyleCatalog.current.normalDensity, actual.normalDensity)
            assertEquals(UiStyleCatalog.current.listGap, actual.listGap)
        }
    }
    @Test fun turningReducedMotionOffRestoresSelectedProfileDurations() {
        var preferences by mutableStateOf(UiPreferences(reducedMotion = true))
        var observed: UiStyleProfile? = null
        compose.setContent { ProvideUiPreferences(preferences) {
            val current = LocalUiStyle.current
            SideEffect { observed = current }
        } }
        compose.runOnIdle { preferences = preferences.copy(reducedMotion = false) }
        compose.runOnIdle { assertEquals(UiStyleCatalog.current, observed) }
    }
    @Test fun currentStyleKeepsSuppliedPreferences() {
        var observedStyle: UiStyleProfile? = null
        var observedPreferences: UiPreferences? = null
        val selected = UiPreferences()
        compose.setContent { ProvideUiPreferences(selected) {
            val style = LocalUiStyle.current; val preferences = LocalUiPreferences.current
            SideEffect { observedStyle = style; observedPreferences = preferences }
        } }
        compose.runOnIdle {
            assertEquals(UiStyleCatalog.current, observedStyle)
            assertEquals(selected, observedPreferences)
        }
    }
    @Test fun localControlOverridesKeepBehaviorAndDoNotLeakToSibling() {
        val selected = UiPreferences(reducedMotion = true)
        var inside: UiPreferences? = null; var outside: UiPreferences? = null
        compose.setContent { ProvideUiPreferences(selected) {
            ProvideUiDefaults(density = UiDensity.COMPACT, size = UiControlSize.COMFORTABLE) {
                val current = LocalUiPreferences.current
                SideEffect { inside = current }
            }
            val current = LocalUiPreferences.current
            SideEffect { outside = current }
        } }
        compose.runOnIdle {
            assertEquals(selected.copy(density = UiDensity.COMPACT, controlSize = UiControlSize.COMFORTABLE), inside)
            assertEquals(selected, outside)
        }
    }
    @Test fun layoutThresholdNeverShrinksForSmallerFonts() {
        var scale = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 0.8f)) {
                val current = uiLayoutScale(); SideEffect { scale = current }
            }
        }
        compose.runOnIdle { assertEquals(1f, scale, 0f) }
    }
    @Test fun layoutThresholdGrowsWithLargeFonts() {
        var scale = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.8f)) {
                val current = uiLayoutScale(); SideEffect { scale = current }
            }
        }
        compose.runOnIdle { assertEquals(1.8f, scale, 0f) }
    }
}
