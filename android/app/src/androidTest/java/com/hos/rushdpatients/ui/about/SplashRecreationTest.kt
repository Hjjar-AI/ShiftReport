package com.hos.rushdpatients.ui.about

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.testutil.SplashProbeActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplashRecreationTest {
    private fun await(scenario: ActivityScenario<SplashProbeActivity>, condition: (SplashProbeActivity) -> Boolean) {
        val timeout = SystemClock.elapsedRealtime() + 8000
        while (SystemClock.elapsedRealtime() < timeout) {
            var success = false
            scenario.onActivity { success = it.ready() && condition(it) }
            if (success) return
            Thread.sleep(25)
        }
        fail("Splash state condition timed out")
    }
    @Test fun heldTimerExpiresAndRecreationRetainsDeadlineWithoutRestoringPhysicalHold() {
        ActivityScenario.launch(SplashProbeActivity::class.java).use { scenario ->
            await(scenario) { true }
            var deadline = 0L
            scenario.onActivity { deadline = it.splash.deadline; it.splash.changeHold(true) }
            await(scenario) { it.splash.remainingSeconds == 0 }
            scenario.onActivity { assertTrue(it.splash.visible); assertTrue(it.splash.held) }
            scenario.recreate()
            await(scenario) { !it.splash.visible }
            scenario.onActivity { assertEquals(deadline, it.splash.deadline); assertFalse(it.splash.held) }
        }
    }
    @Test fun quickSkipSurvivesActualActivityRecreation() {
        ActivityScenario.launch(SplashProbeActivity::class.java).use { scenario ->
            await(scenario) { true }
            scenario.onActivity { it.splash.skip() }
            scenario.recreate()
            await(scenario) { !it.splash.visible }
        }
    }
    @Test fun backgroundResumeDoesNotStartAnotherFiveSecondDeadline() {
        ActivityScenario.launch(SplashProbeActivity::class.java).use { scenario ->
            await(scenario) { true }
            var deadline = 0L
            scenario.onActivity { deadline = it.splash.deadline }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            await(scenario) { true }
            scenario.onActivity { assertEquals(deadline, it.splash.deadline) }
        }
    }
}
