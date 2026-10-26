package com.hos.rushdpatients.ui.ward

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientTask
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HandoverDashboardInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var selected: DashboardFilter? = null
    private var activity = 0; private var fetches = 0
    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)
    private fun show(admin: Boolean = false, synchronization: Boolean = true) {
        val patient = Patient(id = "tea", name = "شاي", isPriority = true,
            tasks = listOf(PatientTask("task", "فحص تجريبي", dueAtEpochMillis = 1000)))
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme { HandoverDashboard(admin, listOf(patient), "tree", PatientSyncStatus.LOCAL,
                online = false, freshnessLabel = "تجريبي",
                onFilter = { selected = it }, onSync = { fetches++ }, onActivity = { activity++ },
                showSynchronization = synchronization) }
        } }
    }
    @Test fun activityCenterIsAbsentForNonAdmin() {
        show()
        compose.onNodeWithText(label(R.string.ui_ward_activity_screen_003)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, activity) }
    }
    @Test fun retiredClinicalActivityIsAbsentForAdminToo() {
        show(admin = true)
        compose.onNodeWithText(label(R.string.ui_ward_activity_screen_003)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, activity); assertEquals(0, fetches); assertNull(selected) }
    }
    @Test fun localPresentationCanHideSynchronizationAction() {
        show(synchronization = false)
        compose.onNodeWithText(label(R.string.ui_ward_drawer_013)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, fetches) }
    }
    @Test fun urgentMetricOpensUrgentFilter() {
        show()
        compose.onNodeWithText(label(R.string.ui_handover_dashboard_006)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DashboardFilter.URGENT, selected); assertEquals(0, fetches) }
    }
    @Test fun retiredOverdueMetricIsAbsent() {
        show()
        compose.onNodeWithText(label(R.string.ui_handover_dashboard_007)).assertDoesNotExist()
        compose.runOnIdle { assertNull(selected); assertEquals(0, fetches) }
    }
    @Test fun pendingTaskMetricOpensPendingTasksRatherThanPublishing() {
        show()
        compose.onNodeWithText(label(R.string.ui_handover_dashboard_012)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DashboardFilter.TASK_PENDING, selected); assertEquals(0, fetches) }
    }
}
