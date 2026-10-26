package com.hos.rushdpatients.ui.about

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.LocalUiPreferences
import com.hos.rushdpatients.ui.theme.LocalUiStyle
import com.hos.rushdpatients.ui.theme.uiContentGap
import com.hos.rushdpatients.ui.theme.uiContentPadding

/** MainActivity owns the fixed five-second deadline; tapping skips the intro. */
@Composable
internal fun SplashIntroScreen(
    onSkip: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val currentOnSkip by rememberUpdatedState(onSkip)
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!entered) {
            withFrameNanos { }
            entered = true
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(lerp(colors.background, colors.primary, 0.08f), colors.background, colors.background)))
        .pointerInput(Unit) { detectTapGestures(onTap = { currentOnSkip() }) }
        .semantics { onClick(label = "تخطي المقدمة") { onSkip(); true } }
        .safeDrawingPadding().padding(UiSpacing.section), contentAlignment = Alignment.Center) {
        // Keep the credits reachable on short windows and with enlarged text.
        val emblemSize = minOf(com.hos.rushdpatients.ui.theme.UiSize.splashEmblem, maxHeight * 0.25f)
            .coerceAtLeast(96.dp)
        Column(Modifier.widthIn(max = com.hos.rushdpatients.ui.theme.UiLayout.splashMaxWidth)
            .fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.largeSection)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.matchParentSize().then(splashEntrance(entered, order = 0, initialScale = 0.88f))
                        .graphicsLayer { scaleX = 1.35f; scaleY = 1.35f }
                        .background(Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.15f), Color.Transparent))))
                    Surface(modifier = splashEntrance(entered, order = 0, initialScale = 0.92f),
                        color = Color.White, shape = MaterialTheme.shapes.extraLarge,
                        border = BorderStroke(LocalUiStyle.current.normalBorder, colors.outlineVariant)) {
                        Image(painterResource(R.drawable.app_emblem_transparent), contentDescription = "شعار شعبة الطب النفسي",
                            contentScale = ContentScale.Fit, modifier = Modifier.size(emblemSize).padding(UiSpacing.screen))
                    }
                }
                Text("تقرير المناوبة", style = MaterialTheme.typography.headlineMedium, color = colors.primary,
                    textAlign = TextAlign.Center, modifier = splashEntrance(entered, order = 1).semantics { heading() })
                Text("للطب النفسي", style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = splashEntrance(entered, order = 2))
            }
            Surface(modifier = Modifier.fillMaxWidth().then(splashEntrance(entered, order = 3)), shape = MaterialTheme.shapes.large,
                color = colors.surface, border = BorderStroke(LocalUiStyle.current.normalBorder, colors.outlineVariant)) {
                Column {
                    SplashCreditSection("إشراف وتطوير", listOf(introSupervisorName), 0, entered)
                    Divider(Modifier.padding(horizontal = UiSpacing.largeSection), color = colors.outlineVariant)
                    SplashCreditSection("مساهمة تقنية · الأطباء", technicalContributors, 1, entered)
                    Divider(Modifier.padding(horizontal = UiSpacing.largeSection), color = colors.outlineVariant)
                    SplashCreditSection("فكرة Excel الأولية وبنية التقرير الأساسي", listOf(introOriginalAuthorName), 2, entered)
                }
            }
        }
    }
}

/** One entrance per splash instance; layers preserve layout, scrolling and touch targets. */
@Composable
private fun splashEntrance(entered: Boolean, order: Int, initialScale: Float = 1f): Modifier {
    val reducedMotion = LocalUiPreferences.current.reducedMotion
    val target = if (entered || reducedMotion) 1f else 0f
    val opacity by animateFloatAsState(targetValue = target,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 420,
            delayMillis = if (reducedMotion) 0 else order * 65), label = "splashOpacity$order")
    val progress by animateFloatAsState(targetValue = target,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 900,
            delayMillis = if (reducedMotion) 0 else order * 65,
            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)), label = "splashEntrance$order")
    val travel = with(LocalDensity.current) { UiSpacing.medium.toPx() }

    return Modifier.graphicsLayer {
        val fraction = if (reducedMotion) 1f else progress
        alpha = if (reducedMotion) 1f else opacity
        translationY = travel * (1f - fraction)
        scaleX = initialScale + (1f - initialScale) * fraction
        scaleY = scaleX
    }
}

@Composable
private fun SplashCreditSection(role: String, names: List<String>, order: Int, entered: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().then(splashEntrance(entered, order = order + 3)).semantics(mergeDescendants = true) {}
        .background(if (order == 0) colors.primary.copy(alpha = 0.06f) else Color.Transparent)
        .padding(uiContentPadding(PaddingValues(horizontal = UiSpacing.largeSection, vertical = UiSpacing.section))),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(uiContentGap())) {
        Text(role, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        names.forEach { name ->
            Text(name, style = if (order == 0) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}
