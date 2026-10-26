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
    @Test fun quickTapSkipsIntro() {
        var skips = 0
        compose.setContent { MaterialTheme { SplashIntroScreen { skips++ } } }
        compose.onNode(hasClickAction()).performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, skips) }
    }
    @Test fun holdingDoesNotExposeRetiredPauseControls() {
        var skips = 0
        compose.setContent { MaterialTheme { SplashIntroScreen { skips++ } } }
        compose.onNode(hasClickAction()).performTouchInput { down(center); advanceEventTime(700); up() }
        compose.runOnIdle { assertEquals(1, skips) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).assertCountEquals(0)
    }
    @Test fun accessibilityClickSkipsWithoutHoldActions() {
        var skips = 0
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme { SplashIntroScreen { skips++ } }
        } }
        compose.onNode(hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1, skips) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).assertCountEquals(0)
    }
}
