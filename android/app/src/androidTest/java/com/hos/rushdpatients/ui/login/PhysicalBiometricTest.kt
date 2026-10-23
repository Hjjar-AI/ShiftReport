package com.hos.rushdpatients.ui.login

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.domain.auth.BiometricHelper
import com.hos.rushdpatients.testutil.BiometricProbeActivity
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalBiometricTest {
    @Test fun enrolledFingerprintReachesProductionSuccessCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Explicit owner presence required", InstrumentationRegistry.getArguments().getString("physicalBiometric") == "true")
        assumeTrue("Enrolled biometrics required", BiometricHelper(instrumentation.targetContext).isAvailable())
        ActivityScenario.launch(BiometricProbeActivity::class.java).use { scenario ->
            scenario.onActivity { it.authenticate() }
            val deadline = SystemClock.elapsedRealtime() + 120_000L
            var outcome = "waiting"
            while (outcome == "waiting" && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { outcome = it.outcome }
                SystemClock.sleep(100)
            }
            assertEquals("Physical fingerprint result", "success", outcome)
        }
    }
}
