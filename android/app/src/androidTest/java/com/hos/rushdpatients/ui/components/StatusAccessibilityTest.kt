package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Semantics contract only; actual TalkBack interaction is an owner-skipped check. */
@RunWith(AndroidJUnit4::class)
class StatusAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun errorsAnnounceAssertivelyLoadingPolitelyAndRetryIsLabelled() {
        var retries = 0
        compose.setContent { MaterialTheme { Column {
            AppNotice("synthetic error", kind = NoticeKind.ERROR, actionLabel = "Retry synthetic", onAction = { retries++ })
            AppLoadingState("synthetic loading")
        } } }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        compose.onNodeWithText("Retry synthetic").assertHasClickAction().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
