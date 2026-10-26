package com.hos.rushdpatients.domain.report

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.testutil.IsolatedStorageContext
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class ReportSnapshotConsistencyTest {
    private fun exercise(assignments: Boolean, cancel: Boolean = false) = runBlocking<Unit> {
        val context = IsolatedStorageContext()
        val armed = AtomicBoolean(false)
        val insideTransaction = AtomicBoolean(false)
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var db: AppDatabase
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback(RoomDatabase.QueryCallback { sql, _ ->
                val normalized = sql.replace("`", "").trim().uppercase(java.util.Locale.ROOT)
                if (normalized.startsWith("SELECT * FROM SHIFTS WHERE ID") && armed.compareAndSet(true, false)) {
                    insideTransaction.set(db.inTransaction())
                    reading.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "Report read barrier timed out" }
                }
            }, Executor { it.run() }).build()
        try {
            val dispatchers = DefaultDispatcherProvider()
            val patients = PatientRepository(db.patientDao(), db, dispatchers)
            val settings = SettingsRepository(db.settingDao(), dispatchers)
            val doctors = DoctorRepository(db.doctorDao(), patients, db, settings, dispatchers)
            val shifts = ShiftRepository(db.shiftDao(), dispatchers)
            val first = Doctor(id = "cedar", fullName = "أرز", firstName = "أرز", lastName = "شجرة", gender = Gender.MALE)
            val second = first.copy(id = "oak", fullName = "بلوط", firstName = "بلوط")
            doctors.upsertAll(listOf(first, second))
            val shift = Shift(id = "snapshot", date = LocalDate.of(2026, 1, 1), doctorIds = listOf(first.id))
            shifts.upsert(shift)
            val patient = Patient(id = "tea", name = "شاي", labs = "قبل", responsibleResidentId = first.id)
            patients.upsert(patient, shift.id)
            val builder = ReportBuilder(patients, doctors, shifts, TextReportBuilder(), db, dispatchers)
            withTimeout(15000) {
                armed.set(true)
                val report = async(Dispatchers.Default) {
                    if (assignments) builder.buildAssignments(shift.id) else builder.build(shift.id)
                }
                try {
                    assertTrue("Report did not reach the transaction read barrier",
                        withContext(Dispatchers.IO) { reading.await(5, TimeUnit.SECONDS) })
                    assertTrue("Report read started outside a Room transaction", insideTransaction.get())
                    val attempted = CompletableDeferred<Unit>()
                    val writer = async(Dispatchers.Default) {
                        attempted.complete(Unit)
                        db.withTransaction {
                            shifts.upsert(shift.copy(doctorIds = listOf(second.id), revision = 1))
                            patients.upsert(patient.copy(labs = "بعد", responsibleResidentId = second.id, revision = 1), shift.id)
                        }
                    }
                    attempted.await()
                    assertFalse("Writer committed while the report transaction was held", writer.isCompleted)
                    if (cancel) report.cancel()
                    release.countDown()
                    if (cancel) report.cancelAndJoin() else {
                        val snapshot = report.await()
                        assertEquals(listOf(first.id), snapshot.shift.doctorIds)
                        assertEquals(listOf(first.id), snapshot.doctors.map { it.id })
                        assertEquals("قبل", snapshot.patients.single().labs)
                        assertEquals(first.id, snapshot.patients.single().responsibleResidentId)
                        assertEquals(1, snapshot.summary.patientCount)
                        assertFalse(snapshot.textChunks.joinToString("\n").contains("بعد"))
                    }
                    writer.await()
                    val next = builder.build(shift.id)
                    assertEquals(listOf(second.id), next.shift.doctorIds)
                    assertEquals("بعد", next.patients.single().labs)
                    assertEquals(second.id, next.patients.single().responsibleResidentId)
                } finally {
                    release.countDown()
                }
            }
        } finally {
            release.countDown()
            db.close()
            context.cleanUp()
        }
    }

    @Test fun concurrentRosterAndClinicalChangeCannotMixReportSnapshots() = exercise(assignments = false)
    @Test fun assignmentsExportUsesSameConsistentSnapshotBoundary() = exercise(assignments = true)
    @Test fun cancelledReportReleasesTransactionForPendingClinicalWriter() = exercise(assignments = false, cancel = true)
}
