package com.hos.rushdpatients.ui.about

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.BuildConfig
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing

/** MainActivity owns the fixed five-second deadline; holding only defers dismissal. */
@Composable
internal fun SplashIntroScreen(
    onSkip: () -> Unit,
    remainingSeconds: Int,
    held: Boolean,
    onHeldChange: (Boolean) -> Unit
) {
    val currentOnSkip by rememberUpdatedState(onSkip)
    val currentOnHeldChange by rememberUpdatedState(onHeldChange)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    Box(Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(Color(0xFF0D1729), Color(0xFF1E3850))))
        .pointerInput(Unit) {
            awaitEachGesture {
                // Observe before scrolling consumes input; do not consume reading/scroll gestures.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var moved = false
                var releasedAt = down.uptimeMillis
                var pressed: Boolean
                currentOnHeldChange(true)
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.id == down.id }?.let {
                            releasedAt = it.uptimeMillis
                            if ((it.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        }
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                    // A hold defers dismissal until release; only a short stationary tap skips.
                    if (!moved && releasedAt - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) currentOnSkip()
                } finally {
                    currentOnHeldChange(false)
                }
            }
        }
        .semantics {
            onClick(label = "تخطي المقدمة") { onSkip(); true }
            customActions = listOf(CustomAccessibilityAction(
                if (held) "إنهاء القراءة" else "إبقاء المقدمة مفتوحة للقراءة"
            ) { onHeldChange(!held); true })
        }
        .safeDrawingPadding().padding(UiSpacing.section), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
            Surface(color = Color.White, shape = MaterialTheme.shapes.large) {
                Image(painterResource(R.drawable.app_emblem_transparent), contentDescription = "شعار شعبة الطب النفسي",
                    contentScale = ContentScale.Fit, modifier = Modifier.size(120.dp).padding(UiSpacing.screen))
            }
            Text("تقرير المناوبة", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFF1F5FF),
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text("للطب النفسي", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB9D1EC), textAlign = TextAlign.Center)
            SplashCreditCard("إشراف وتطوير", listOf(introSupervisorName), 0, entered)
            SplashCreditCard("مساهمة تقنية · الأطباء", technicalContributors, 1, entered)
            SplashCreditCard("فكرة Excel الأولية وبنية التقرير الأساسي", listOf(introOriginalAuthorName), 2, entered)
            Text("الإصدار ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB9D1EC))
            Text(if (held && remainingSeconds <= 0) "انتهى الوقت · ارفع إصبعك للمتابعة"
                else if (held) "ارفع إصبعك عند انتهاء القراءة · المتبقي: ${uiNumber(remainingSeconds)} ث"
                else "اضغط مطولاً للقراءة · لمسة سريعة للتخطي · المتبقي: ${uiNumber(remainingSeconds.coerceAtLeast(0))} ث",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = Color(0xFFDAE8F8))
        }
    }
}

@Composable
private fun SplashCreditCard(role: String, names: List<String>, order: Int, entered: Boolean) {
    val fraction by animateFloatAsState(targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 320, delayMillis = order * 110), label = "splashCreditEntry")
    val travel = with(LocalDensity.current) { 12.dp.toPx() }
    Surface(modifier = Modifier.fillMaxWidth().graphicsLayer {
        alpha = fraction
        translationY = travel * (1f - fraction)
    }.semantics(mergeDescendants = true) {}, shape = MaterialTheme.shapes.large,
        color = Color(0xFF223750), contentColor = Color(0xFFF1F5FF),
        border = BorderStroke(1.dp, Color(0xFF405C78))) {
        Column(Modifier.padding(horizontal = UiSpacing.section, vertical = UiSpacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            Text(role, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB9D1EC), textAlign = TextAlign.Center)
            names.forEach { name ->
                Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}
