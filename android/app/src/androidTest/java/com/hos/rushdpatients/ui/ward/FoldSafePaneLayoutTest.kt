package com.hos.rushdpatients.ui.ward

import android.graphics.Rect
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as LayoutRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.layout.FoldingFeature
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic hinge coordinates exercise production pane placement; no physical fold claim. */
@RunWith(AndroidJUnit4::class)
class FoldSafePaneLayoutTest {
    @get:Rule val compose = createComposeRule()
    private class Fold(override val bounds: Rect, override val orientation: FoldingFeature.Orientation) : FoldingFeature {
        override val isSeparating = true
        override val occlusionType = FoldingFeature.OcclusionType.FULL
        override val state = FoldingFeature.State.FLAT
    }
    private var fold by mutableStateOf<FoldingFeature?>(null)
    private var width by mutableStateOf(900)
    private var root: LayoutRect? = null
    private var primary: LayoutRect? = null
    private var supporting: LayoutRect? = null
    private var density = 1f
    private fun capture(assign: (LayoutRect) -> Unit) = Modifier.onGloballyPositioned {
        val origin = it.positionInWindow()
        assign(LayoutRect(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)) }
    private fun show() {
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { MaterialTheme {
            density = LocalDensity.current.density
            var draft by rememberSaveable { mutableStateOf("مسودة محفوظة") }
            Box(Modifier.requiredSize(width.dp, 600.dp).then(capture { root = it })) {
                WardAdaptivePanes(enableSplit = true, foldingFeature = fold, listFraction = 0.6f,
                    onListFractionChange = {}, modifier = Modifier.fillMaxSize(),
                    primary = { OutlinedTextField(draft, { draft = it }, Modifier.fillMaxSize().then(capture { primary = it })) },
                    supporting = { Box(Modifier.fillMaxSize().then(capture { supporting = it })) })
            }
        } } }
        compose.waitUntil { root != null && primary != null }
    }
    private fun hinge(vertical: Boolean): Rect {
        val r = requireNotNull(root)
        return if (vertical) Rect((r.left + r.width / 2 - 8 * density).toInt(), r.top.toInt(),
            (r.left + r.width / 2 + 8 * density).toInt(), r.bottom.toInt())
        else Rect(r.left.toInt(), (r.top + r.height / 2 - 8 * density).toInt(), r.right.toInt(),
            (r.top + r.height / 2 + 8 * density).toInt())
    }
    private fun assertAvoids(rect: LayoutRect, hinge: Rect) {
        val overlaps = rect.left < hinge.right && rect.right > hinge.left && rect.top < hinge.bottom && rect.bottom > hinge.top
        assertFalse("Clinical pane overlaps separating hinge", overlaps)
    }
    @Test fun rtlVerticalFoldPlacesBothPanesOutsideHinge() {
        show(); val hinge = hinge(true)
        compose.runOnIdle { fold = Fold(hinge, FoldingFeature.Orientation.VERTICAL) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertAvoids(requireNotNull(primary), hinge); assertAvoids(requireNotNull(supporting), hinge)
            assertTrue(requireNotNull(primary).left >= hinge.right)
        }
    }
    @Test fun horizontalFoldNeverPlacesPrimaryContentAcrossHinge() {
        show(); val hinge = hinge(false)
        compose.runOnIdle { fold = Fold(hinge, FoldingFeature.Orientation.HORIZONTAL) }
        compose.waitForIdle()
        compose.runOnIdle { assertAvoids(requireNotNull(primary), hinge) }
    }
    @Test fun hingeAppearanceAndRemovalKeepDraftMounted() {
        show()
        compose.onNode(hasSetTextAction()).performTextReplacement("مسودة بعد الكتابة")
        val hinge = hinge(true)
        compose.runOnIdle { fold = Fold(hinge, FoldingFeature.Orientation.VERTICAL) }
        compose.onNode(hasSetTextAction()).assertTextEquals("مسودة بعد الكتابة")
        compose.runOnIdle { fold = null; width = 360 }
        compose.onNode(hasSetTextAction()).assertTextEquals("مسودة بعد الكتابة")
    }
}
