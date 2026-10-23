package com.hos.rushdpatients.domain.export

import android.content.Context
import android.net.Uri
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.sort.*
import com.hos.rushdpatients.pdf.MediaStoreSaver
import com.hos.rushdpatients.sync.CsvCodec
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.util.DispatcherProvider
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PatientCsvExporterTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = mockk<Context>()
    private val shifts = mockk<ShiftRepository>()
    private val patients = mockk<PatientRepository>()
    private val doctors = mockk<DoctorRepository>()
    private val saver = mockk<MediaStoreSaver>()
    private val dispatchers = mockk<DispatcherProvider> { every { io } returns Dispatchers.Unconfined }
    private val uri = mockk<Uri>()
    private val shift = Fixtures.shift().copy(sortSpecJson = SortSpecCodec.encode(SortSpec.EMPTY))
    private var source: File? = null
    private var decoded: List<Patient> = emptyList()
    private fun exporter() = PatientCsvExporter(context, shifts, patients, doctors, saver, dispatchers)
    @Before fun seed() {
        every { context.cacheDir } returns temporary.root
        coEvery { shifts.getById(shift.id) } returns shift
        coEvery { patients.getForShift(shift.id) } returns listOf(Fixtures.patient())
        coEvery { doctors.getAllIncludingDeleted() } returns listOf(Fixtures.doctor())
        coEvery { saver.saveCsv(context, any(), any()) } coAnswers {
            source = secondArg<File>()
            assertTrue(source!!.isFile)
            decoded = CsvCodec.decode(source!!.readText()).patients
            uri
        }
    }
    private fun assertNoTemporaryFiles() = assertTrue(temporary.root.listFiles()!!.isEmpty())
    @Test fun missingCurrentShiftDoesNotCreateFile() = runBlocking<Unit> {
        coEvery { shifts.getByDate(any()) } returns null
        assertTrue(exporter().exportCurrentShift() is PatientCsvExporter.Result.Failure)
        coVerify(exactly = 0) { saver.saveCsv(any(), any(), any()) }
        assertNoTemporaryFiles()
    }
    @Test fun missingRequestedShiftDoesNotReadPatientsOrSave() = runBlocking<Unit> {
        coEvery { shifts.getById("missing") } returns null
        assertTrue(exporter().exportShift("missing") is PatientCsvExporter.Result.Failure)
        coVerify(exactly = 0) { patients.getForShift(any()) }
        coVerify(exactly = 0) { saver.saveCsv(any(), any(), any()) }
    }
    @Test fun emptyShiftDoesNotPublishEmptyCsv() = runBlocking<Unit> {
        coEvery { patients.getForShift(shift.id) } returns emptyList()
        assertTrue(exporter().exportShift(shift.id) is PatientCsvExporter.Result.Failure)
        coVerify(exactly = 0) { saver.saveCsv(any(), any(), any()) }
        assertNoTemporaryFiles()
    }
    @Test fun successReturnsProviderUriCountAndDatedFilenameThenDeletesPlaintext() = runBlocking<Unit> {
        val result = exporter().exportShift(shift.id) as PatientCsvExporter.Result.Success
        assertEquals(uri, result.uri)
        assertEquals(1, result.patientCount)
        assertTrue(result.fileName.startsWith("Rushd_Patients_${shift.date}_"))
        assertTrue(result.fileName.endsWith(".csv"))
        assertNotNull(source)
        assertFalse(source!!.exists())
        assertNoTemporaryFiles()
    }
    @Test fun csvPreservesArabicTasksBadgesAndClinicalFields() = runBlocking<Unit> {
        val patient = Fixtures.patient(name = "شاي \"نعناع\"", labs = "نتيجة\nثانية", followUp = "متابعة,مستمرة")
            .copy(tasks = listOf(Fixtures.task(), Fixtures.task("done", true)),
                badges = listOf(PatientBadge("ملاحظة تجريبية")), revision = 7)
        coEvery { patients.getForShift(shift.id) } returns listOf(patient)
        exporter().exportShift(shift.id)
        assertEquals(patient, decoded.single())
        assertNoTemporaryFiles()
    }
    @Test fun exportUsesPersistedOrderAndRenumbersWithoutMutatingSavedRows() = runBlocking<Unit> {
        val original = listOf(Fixtures.patient("b", sortOrder = 20), Fixtures.patient("a", sortOrder = 10))
        coEvery { patients.getForShift(shift.id) } returns original
        exporter().exportShift(shift.id)
        assertEquals(listOf("a", "b"), decoded.map { it.id })
        assertEquals(listOf(1, 2), decoded.map { it.sortOrder })
        assertEquals(listOf(20, 10), original.map { it.sortOrder })
    }
    @Test fun nullProviderResultIsFailureAndDeletesPlaintext() = runBlocking<Unit> {
        coEvery { saver.saveCsv(any(), any(), any()) } returns null
        assertTrue(exporter().exportShift(shift.id) is PatientCsvExporter.Result.Failure)
        assertNoTemporaryFiles()
    }
    @Test fun providerFailureIsReportedAndDeletesPlaintext() = runBlocking<Unit> {
        coEvery { saver.saveCsv(any(), any(), any()) } throws IOException("synthetic storage failure")
        val result = exporter().exportShift(shift.id) as PatientCsvExporter.Result.Failure
        assertEquals("synthetic storage failure", result.message)
        assertNoTemporaryFiles()
    }
    @Test fun cancellationPropagatesAndDeletesPlaintext() = runBlocking<Unit> {
        coEvery { saver.saveCsv(any(), any(), any()) } throws CancellationException("synthetic cancel")
        try { exporter().exportShift(shift.id); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
        assertNoTemporaryFiles()
    }
    @Test fun currentShiftExportUsesResolvedShiftIdentity() = runBlocking<Unit> {
        coEvery { shifts.getByDate(any()) } returns shift
        assertTrue(exporter().exportCurrentShift() is PatientCsvExporter.Result.Success)
        coVerify(exactly = 1) { shifts.getById(shift.id) }
        coVerify(exactly = 1) { patients.getForShift(shift.id) }
        assertNoTemporaryFiles()
    }
}
