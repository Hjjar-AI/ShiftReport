package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WardPreferencesTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } answers { firstArg<Int>().toString() } }
    private val state = MutableStateFlow(WardUiState())
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val expanded = MutableStateFlow<String?>(null)
    private val columns = MutableStateFlow<String?>(null)
    private val compact = MutableStateFlow<String?>(null)
    private val group = MutableStateFlow<String?>(null)
    private fun preferences(scope: CoroutineScope) = WardPreferences(text, state, scope, settings)
    private fun observe(scope: CoroutineScope): WardPreferences {
        every { settings.observe(AppConstants.SETTING_PATIENT_DETAILS_EXPANDED) } returns expanded
        every { settings.observe(AppConstants.SETTING_PATIENT_TWO_COLUMN) } returns columns
        every { settings.observe(AppConstants.SETTING_PATIENT_COMPACT_DENSITY) } returns compact
        every { settings.observe(AppConstants.SETTING_GROUP_BY_MODE) } returns group
        return preferences(scope).also { it.observePreferences() }
    }
    @Test fun missingPreferencesUseCompactSafeWardDefaults() = runTest {
        observe(backgroundScope); runCurrent()
        assertFalse(state.value.patientDetailsExpanded); assertFalse(state.value.twoColumn)
        assertFalse(state.value.compactCards)
        assertEquals(GroupByMode.SUPERVISOR, state.value.groupByMode)
    }
    @Test fun malformedBooleanPreferencesDoNotEnableActions() = runTest {
        expanded.value = "TRUE"; columns.value = "1"; compact.value = "yes"
        observe(backgroundScope); runCurrent()
        assertFalse(state.value.patientDetailsExpanded); assertFalse(state.value.twoColumn)
        assertFalse(state.value.compactCards)
    }
    @Test fun eachObservedPreferenceUpdatesIndependently() = runTest {
        observe(backgroundScope); runCurrent()
        columns.value = "true"; runCurrent()
        assertTrue(state.value.twoColumn); assertFalse(state.value.compactCards)
        compact.value = "true"; expanded.value = "true"; runCurrent()
        assertTrue(state.value.compactCards); assertTrue(state.value.patientDetailsExpanded)
        columns.value = "false"; runCurrent()
        assertFalse(state.value.twoColumn); assertTrue(state.value.compactCards)
    }
    @Test fun groupingPreferenceRegroupsCurrentPatientsWithoutLosingClinicalContent() = runTest {
        val patient = Fixtures.patient(labs = "نتيجة", responsibleResidentId = "tree")
        state.value = state.value.copy(patients = listOf(patient), doctors = listOf(Fixtures.doctor("tree", fullName = "أرز")))
        observe(backgroundScope); runCurrent(); group.value = "RESIDENT"; runCurrent()
        assertEquals(GroupByMode.RESIDENT, state.value.groupByMode)
        assertEquals("tree", state.value.groupedPatients.single().key)
        assertEquals(listOf(patient), state.value.groupedPatients.single().patients)
    }
    @Test fun disablingGroupingClearsSupplementaryGroupsWithoutClearingWard() = runTest {
        val patient = Fixtures.patient(); state.value = state.value.copy(patients = listOf(patient))
        observe(backgroundScope); runCurrent(); group.value = "NONE"; runCurrent()
        assertTrue(state.value.groupedPatients.isEmpty()); assertEquals(listOf(patient), state.value.patients)
    }
    @Test fun expansionTogglePersistsBothDirections() = runTest {
        val preferences = preferences(this)
        preferences.togglePatientDetails(); runCurrent(); assertTrue(state.value.patientDetailsExpanded)
        preferences.togglePatientDetails(); runCurrent(); assertFalse(state.value.patientDetailsExpanded)
        coVerifyOrder {
            settings.putBoolean(AppConstants.SETTING_PATIENT_DETAILS_EXPANDED, true)
            settings.putBoolean(AppConstants.SETTING_PATIENT_DETAILS_EXPANDED, false)
        }
    }
    @Test fun explicitGroupSelectionPersistsAndUsesCurrentRegistry() = runTest {
        val patient = Fixtures.patient(responsibleSpecialistId = "tree")
        state.value = state.value.copy(patients = listOf(patient), doctors = listOf(Fixtures.doctor("tree", fullName = "أرز")))
        preferences(this).setGroupByMode(GroupByMode.SUPERVISOR); runCurrent()
        coVerify { settings.put(AppConstants.SETTING_GROUP_BY_MODE, "SUPERVISOR") }
        assertEquals("أرز", state.value.groupedPatients.single().name)
    }
    @Test fun compactCardPreferenceDoesNotChangeClinicalContent() = runTest {
        val before = Fixtures.patient(labs = "نتيجة")
        state.value = state.value.copy(patients = listOf(before))
        preferences(this).setCompactCards(true); runCurrent()
        assertTrue(state.value.compactCards)
        assertEquals(listOf(before), state.value.patients)
        coVerify(exactly = 1) { settings.putBoolean(AppConstants.SETTING_PATIENT_COMPACT_DENSITY, true) }
    }
}
