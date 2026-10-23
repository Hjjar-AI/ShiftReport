package com.hos.rushdpatients.sync

import android.content.Context
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.report.*
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.dto.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class PatientPublicationCoordinatorTest {
    private val directory = Files.createTempDirectory("publication_test_").toFile()
    private val context = mockk<Context> { every { cacheDir } returns directory }
    private val telegram = mockk<TelegramClient>()
    private val topics = mockk<Topics> { every { chatId } returns -100; every { threadId(Topic.CSV) } returns 3 }
    private val shifts = mockk<ShiftRepository>(relaxed = true)
    private val patients = mockk<PatientRepository>()
    private val doctors = mockk<DoctorRepository>()
    private val cursors = mockk<SyncStateRepository>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val audit = mockk<AuditRepository>(relaxed = true)
    private val remote = mockk<RemoteSyncStateStore>()
    private val doctorSync = mockk<DoctorSyncCoordinator>(relaxed = true)
    private val reports = mockk<ReportBuilder>()
    private val dispatchers = mockk<DispatcherProvider> { every { io } returns Dispatchers.Unconfined }
    private val coordinator = PatientPublicationCoordinator(Mutex(), context, telegram, topics, shifts, patients,
        doctors, cursors, settings, audit, dispatchers, remote, doctorSync, reports)
    private val shift = Fixtures.shift(date = ShiftDate.current())
    private val actor = Fixtures.doctor(telegramId = 123)
    private var rows = listOf(Fixtures.patient())
    private var source = SyncState(doctorsFileId = "registry", doctorsMessageId = 1)
    private fun built() = BuiltReport(shift, rows, listOf(actor), mapOf(actor.id to actor.fullName),
        ReportSummary(rows.size, rows.size, 0, shift.date, listOf(actor)), listOf("synthetic reviewed report"))
    @Before fun setup() {
        coEvery { shifts.getById(shift.id) } returns shift
        coEvery { shifts.getRecent(12) } returns listOf(shift)
        coEvery { patients.getForShift(shift.id) } answers { rows }
        coEvery { doctors.getAllIncludingDeleted() } returns listOf(actor)
        coEvery { doctors.pendingSharedChangesSummary() } returns null
        coEvery { settings.get(AppConstants.SETTING_DEVICE_ID) } returns "synthetic-device"
        coEvery { remote.readState() } answers { source }
        coEvery { remote.updateState(any()) } coAnswers {
            firstArg<(SyncState) -> SyncState>().invoke(source).also { source = it }
        }
        coEvery { doctorSync.requirePublishedClinician(any()) } returns actor
        coEvery { reports.build(shift.id) } answers { built() }
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } returns
            TgMessage(10, date = 100, document = TgDocument(fileId = "uploaded"))
    }
    @After fun cleanup() { unmockkObject(ShiftDate); directory.deleteRecursively() }
    @Test fun `reviewed upload publishes exact local bundle and records confirmed cursor`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        var csv = ""
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } answers {
            csv = secondArg<File>().readText()
            TgMessage(10, date = 100, document = TgDocument(fileId = "uploaded"))
        }
        val result = coordinator.uploadReviewedCsv(review).getOrThrow()
        assertEquals(10L, result.messageId)
        assertEquals(rows.single().name, CsvBundleCodec.decode(csv).single().patients.single().name)
        assertEquals(10L, source.csvMessageId)
        assertEquals(1, source.publicationJournal.size)
        coVerify { settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false) }
        coVerify { cursors.update(any(), any(), 10, "uploaded") }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun `clinical edits after review block all upload`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        rows = listOf(rows.single().copy(labs = "changed"))
        assertTrue(coordinator.uploadReviewedCsv(review).isFailure)
        coVerify(exactly = 0) { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `changed published source or actor requires another review`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        source = source.copy(doctorsMessageId = 2)
        assertTrue(coordinator.uploadReviewedCsv(review).isFailure)
        source = review.source
        coEvery { doctorSync.requirePublishedClinician(any()) } returns actor.copy(id = "other")
        assertTrue(coordinator.uploadReviewedCsv(review).isFailure)
        coVerify(exactly = 0) { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `interrupted upload preserves pending state and removes temporary file`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } throws IOException("interrupted")
        assertTrue(coordinator.uploadReviewedCsv(review).isFailure)
        coVerify(exactly = 0) { remote.updateState(any()) }
        coVerify(exactly = 0) { settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false) }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun `edit racing upload prevents pinning reviewed content`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } answers {
            rows = listOf(rows.single().copy(followUp = "local edit during upload"))
            TgMessage(10, document = TgDocument(fileId = "uploaded"))
        }
        assertTrue(coordinator.uploadReviewedCsv(review).isFailure)
        coVerify(exactly = 0) { remote.updateState(any()) }
        coVerify(exactly = 0) { settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false) }
    }
    @Test fun `pin update failure cannot mark upload locally confirmed`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        coEvery { remote.updateState(any()) } throws IOException("pin failure")
        assertTrue(coordinator.uploadReviewedCsv(review).isFailure)
        coVerify(exactly = 0) { cursors.update(any(), any(), any(), any()) }
        coVerify(exactly = 0) { shifts.updatePublicationMetadata(any(), any()) }
    }
    @Test fun `archived shift cannot be published`() = runBlocking<Unit> {
        coEvery { shifts.getById(shift.id) } returns shift.copy(date = ShiftDate.current().minusDays(1))
        assertTrue(coordinator.uploadCsv(shift.id).isFailure)
        coVerify(exactly = 0) { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `publication cancellation propagates with temporary cleanup`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { coordinator.uploadReviewedCsv(review) } }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun `shift boundary changing after review blocks upload before transport`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        mockkObject(ShiftDate)
        every { ShiftDate.current(any()) } returns shift.date.plusDays(1)
        assertTrue(coordinator.uploadReviewedCsv(review).isFailure)
        coVerify(exactly = 0) { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `clock correction changing active shift during upload blocks index publication`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        mockkObject(ShiftDate)
        var current = shift.date
        every { ShiftDate.current(any()) } answers { current }
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } answers {
            current = shift.date.minusDays(1)
            TgMessage(10, document = TgDocument(fileId = "uploaded"))
        }
        assertTrue(coordinator.uploadReviewedCsv(review).isFailure)
        coVerify(exactly = 0) { remote.updateState(any()) }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun `advance of active shift during upload requires renewed clinical review`() = runBlocking<Unit> {
        val review = coordinator.prepareCsvPublication(shift.id)
        mockkObject(ShiftDate)
        var current = shift.date
        every { ShiftDate.current(any()) } answers { current }
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } answers {
            current = shift.date.plusDays(1)
            TgMessage(10, document = TgDocument(fileId = "uploaded"))
        }
        assertTrue(coordinator.uploadReviewedCsv(review).isFailure)
        coVerify(exactly = 0) { cursors.update(any(), any(), any(), any()) }
        coVerify(exactly = 0) { shifts.updatePublicationMetadata(any(), any()) }
    }
}
