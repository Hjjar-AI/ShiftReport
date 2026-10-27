package com.hos.rushdpatients.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.about.rememberLaunchSplashState
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the production gate with controlled IO, never initializes clinical storage. */
@RunWith(AndroidJUnit4::class)
class StorageAccessGateTest {
    @get:Rule val compose = createComposeRule()
    private val retry get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.ui_retry)

    @Test fun skipCannotBypassPendingStorageAndReadyDoesNotReplayIntro() {
        val release = CompletableDeferred<Unit>()
        lateinit var splash: com.hos.rushdpatients.ui.about.LaunchSplashState
        compose.setContent {
            splash = rememberLaunchSplashState()
            MaterialTheme {
                StorageAccessGate(loading = { Text("INTRO") }, checkAccess = { release.await() }) {
                    Text(if (splash.visible) "INTRO" else "WORKSPACE")
                }
            }
        }
        compose.onNodeWithText("INTRO").assertExists()
        compose.runOnIdle { splash.skip() }
        compose.onNodeWithText("INTRO").assertExists()
        compose.onNodeWithText("WORKSPACE").assertDoesNotExist()
        release.complete(Unit)
        compose.waitUntil(5000) { compose.onAllNodesWithText("WORKSPACE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("INTRO").assertDoesNotExist()
    }
    @Test fun expiredDeadlineCannotBypassPendingStorage() {
        val release = CompletableDeferred<Unit>()
        compose.setContent {
            // An expired real launch state, with the same gate/content split as MainActivity.
            val splash = remember { com.hos.rushdpatients.ui.about.LaunchSplashState(mutableStateOf(true), 0L) }
            LaunchedEffect(Unit) { splash.tick() }
            MaterialTheme {
                StorageAccessGate(loading = { Text("INTRO") }, checkAccess = { release.await() }) {
                    Text(if (splash.visible) "INTRO" else "WORKSPACE")
                }
            }
        }
        compose.onNodeWithText("INTRO").assertExists()
        compose.onNodeWithText("WORKSPACE").assertDoesNotExist()
        release.complete(Unit)
        compose.waitUntil(5000) { compose.onAllNodesWithText("WORKSPACE").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun failedStorageShowsPreservationAndRetryWithoutExposingException() {
        val attempts = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        compose.setContent {
            MaterialTheme {
                StorageAccessGate(loading = { Text("INTRO") }, checkAccess = {
                    if (attempts.incrementAndGet() == 1) throw java.io.IOException("synthetic-private-detail")
                    release.await()
                }) { Text("WORKSPACE") }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText(retry).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("synthetic-private-detail", substring = true).assertDoesNotExist()
        compose.onNodeWithText("WORKSPACE").assertDoesNotExist()
        compose.onNodeWithText(retry).performClick()
        compose.waitUntil(5000) { attempts.get() == 2 }
        compose.onNodeWithText("INTRO").assertExists()
        release.complete(Unit)
        compose.waitUntil(5000) { compose.onAllNodesWithText("WORKSPACE").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun recreationRechecksStorageButRetainsSkippedLaunchState() {
        val restoration = StateRestorationTester(compose)
        val release = CompletableDeferred<Unit>()
        val attempts = AtomicInteger()
        lateinit var splash: com.hos.rushdpatients.ui.about.LaunchSplashState
        var deadline = 0L
        restoration.setContent {
            splash = rememberLaunchSplashState()
            MaterialTheme {
                StorageAccessGate(loading = { Text("INTRO") }, checkAccess = {
                    attempts.incrementAndGet(); release.await()
                }) { Text(if (splash.visible) "INTRO" else "WORKSPACE") }
            }
        }
        compose.waitUntil(5000) { attempts.get() == 1 }
        compose.runOnIdle { splash.skip(); deadline = splash.deadline }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(5000) { attempts.get() == 2 }
        compose.runOnIdle { assertFalse(splash.visible); assertEquals(deadline, splash.deadline) }
        compose.onNodeWithText("INTRO").assertExists()
        release.complete(Unit)
        compose.waitUntil(5000) { compose.onAllNodesWithText("WORKSPACE").fetchSemanticsNodes().isNotEmpty() }
    }
}
