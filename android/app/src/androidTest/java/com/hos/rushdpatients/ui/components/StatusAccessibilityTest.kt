package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ordinary status text/control semantics remain; custom narration and actions are retired. */
@RunWith(AndroidJUnit4::class)
class StatusAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun statusTextRemainsVisibleWithoutCustomAnnouncementsAndRetryIsLabelled() {
        var retries = 0
        compose.setContent { MaterialTheme { Column {
            AppNotice("synthetic error", kind = NoticeKind.ERROR, actionLabel = "Retry synthetic", onAction = { retries++ })
            AppLoadingState("synthetic loading")
        } } }
        compose.onNodeWithText("synthetic error").assertIsDisplayed()
        compose.onNodeWithText("synthetic loading").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true)
            .assertCountEquals(0)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
            .assertCountEquals(0)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        compose.onNodeWithText("Retry synthetic").assertHasClickAction().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
