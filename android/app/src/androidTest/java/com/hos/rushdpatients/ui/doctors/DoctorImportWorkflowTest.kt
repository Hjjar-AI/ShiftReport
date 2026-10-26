package com.hos.rushdpatients.ui.doctors

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.domain.doctor.DoctorCsvCodec
import com.hos.rushdpatients.domain.export.PendingExportStore
import com.hos.rushdpatients.domain.report.ReportBuilder
import com.hos.rushdpatients.domain.report.TextReportBuilder
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.testutil.SyncFixture
import com.hos.rushdpatients.ui.components.UiStrings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DoctorImportWorkflowTest : SyncFixture() {
    private val store = ViewModelStore()
    private val uris = mutableListOf<Uri>()
    private fun viewModel(): DoctorsViewModel {
        val reports = ReportBuilder(patients, doctors, shifts, TextReportBuilder(), db, dispatchers)
        val sync = SyncService(preferences, wire.client, wire.topics, shifts, patients, doctors, cursors,
            settings, db, audit, dispatchers, remote, doctorSync, reports, undo,
            com.hos.rushdpatients.sync.PatientPublicationStatus(shifts, patients, doctors, settings, dispatchers))
        lateinit var vm: DoctorsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = DoctorsViewModel(UiStrings(preferences), preferences, doctors, settings, mutations, audit,
                sync, authorizer, PendingExportStore(preferences, dispatchers), SavedStateHandle())
            store.put("import", vm)
        }
        return vm
    }
    private fun source(text: String) = Uri.parse("content://${androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.packageName}.debug.faultdocuments/${UUID.randomUUID()}").also { uri ->
        uris += uri
        preferences.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    }
    private suspend fun preview(vm: DoctorsViewModel, uri: Uri): DoctorsUiState {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.prepareDoctorImport(uri) }
        return withTimeout(10_000) { vm.state.first { !it.importing && (it.importPreview != null || it.importError != null) } }
    }
    @After fun closeWorkspace() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        uris.forEach { preferences.contentResolver.delete(it, null, null) }
    }
    @Test fun providerSelectedThirtyThreeRowCsvBuildsPreviewThenCommitsThroughProductionViewModel() = runBlocking<Unit> {
        val imported = (1..33).map { doctor("import-$it", "بلوط $it") }
        val vm = viewModel()
        val state = preview(vm, source("\uFEFF" + DoctorCsvCodec.encode(imported)))
        assertTrue("Synthetic provider import failed", state.importError == null)
        assertEquals(33, state.importPreview?.added?.size)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.confirmDoctorImport() }
        withTimeout(15_000) { vm.state.first { !it.importing && it.importPreview == null } }
        assertEquals(34, doctors.count())
        assertTrue(imported.all { doctors.getById(it.id) != null })
    }
    @Test fun revokedProviderPermissionShowsImportErrorAndPreservesRegistry() = runBlocking<Unit> {
        val before = doctors.getAll(); val vm = viewModel()
        val uri = Uri.parse("content://${androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.packageName}.debug.faultdocuments/${UUID.randomUUID()}?fault=denied").also(uris::add)
        val state = preview(vm, uri)
        assertNotNull(state.importError); assertNull(state.importPreview)
        assertFalse(state.importing); assertEquals(before, doctors.getAll())
    }
    @Test fun malformedCsvCannotCreatePartialImportPreview() = runBlocking<Unit> {
        val before = doctors.getAll()
        val state = preview(viewModel(), source("not a doctor CSV"))
        assertNotNull(state.importError); assertNull(state.importPreview)
        assertEquals(before, doctors.getAll())
    }
    @Test fun oversizedProviderInputIsRejectedBeforeRegistryMutation() = runBlocking<Unit> {
        val before = doctors.getAll()
        val state = preview(viewModel(), source("x".repeat(2_000_001)))
        assertNotNull(state.importError); assertFalse(state.importing)
        assertEquals(before, doctors.getAll())
    }
    @Test fun cancellingImportPreviewCannotCommitLaterCallback() = runBlocking<Unit> {
        val vm = viewModel(); val before = doctors.getAll()
        preview(vm, source(DoctorCsvCodec.encode(listOf(doctor("imported", "صنوبر")))))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.dismissDoctorImport(); vm.confirmDoctorImport() }
        assertEquals(before, doctors.getAll())
        assertNull(vm.state.value.importPreview)
    }
    @Test fun ownersOriginalPrivateCsvCommitsOnlyIntoIsolatedRegistry() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "verification-inputs/doctors-import.local.csv")
        assumeTrue("Optional owner CSV was not provisioned privately", file.isFile)
        val vm = viewModel()
        val state = preview(vm, Uri.fromFile(file))
        // Only counts/success enter public test output; the original file remains private.
        assertTrue("Original CSV import did not produce a preview", state.importError == null && state.importPreview != null)
        assertEquals(1, doctors.count())
        val expectedCount = requireNotNull(state.importPreview).activeCount
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.confirmDoctorImport() }
        withTimeout(15_000) { vm.state.first { !it.importing && it.importPreview == null } }
        assertEquals("Original CSV import count", expectedCount, doctors.getAll().count { !it.isDeleted })
    }
}
