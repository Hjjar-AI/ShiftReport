package com.hos.rushdpatients.data.maintenance

import android.content.Context
import androidx.room.withTransaction
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.db.dao.PatientDao
import com.hos.rushdpatients.data.db.dao.SettingDao
import io.mockk.*
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecycleBinCleanupWorkerTest {
    private val root = Files.createTempDirectory("recycle_worker_").toFile()
    private val file = java.io.File(root, "synthetic.db")
    private val context = mockk<Context>(relaxed = true) {
        every { getDatabasePath(AppConstants.DATABASE_NAME) } returns file
    }
    private val patients = mockk<PatientDao>(relaxed = true)
    private val settings = mockk<SettingDao>(relaxed = true)
    private val database = mockk<AppDatabase> {
        every { patientDao() } returns patients
        every { settingDao() } returns settings
    }
    @Before fun setup() {
        every { context.applicationContext } returns context
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(context) } returns database
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction<Any?>(any()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }
    }
    @After fun cleanup() {
        unmockkObject(AppDatabase.Companion)
        unmockkStatic("androidx.room.RoomDatabaseKt")
        root.deleteRecursively()
    }
    private suspend fun runWorker() = RecycleBinCleanupWorker(context, mockk<WorkerParameters>(relaxed = true)).doWork()
    @Test fun `unused installation never creates or opens a database`() = runBlocking<Unit> {
        assertEquals(ListenableWorker.Result.success(), runWorker())
        verify(exactly = 0) { AppDatabase.getInstance(any()) }
        assertFalse(file.exists())
    }
    @Test fun `worker uses seven day cutoff and cleans orphan drafts after purge`() = runBlocking<Unit> {
        file.createNewFile()
        val week = java.util.concurrent.TimeUnit.DAYS.toMillis(7)
        val earliest = System.currentTimeMillis() - week
        assertEquals(ListenableWorker.Result.success(), runWorker())
        val latest = System.currentTimeMillis() - week
        coVerify(exactly = 1) { database.withTransaction<Any?>(any()) }
        coVerifyOrder {
            patients.purgeSoftDeletedBefore(match { it in earliest..latest })
            settings.deleteOrphanedPatientDrafts(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX)
        }
    }
    @Test fun `database failure requests retry without cleaning drafts`() = runBlocking<Unit> {
        file.createNewFile()
        coEvery { patients.purgeSoftDeletedBefore(any()) } throws java.io.IOException("synthetic database failure")
        assertEquals(ListenableWorker.Result.retry(), runWorker())
        coVerify(exactly = 0) { settings.deleteOrphanedPatientDrafts(any()) }
    }
    @Test fun `cancellation propagates instead of requesting retry`() = runBlocking<Unit> {
        file.createNewFile()
        coEvery { patients.purgeSoftDeletedBefore(any()) } throws CancellationException("synthetic cancellation")
        try { runWorker(); fail("Cancellation swallowed") } catch (_: CancellationException) { }
        coVerify(exactly = 0) { settings.deleteOrphanedPatientDrafts(any()) }
    }
    @Test fun `repeated startup scheduling keeps one unique daily work identity`() {
        val manager = mockk<androidx.work.WorkManager>(relaxed = true)
        mockkStatic(androidx.work.WorkManager::class)
        try {
            every { androidx.work.WorkManager.getInstance(context) } returns manager
            repeat(3) { RecycleBinCleanupWorker.schedule(context) }
            verify(exactly = 3) {
                manager.enqueueUniquePeriodicWork("patient_recycle_bin_cleanup",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    match<androidx.work.PeriodicWorkRequest> {
                        it.workSpec.intervalDuration == java.util.concurrent.TimeUnit.DAYS.toMillis(1)
                    })
            }
        } finally { unmockkStatic(androidx.work.WorkManager::class) }
    }

}
