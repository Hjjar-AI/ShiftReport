package com.hos.rushdpatients.testutil

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.R

/** Empty labs are optional; tests must add a panel through the real editor before typing. */
fun ComposeTestRule.ensureLabGroup() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val results = hasSetTextAction() and hasText(context.getString(R.string.lab_group_results), substring = true)
    if (onAllNodes(results).fetchSemanticsNodes().isEmpty()) {
        onNodeWithText(context.getString(R.string.lab_groups_add)).performScrollTo().performClick()
    }
}

/** Select the patient card's standard selection/click semantics, without custom TalkBack actions. */
fun ComposeTestRule.openPatientEditor(name: String) {
    val card = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) and hasClickAction() and
        hasAnyDescendant(hasText(name, substring = true))
    onNode(card, useUnmergedTree = true)
        .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions).not()).performClick()
}
