package com.hos.rushdpatients.ui.ward

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.testutil.PatientScrollProbeActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/** Real Android frames and touch events; no Compose test clock or idling between moves. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PatientScrollPerformanceTest {
    @Test fun recordFrameDurationsDuringNativePatientCardScrolling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val metrics = linkedMapOf("total" to FrameMetrics.TOTAL_DURATION,
            "input" to FrameMetrics.INPUT_HANDLING_DURATION, "animation" to FrameMetrics.ANIMATION_DURATION,
            "layout" to FrameMetrics.LAYOUT_MEASURE_DURATION, "draw" to FrameMetrics.DRAW_DURATION,
            "sync" to FrameMetrics.SYNC_DURATION, "commands" to FrameMetrics.COMMAND_ISSUE_DURATION,
            "unknownDelay" to FrameMetrics.UNKNOWN_DELAY_DURATION)
        val values = metrics.mapValues { Collections.synchronizedList(mutableListOf<Long>()) }
        val thread = HandlerThread("native-scroll-frames").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            metrics.forEach { (key, id) -> values.getValue(key).add(frame.getMetric(id)) }
        }
        try {
            ActivityScenario.launch(PatientScrollProbeActivity::class.java).use { scenario ->
                val deadline = SystemClock.elapsedRealtime() + 10_000
                var ready = false
                while (!ready && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { ready = it.ready() }
                    SystemClock.sleep(20)
                }
                assertTrue("Synthetic grid did not become ready", ready)
                var width = 0; var height = 0; var left = 0; var top = 0
                scenario.onActivity {
                    val view = it.window.decorView
                    width = view.width; height = view.height
                    val origin = IntArray(2); view.getLocationOnScreen(origin)
                    left = origin[0]; top = origin[1]
                }
                fun swipe() {
                    val down = SystemClock.uptimeMillis()
                    fun send(action: Int, fraction: Float) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                            left + width * .5f, top + height * (.8f - .6f * fraction), 0)
                        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
                        finally { event.recycle() }
                    }
                    send(MotionEvent.ACTION_DOWN, 0f)
                    for (step in 1..12) { SystemClock.sleep(16); send(MotionEvent.ACTION_MOVE, step / 12f) }
                    send(MotionEvent.ACTION_UP, 1f)
                    SystemClock.sleep(200)
                }
                swipe() // Warm font/layout and touch paths before sampling.
                scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
                try {
                    repeat(8) { swipe() }
                    SystemClock.sleep(300)
                    scenario.onActivity { assertTrue("Native gestures did not scroll", it.grid.firstVisibleItemIndex > 0) }
                } finally {
                    scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                }
            }
        } finally { thread.quitSafely(); thread.join(2000) }
        val total = synchronized(values.getValue("total")) { values.getValue("total").toList() }
        assertTrue("No real frames captured", total.isNotEmpty())
        val phases = values.entries.joinToString(",") { (name, list) ->
            val sorted = synchronized(list) { list.toList().sorted() }
            "\"$name\":{\"medianMs\":${sorted[sorted.size / 2] / 1e6}," +
                "\"p95Ms\":${sorted[((sorted.size - 1) * .95).toInt()] / 1e6}}"
        }
        val report = "{\"frames\":${total.size},\"over16_67ms\":${total.count { it > 16_666_667 }}," +
            "\"build\":\"debug-native-clock\",\"phases\":{$phases}}"
        File(instrumentation.targetContext.getExternalFilesDir(null), "verification-artifacts").apply { mkdirs() }
            .resolve("patient-scroll-native-frames.json").writeText(report)
    }
}
