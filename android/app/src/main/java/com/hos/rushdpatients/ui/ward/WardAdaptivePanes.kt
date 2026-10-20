package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiBorder
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.UiSize
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Composable
internal fun rememberWardFoldingFeature(): FoldingFeature? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val flow = remember(context, activity) {
        if (activity == null) flowOf<FoldingFeature?>(null)
        else WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull {
                it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL
            }
        }
    }
    return flow.collectAsStateWithLifecycle(initialValue = null).value
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
    else -> null
}

private data class PaneRegion(val x: Float, val y: Float, val width: Float, val height: Float)

/** Physical fold coordinates are translated from window pixels into this content's local dp. */
@Composable
internal fun WardAdaptivePanes(
    enableSplit: Boolean,
    allowMediumSupport: Boolean = false,
    foldingFeature: FoldingFeature?,
    listFraction: Float,
    onListFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    primaryTitle: String = "محتوى المناوبة",
    supportingTitle: String = "ملف المريض وجاهزية التقرير",
    primary: @Composable (Boolean) -> Unit,
    supporting: @Composable () -> Unit = {},
    compactOverlay: @Composable () -> Unit = {}
) {
    val density = LocalDensity.current.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var origin by remember { mutableStateOf<Offset?>(null) }
    var dividerFocused by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.onGloballyPositioned { origin = it.positionInWindow() },
        contentAlignment = AbsoluteAlignment.TopLeft) {
        val width = maxWidth.value
        val height = maxHeight.value
        val full = PaneRegion(0f, 0f, width, height)
        val offset = origin
        val fold = foldingFeature
        // Wait for the local origin before drawing content around a known fold.
        if (fold != null && offset == null) return@BoxWithConstraints
        val regions = if (fold != null && offset != null) {
            val bounds = fold.bounds
            if (fold.orientation == FoldingFeature.Orientation.VERTICAL) {
                val start = (bounds.left - offset.x) / density
                val end = (bounds.right - offset.x) / density
                if (end >= 0f && start <= width) listOf(
                    PaneRegion(0f, 0f, (start - UiLayout.foldClearance.value).coerceIn(0f, width), height),
                    PaneRegion((end + UiLayout.foldClearance.value).coerceIn(0f, width), 0f, (width - end - UiLayout.foldClearance.value).coerceIn(0f, width), height)
                ) else emptyList()
            } else {
                val start = (bounds.top - offset.y) / density
                val end = (bounds.bottom - offset.y) / density
                if (end >= 0f && start <= height) listOf(
                    PaneRegion(0f, 0f, width, (start - UiLayout.foldClearance.value).coerceIn(0f, height)),
                    PaneRegion(0f, (end + UiLayout.foldClearance.value).coerceIn(0f, height), width, (height - end - UiLayout.foldClearance.value).coerceIn(0f, height))
                ) else emptyList()
            }
        } else emptyList()
        val foldSplit = regions.size == 2 && enableSplit && regions.all {
            it.width >= UiLayout.foldPaneMinWidth.value && it.height >= UiLayout.paneMinHeight.value
        }
        val regularSplit = enableSplit && regions.isEmpty() && width >= UiLayout.splitMinWidth.value && height >= UiLayout.paneMinHeight.value
        val mediumSupport = enableSplit && allowMediumSupport && regions.isEmpty() &&
            !regularSplit && width >= UiLayout.mediumSupportMinWidth.value && height >= UiLayout.mediumSupportMinHeight.value
        val split = foldSplit || regularSplit || mediumSupport
        val total = (width - UiSpacing.touchTarget.value).coerceAtLeast(1f)
        val minimum = (UiLayout.primaryPaneMinWidth.value / total).coerceIn(0f, 1f)
        val maximum = (1f - UiLayout.supportingPaneMinWidth.value / total).coerceIn(minimum, 1f)
        val fraction = listFraction.coerceIn(minimum, maximum)
        LaunchedEffect(width, height, regularSplit) {
            if (regularSplit && fraction != listFraction) onListFractionChange(fraction)
        }
        val listWidth = total * fraction
        val primaryRegion = when {
            foldSplit -> if (fold?.orientation == FoldingFeature.Orientation.VERTICAL && rtl) regions[1] else regions[0]
            regularSplit -> PaneRegion(if (rtl) width - listWidth else 0f, 0f, listWidth, height)
            mediumSupport -> PaneRegion(0f, 0f, width, height - UiLayout.mediumSupportHeight.value)
            regions.isNotEmpty() -> regions.maxBy { it.width * it.height }
            else -> full
        }
        val supportingRegion = when {
            foldSplit -> regions.first { it != primaryRegion }
            regularSplit -> PaneRegion(if (rtl) 0f else listWidth + UiSpacing.touchTarget.value, 0f, total - listWidth, height)
            mediumSupport -> PaneRegion(0f, height - UiLayout.mediumSupportHeight.value, width, UiLayout.mediumSupportHeight.value)
            else -> full
        }
        PaneRegionBox(primaryRegion, primaryTitle, 0f) { primary(split) }
        if (split) {
            PaneRegionBox(supportingRegion, supportingTitle, 1f) { supporting() }
            if (regularSplit) {
                val dividerX = if (rtl) supportingRegion.width else listWidth
                val direction = if (rtl) -1f else 1f
                fun adjust(value: Float) = onListFractionChange(value.coerceIn(minimum, maximum))
                Box(
                    Modifier.absoluteOffset(x = dividerX.dp).width(UiSpacing.touchTarget).height(height.dp)
                        .background(if (dividerFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .border(UiBorder.stroke(focused = dividerFocused))
                        .semantics {
                            contentDescription = "تغيير عرض قائمة المرضى"
                            stateDescription = "عرض القائمة ${(fraction * 100).toInt()} بالمئة"
                            progressBarRangeInfo = ProgressBarRangeInfo(fraction, minimum..maximum)
                            setProgress { adjust(it); true }
                            customActions = listOf(
                                CustomAccessibilityAction("توسيع القائمة") { adjust(fraction + .05f); true },
                                CustomAccessibilityAction("تضييق القائمة") { adjust(fraction - .05f); true },
                                CustomAccessibilityAction("إعادة توزيع العرض") { adjust(.42f); true }
                            )
                        }
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                Key.DirectionRight -> { adjust(fraction + direction * .05f); true }
                                Key.DirectionLeft -> { adjust(fraction - direction * .05f); true }
                                Key.MoveHome -> { adjust(.42f); true }
                                else -> false
                            }
                        }
                        .onFocusChanged { dividerFocused = it.isFocused }
                        .focusable()
                        .draggable(
                            state = rememberDraggableState { delta ->
                                adjust(listFraction + direction * delta / density / total)
                            },
                            orientation = Orientation.Horizontal
                        ),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.DragHandle, contentDescription = null, modifier = Modifier.size(UiSize.icon)) }
            }
        } else compactOverlay()
    }
}

@Composable
private fun PaneRegionBox(region: PaneRegion, title: String, order: Float, content: @Composable () -> Unit) {
    Box(
        Modifier.absoluteOffset(region.x.dp, region.y.dp)
            .width(region.width.dp).height(region.height.dp).clipToBounds()
            .semantics { paneTitle = title; isTraversalGroup = true; traversalIndex = order }
    ) { content() }
}
