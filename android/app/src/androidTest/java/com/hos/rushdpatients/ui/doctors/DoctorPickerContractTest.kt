package com.hos.rushdpatients.ui.doctors

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
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
import java.io.File

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
    private suspend fun ordinaryAdmin() {
        val actor = requireNotNull(client.doctors.getById(client.actor.id)).copy(isPermanentAdmin = false)
        client.doctors.upsert(actor)
        client.sessions.start(requireNotNull(client.sessions.current()).copy(role = actor.role))
    }
    private suspend fun revokeAdmin(deleted: Boolean = false) {
        val actor = requireNotNull(client.doctors.getById(client.actor.id))
        if (deleted) client.doctors.softDelete(actor.id)
        else client.doctors.upsert(actor.copy(rank = 0))
    }
    private fun assertNoRemoteMutation() {
        assertTrue(server.calls.none { it.second in setOf("sendDocument", "editMessageText", "sendMessage") })
    }
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

    @Test fun pickerResultAfterBackgroundPrivacyRestoresRegistrationAndOpenTools() = runBlocking<Unit> {
        val uri = source(); val picker = openPicker()
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText(label(R.string.ui_doctors_screen_015)).assertIsDisplayed()
        onMain { picker.deliver(uri) }; awaitPreview()
        assertNull(doctorVm.state.value.importError)
        assertNotNull(doctorVm.state.value.importPreview)
        assertNull(client.doctors.getById("oak"))
    }

    private fun rejectedImportAfterAuthorityChange(deleted: Boolean) = runBlocking<Unit> {
        ordinaryAdmin()
        val uri = source(); val picker = openPicker()
        onMain { picker.deliver(uri) }; awaitPreview()
        assertNotNull(doctorVm.state.value.importPreview)
        revokeAdmin(deleted)
        val before = client.doctors.getAllIncludingDeleted()
        val audit = client.audit.getRecent()
        compose.onNodeWithText(label(R.string.ui_doctors_screen_043)).performClick()
        compose.waitUntil(10000) { !doctorVm.state.value.importing && doctorVm.state.value.snackbar != null }
        assertEquals(before, client.doctors.getAllIncludingDeleted())
        assertEquals(audit, client.audit.getRecent())
        assertNull(client.doctors.getById("oak"))
        assertNoRemoteMutation()
    }

    @Test fun adminRevokedAfterImportPreviewCannotCommitFromOpenConfirmation() = rejectedImportAfterAuthorityChange(false)
    @Test fun actorDeletedAfterImportPreviewCannotCommitFromOpenConfirmation() = rejectedImportAfterAuthorityChange(true)

    @Test fun exportPickerResultAfterAdminRevocationCannotWriteStagedRegistry() = runBlocking<Unit> {
        ordinaryAdmin()
        val picker = openPicker()
        onMain { picker.deliver(null) }
        compose.onNodeWithText(label(R.string.ui_doctors_screen_016)).performScrollTo().performClick()
        compose.waitUntil(10000) { picker.launchedIntent?.action == Intent.ACTION_CREATE_DOCUMENT }
        val staging = File(client.context.noBackupFilesDir, "pending_document_exports")
        assertTrue(staging.listFiles().orEmpty().isNotEmpty())
        revokeAdmin()
        val audit = client.audit.getRecent()
        val uri = Uri.parse("content://com.hos.rushdpatients.debug.faultdocuments/document/${UUID.randomUUID()}")
            .also(sources::add)
        onMain { picker.deliver(uri) }
        compose.waitUntil(10000) { !doctorVm.state.value.exporting && doctorVm.state.value.snackbar != null }
        assertTrue(staging.listFiles().orEmpty().isEmpty())
        assertEquals(audit, client.audit.getRecent())
        val bytes = runCatching { client.context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        assertTrue(bytes == null || bytes.isEmpty())
        assertNoRemoteMutation()
    }
}
