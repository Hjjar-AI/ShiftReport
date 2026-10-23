package com.hos.rushdpatients.ui.ward

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.ui.theme.RushdPatientsTheme
import com.hos.rushdpatients.ui.theme.UiPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/** Exercises real cards at the ward's 200-patient limit; records debug timings, not a release SLA. */
@RunWith(AndroidJUnit4::class)
class PatientGridScrollTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var index = 0
    private val patients = (1..200).map { n -> Patient(id = "scroll-$n", name = "شاي تجريبي $n",
        sortOrder = n, initialDiagnosis = "تشخيص تجريبي", treatmentPlan = "خطة تجريبية",
        followUp = "متابعة تجريبية", labs = "نتائج تجريبية",
        tasks = listOf(PatientTask("task-$n", "مهمة تجريبية"))) }
    private fun show(expanded: Boolean, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, fontScale)) {
                RushdPatientsTheme(uiPreferences = UiPreferences(reducedMotion = true)) {
                    val grid = rememberLazyGridState()
                    LaunchedEffect(grid) { snapshotFlow { grid.firstVisibleItemIndex }.collect { index = it } }
                    Box(Modifier.fillMaxSize().testTag("patient-grid")) {
                        WardPatientGrid(WardUiState(loading = false, patients = patients, groupByMode = GroupByMode.NONE),
                            patients, false, grid, 48.dp, emptyList(), emptyList(), {}, {},
                            controlsContent = { Text("بيانات تجريبية — 200 مريض") }, headerContent = {},
                            patientCard = { patient, modifier -> PatientCard(PatientCardState(patient,
                                expanded = expanded, taskNowEpochMillis = 100), PatientCardActions.None, modifier) })
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    @Test fun collapsedCardsScrollThroughLargeWardAndReturnToTop() {
        show(false)
        repeat(6) { compose.onNodeWithTag("patient-grid").performTouchInput { swipeUp(durationMillis = 180) } }
        compose.runOnIdle { assertTrue("List did not advance", index > 0) }
        repeat(8) { compose.onNodeWithTag("patient-grid").performTouchInput { swipeDown(durationMillis = 180) } }
        compose.runOnIdle { assertEquals(0, index) }
    }
    @Test fun expandedCardsRemainScrollableAtLargeFontAndProduceReviewArtifact() {
        show(true, 1.8f)
        repeat(3) { compose.onNodeWithTag("patient-grid").performTouchInput { swipeUp(durationMillis = 200) } }
        compose.runOnIdle { assertTrue(index > 0) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "verification-artifacts").apply { mkdirs() }
            .resolve("patient-cards-rtl-large-font.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
    }
    @Test fun recordFrameDurationsDuringRealCardScrolling() {
        show(false)
        val durations = Collections.synchronizedList(mutableListOf<Long>())
        val phases = linkedMapOf("input" to FrameMetrics.INPUT_HANDLING_DURATION,
            "animation" to FrameMetrics.ANIMATION_DURATION,
            "layout" to FrameMetrics.LAYOUT_MEASURE_DURATION, "draw" to FrameMetrics.DRAW_DURATION,
            "sync" to FrameMetrics.SYNC_DURATION, "commands" to FrameMetrics.COMMAND_ISSUE_DURATION,
            "unknownDelay" to FrameMetrics.UNKNOWN_DELAY_DURATION)
        val phaseSamples = phases.mapValues { Collections.synchronizedList(mutableListOf<Long>()) }
        val thread = HandlerThread("synthetic-scroll-frames").apply { start() }
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            durations.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
            phases.forEach { (name, metric) -> phaseSamples.getValue(name).add(metrics.getMetric(metric)) }
        }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        try {
            repeat(8) { compose.onNodeWithTag("patient-grid").performTouchInput { swipeUp(durationMillis = 180) } }
            compose.waitForIdle()
        } finally {
            compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely(); thread.join(2000)
        }
        val samples = synchronized(durations) { durations.toList().sorted() }
        assertTrue("No frame metrics captured", samples.isNotEmpty())
        val phaseReport = phaseSamples.entries.joinToString(",") { (name, values) ->
            val sorted = synchronized(values) { values.toList().sorted() }
            "\"$name\":{\"medianMs\":${sorted[sorted.size / 2] / 1_000_000.0}," +
                "\"p95Ms\":${sorted[((sorted.size - 1) * .95).toInt()] / 1_000_000.0}}"
        }
        val report = "{\"frames\":${samples.size},\"medianMs\":${samples[samples.size / 2] / 1_000_000.0}," +
            "\"p95Ms\":${samples[((samples.size - 1) * 0.95).toInt()] / 1_000_000.0}," +
            "\"over16_67ms\":${samples.count { it > 16_666_667 }},\"build\":\"debug-instrumented\",\"phases\":{$phaseReport}}"
        File(compose.activity.getExternalFilesDir(null), "verification-artifacts").apply { mkdirs() }
            .resolve("patient-scroll-frames.json").writeText(report)
    }
}
