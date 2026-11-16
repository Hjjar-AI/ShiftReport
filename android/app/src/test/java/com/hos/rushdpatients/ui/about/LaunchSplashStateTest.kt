package com.hos.rushdpatients.ui.about

import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class LaunchSplashStateTest {
    private var now = 1000L
    @Before fun setup() { mockkStatic(SystemClock::class); every { SystemClock.elapsedRealtime() } answers { now } }
    @After fun cleanup() { unmockkStatic(SystemClock::class) }
    @Test fun expiredHoldDismissesOnReleaseWithoutExtendingDeadline() {
        val state = LaunchSplashState(mutableStateOf(true), 6000L)
        state.press(); now = 7000L
        assertEquals(0L, state.tick()); assertTrue(state.visible)
        assertEquals(6000L, state.deadline)
        state.release(); assertFalse(state.visible)
    }
    @Test fun recreatedStateKeepsDeadlineAndDoesNotRetainAStalePress() {
        val showing = mutableStateOf(true)
        val state = LaunchSplashState(showing, 6000L)
        state.press(); now = 7000L
        val recreated = LaunchSplashState(showing, state.deadline)
        recreated.tick(); assertFalse(recreated.visible)
    }
    @Test fun preferenceLoadingConsumesOriginalDeadlineWithoutGrantingExtraIntroTime() {
        val state = LaunchSplashState(mutableStateOf(true), now + 3000L)
        val deadline = state.deadline
        // No intro text is composed while preferences are pending; the launch clock still advances.
        now += 3500L
        assertEquals(0L, state.tick())
        assertFalse(state.visible)
        assertEquals(deadline, state.deadline)
    }
    @Test fun releaseBeforeDeadlineKeepsIntroUntilOriginalExpiry() {
        val state = LaunchSplashState(mutableStateOf(true), now + 3000L)
        state.press(); now += 500L; state.release()
        assertTrue(state.visible)
        assertEquals(2500L, state.tick())
        now += 2500L
        state.tick()
        assertFalse(state.visible)
    }
    @Test fun skippedIntroCannotReappearWhenPreferencesBecomeReady() {
        val showing = mutableStateOf(true)
        val state = LaunchSplashState(showing, now + 3000L)
        state.skip()
        now += 1000L
        // Storage readiness/recreation reuses launch visibility and deadline.
        val ready = LaunchSplashState(showing, state.deadline)
        ready.tick(); ready.release()
        assertFalse(ready.visible)
        assertEquals(state.deadline, ready.deadline)
    }

}
