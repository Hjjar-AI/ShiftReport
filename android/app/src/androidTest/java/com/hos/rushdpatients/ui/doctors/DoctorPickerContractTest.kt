package com.hos.rushdpatients.ui.doctors

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.doctor.DoctorCsvCodec
import com.hos.rushdpatients.testutil.PickerResultRegistry
import com.hos.rushdpatients.testutil.ProductionUiFixture
import com.hos.rushdpatients.ui.navigation.Routes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DoctorPickerContractTest : ProductionUiFixture() {
    @get:Rule val compose = createEmptyComposeRule()
    private val sources = mutableListOf<Uri>()
    private fun label(id: Int) = client.context.getString(id)
    private fun source(fault: String? = null): Uri {
        val uri = Uri.parse("content://com.hos.rushdpatients.debug.faultdocuments/document/${UUID.randomUUID()}")
        sources += uri
        val doctor = client.actor.copy(id = "oak", fullName = "د. بلوط شجرة", firstName = "بلوط", rank = 0,
            telegramId = 456, isPermanentAdmin = false, extraOptions = emptySet())
        client.context.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(("\uFEFF" + DoctorCsvCodec.encode(listOf(doctor))).toByteArray(Charsets.UTF_8)) }
        return if (fault == null) uri else uri.buildUpon().appendQueryParameter("fault", fault).build()
    }
    private fun openPicker(): PickerResultRegistry {
        val registry = PickerResultRegistry(client.context); pickerOwner = registry
        launch()
        compose.waitUntil(10000) { route == Routes.WARD }
        onMain { nav.navigate(Routes.DOCTORS) }
        compose.waitUntil(10000) { route == Routes.DOCTORS && !doctorVm.state.value.loading }
        compose.onNodeWithText(label(R.string.ui_doctor_registry_tools)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.ui_doctors_screen_015)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Intent.ACTION_OPEN_DOCUMENT, registry.launchedIntent?.action) }
        return registry
    }
    private fun awaitPreview() = compose.waitUntil(10000) {
        !doctorVm.state.value.importing && (doctorVm.state.value.importPreview != null || doctorVm.state.value.importError != null) }
    @org.junit.After fun deleteSyntheticSources() {
        sources.forEach { client.context.contentResolver.delete(it, null, null) }
    }
    @Test fun documentContractResultReachesProductionPreviewWithoutAutoCommit() = runBlocking<Unit> {
        val uri = source(); val picker = openPicker()
        onMain { picker.deliver(uri) }; awaitPreview()
        assertNull(doctorVm.state.value.importError)
        assertEquals(1, doctorVm.state.value.importPreview?.added?.size)
        assertNull(client.doctors.getById("oak"))
        compose.onNodeWithText(label(R.string.ui_doctors_screen_043)).assertIsEnabled()
    }
    @Test fun cancelledSystemContractPreservesRegistryAndShowsNoImportError() = runBlocking<Unit> {
        val before = client.doctors.getAll(); val picker = openPicker()
        onMain { picker.deliver(null) }
        compose.runOnIdle { assertNull(doctorVm.state.value.importError); assertNull(doctorVm.state.value.importPreview) }
        assertEquals(before, client.doctors.getAll())
    }
    @Test fun unavailableSelectedDocumentShowsErrorWithoutPartialRegistryMutation() = runBlocking<Unit> {
        val uri = source("unavailable"); val before = client.doctors.getAll(); val picker = openPicker()
        onMain { picker.deliver(uri) }; awaitPreview()
        assertNotNull(doctorVm.state.value.importError); assertNull(doctorVm.state.value.importPreview)
        assertEquals(before, client.doctors.getAll())
    }
    @Test fun revokedSelectedDocumentGrantShowsErrorWithoutPartialRegistryMutation() = runBlocking<Unit> {
        val uri = source("denied"); val before = client.doctors.getAll(); val picker = openPicker()
        onMain { picker.deliver(uri) }; awaitPreview()
        assertNotNull(doctorVm.state.value.importError); assertEquals(before, client.doctors.getAll())
    }
    @Test fun delayedPickerResultAfterActivityRecreationStillUsesIsolatedViewModel() = runBlocking<Unit> {
        val uri = source(); val picker = openPicker()
        recreateActivity()
        compose.waitUntil(10000) { route == Routes.DOCTORS && !doctorVm.state.value.loading }
        onMain { picker.deliver(uri) }; awaitPreview()
        assertNull(doctorVm.state.value.importError)
        assertNotNull(doctorVm.state.value.importPreview)
        assertNull(client.doctors.getById("oak"))
    }
}
