package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.appContentEntrance
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiBorder
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.UiSize
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
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

private typealias PaneRegion = FoldSafeRegion

/** Physical fold coordinates are translated from window pixels into this content's local dp. */
@Composable
internal fun WardAdaptivePanes(
    enableSplit: Boolean,
    allowMediumSupport: Boolean = false,
    foldingFeature: FoldingFeature?,
    listFraction: Float,
    onListFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    primaryTitle: String = androidx.compose.ui.res.stringResource(R.string.ui_ward_adaptive_panes_001),
    supportingTitle: String = androidx.compose.ui.res.stringResource(R.string.ui_ward_adaptive_panes_002),
    primary: @Composable (Boolean) -> Unit,
    supporting: @Composable () -> Unit = {},
    compactOverlay: @Composable () -> Unit = {}
) {
    val scale = com.hos.rushdpatients.ui.theme.uiLayoutScale()
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
        // Keep content/drafts mounted while the first measured origin becomes available.
        val regions = foldSafeRegions(fold, offset, width * density, height * density,
            UiLayout.foldClearance.value * density).map {
            PaneRegion(it.x / density, it.y / density, it.width / density, it.height / density)
        }
        val foldSplit = regions.size == 2 && enableSplit && regions.all {
            it.width >= (UiLayout.foldPaneMinWidth.value * scale) && it.height >= (UiLayout.paneMinHeight.value * scale)
        }
        val regularSplit = enableSplit && regions.isEmpty() && width >= maxOf(UiLayout.splitMinWidth.value * scale,
            (UiLayout.primaryPaneMinWidth.value + UiLayout.supportingPaneMinWidth.value) * scale + UiSpacing.touchTarget.value) && height >= (UiLayout.paneMinHeight.value * scale)
        val mediumSupport = enableSplit && allowMediumSupport && regions.isEmpty() &&
            !regularSplit && width >= (UiLayout.mediumSupportMinWidth.value * scale) && height >= (UiLayout.mediumSupportMinHeight.value * scale)
        val split = foldSplit || regularSplit || mediumSupport
        val total = (width - UiSpacing.touchTarget.value).coerceAtLeast(1f)
        val minimum = ((UiLayout.primaryPaneMinWidth.value * scale) / total).coerceIn(0f, 1f)
        val maximum = (1f - (UiLayout.supportingPaneMinWidth.value * scale) / total).coerceIn(minimum, 1f)
        val fraction = listFraction.coerceIn(minimum, maximum)
        LaunchedEffect(width, height, scale, regularSplit, fraction) {
            if (regularSplit && fraction != listFraction) onListFractionChange(fraction)
        }
        val listWidth = total * fraction
        val primaryRegion = when {
            foldSplit -> if (fold?.orientation == FoldingFeature.Orientation.VERTICAL && rtl) regions[1] else regions[0]
            regularSplit -> PaneRegion(if (rtl) width - listWidth else 0f, 0f, listWidth, height)
            mediumSupport -> PaneRegion(0f, 0f, width, height - (UiLayout.mediumSupportHeight.value * scale))
            regions.isNotEmpty() -> regions.maxBy { it.width * it.height }
            else -> full
        }
        val supportingRegion = when {
            foldSplit -> regions.first { it != primaryRegion }
            regularSplit -> PaneRegion(if (rtl) 0f else listWidth + UiSpacing.touchTarget.value, 0f, total - listWidth, height)
            mediumSupport -> PaneRegion(0f, height - (UiLayout.mediumSupportHeight.value * scale), width, (UiLayout.mediumSupportHeight.value * scale))
            else -> full
        }
        PaneRegionBox(primaryRegion, primaryTitle, 0f) { primary(split) }
        if (split) {
            PaneRegionBox(supportingRegion, supportingTitle, 1f) {
                Box(Modifier.appContentEntrance(supportingTitle)) { supporting() }
            }
            if (regularSplit) {
                val dividerX = if (rtl) supportingRegion.width else listWidth
                val direction = if (rtl) -1f else 1f
                fun adjust(value: Float) = onListFractionChange(value.coerceIn(minimum, maximum))
                val dividerColor = MaterialTheme.colorScheme.outlineVariant
                Box(
                    Modifier.absoluteOffset(x = dividerX.dp).width(UiSpacing.touchTarget).height(height.dp)
                        .drawBehind {
                            drawLine(dividerColor, Offset(size.width / 2, 0f),
                                Offset(size.width / 2, size.height), UiBorder.hairline.toPx())
                        }
                        .then(if (dividerFocused) Modifier.border(UiBorder.stroke(focused = true)) else Modifier)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                Key.DirectionRight -> { adjust(fraction + direction * UiLayout.paneAdjustmentFraction); true }
                                Key.DirectionLeft -> { adjust(fraction - direction * UiLayout.paneAdjustmentFraction); true }
                                Key.MoveHome -> { adjust(UiLayout.defaultListFraction); true }
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
                ) { Icon(AppIcons.DragHandle, contentDescription = null, modifier = Modifier.size(UiSize.icon)) }
            }
        } else compactOverlay()
    }
}

@Composable
private fun PaneRegionBox(region: PaneRegion, title: String, order: Float, content: @Composable () -> Unit) {
    Box(
        Modifier.absoluteOffset(region.x.dp, region.y.dp)
            .width(region.width.dp).height(region.height.dp).clipToBounds()
            .semantics { isTraversalGroup = true; traversalIndex = order }
    ) { content() }
}
