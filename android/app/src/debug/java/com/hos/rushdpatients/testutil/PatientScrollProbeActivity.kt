package com.hos.rushdpatients.testutil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.ui.theme.RushdPatientsTheme
import com.hos.rushdpatients.ui.theme.UiPreferences
import com.hos.rushdpatients.ui.ward.*

/** Normal Android frame clock, synthetic cards, no clinical storage or network. */
class PatientScrollProbeActivity : ComponentActivity() {
    internal lateinit var grid: LazyGridState
    internal fun ready() = ::grid.isInitialized && grid.layoutInfo.totalItemsCount > 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val patients = (1..200).map { n -> Patient(id = "scroll-$n", name = "شاي تجريبي $n",
            sortOrder = n, initialDiagnosis = "تشخيص تجريبي", treatmentPlan = "خطة تجريبية",
            followUp = "متابعة تجريبية", labs = "نتائج تجريبية",
            tasks = listOf(PatientTask("task-$n", "مهمة تجريبية"))) }
        val reducedMotion = intent.getBooleanExtra("reducedMotion", false)
        val state = WardUiState(loading = false, patients = patients, groupByMode = GroupByMode.NONE)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RushdPatientsTheme(uiPreferences = UiPreferences(reducedMotion = reducedMotion)) {
                    grid = rememberLazyGridState()
                    Surface {
                        WardPatientGrid(state, patients, false, grid, 48.dp, emptyList(), emptyList(), {}, {},
                            controlsContent = { Text("بيانات تجريبية — 200 مريض") }, headerContent = {},
                            patientCard = { patient, modifier -> PatientCard(PatientCardState(patient),
                                PatientCardActions.None, modifier) })
                    }
                }
            }
        }
    }
}
