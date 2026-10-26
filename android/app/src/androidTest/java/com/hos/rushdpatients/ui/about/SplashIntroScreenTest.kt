package com.hos.rushdpatients.ui.about

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplashIntroScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun quickTapSkipsAndReleasesReadingHold() {
        var skips = 0
        val holds = mutableListOf<Boolean>()
        compose.setContent { MaterialTheme { SplashIntroScreen({ skips++ }, 5, false, { holds += it }) } }
        compose.onRoot().performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, skips); assertEquals(listOf(true, false), holds) }
    }
    @Test fun longPressKeepsReadingUntilReleaseWithoutQuickSkip() {
        var skips = 0
        val holds = mutableListOf<Boolean>()
        compose.setContent { MaterialTheme { SplashIntroScreen({ skips++ }, 0, true, { holds += it }) } }
        compose.onRoot().performTouchInput { down(center); advanceEventTime(700); up() }
        compose.runOnIdle { assertEquals(0, skips); assertEquals(listOf(true, false), holds) }
    }
    @Test fun accessibilityActionCanHoldAndReleaseExpiredSplash() {
        var held by mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme { SplashIntroScreen({}, 0, held, { held = it }) }
        } }
        val node = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val hold = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        compose.runOnIdle { assertTrue(hold.action()); assertTrue(held) }
        val release = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        compose.runOnIdle { assertTrue(release.action()); assertFalse(held) }
    }
}
