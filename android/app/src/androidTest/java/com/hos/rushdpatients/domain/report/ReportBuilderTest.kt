package com.hos.rushdpatients.domain.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.domain.sort.SortDirection
import com.hos.rushdpatients.domain.sort.SortField
import com.hos.rushdpatients.domain.sort.SortLevel
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.network.telegram.Markdown
import com.hos.rushdpatients.testutil.ClinicalRepositoryFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room reads and text rendering; no publication or installed clinical storage. */
@RunWith(AndroidJUnit4::class)
class ReportBuilderTest : ClinicalRepositoryFixture() {
    private fun builder() = ReportBuilder(patients, doctors, shifts, TextReportBuilder(), db, dispatchers)

    private suspend fun roster(ids: List<String> = listOf(admin.id)) {
        shifts.upsert(requireNotNull(shifts.getById("shift-1")).copy(doctorIds = ids))
    }

    @Test fun missingShiftRejectsBothExportPaths() = runBlocking<Unit> {
        assertTrue(rejected { builder().build("absent") } is IllegalStateException)
        assertTrue(rejected { builder().buildAssignments("absent") } is IllegalStateException)
    }

    @Test fun clinicalReportRequiresRosterEvenWhenWardIsEmpty() = runBlocking<Unit> {
        assertTrue(rejected { builder().build("shift-1") } is IllegalArgumentException)
    }

    @Test fun unresolvedRosterDoesNotProduceClinicalReport() = runBlocking<Unit> {
        roster(listOf("missing-doctor"))
        assertTrue(rejected { builder().build("shift-1") } is IllegalArgumentException)
    }

    @Test fun assignmentsExportAllowsEmptyRosterAndRetainsPatients() = runBlocking<Unit> {
        patients.upsert(Patient(id = "tea", name = "شاي"), "shift-1")
        val report = builder().buildAssignments("shift-1")
        assertEquals(listOf("tea"), report.patients.map { it.id })
        assertTrue(report.doctors.isEmpty())
        assertFalse(report.summary.hasDoctors)
        assertEquals(1, report.summary.patientCount)
    }

    @Test fun emptyWardWithRosterHasZeroTotalsAndOneSummary() = runBlocking<Unit> {
        roster()
        val report = builder().build("shift-1")
        assertEquals(ReportSummary(0, 0, 0, report.shift.date, listOf(admin)), report.summary)
        assertTrue(report.patients.isEmpty())
        assertEquals(1, Regex("عدد المرضى:").findAll(report.textChunks.joinToString("\n")).count())
    }

    @Test fun totalsExcludeDeletedPatientsAndOtherShiftsAndIncludeDualDiagnosis() = runBlocking<Unit> {
        roster()
        patients.upsertAll(listOf(
            Patient(id = "tea", name = "شاي", diagnosisType = DiagnosisType.PSYCHIATRIC, hasCompanion = true),
            Patient(id = "coffee", name = "قهوة", diagnosisType = DiagnosisType.ADDICTION),
            Patient(id = "cocoa", name = "كاكاو", diagnosisType = DiagnosisType.DUAL, hasCompanion = true),
            Patient(id = "deleted", name = "سحلب", hasCompanion = true)
        ), "shift-1")
        patients.softDelete("deleted")
        patients.upsert(Patient(id = "other", name = "متة", hasCompanion = true), "shift-2")
        val report = builder().build("shift-1")
        assertEquals(setOf("tea", "coffee", "cocoa"), report.patients.map { it.id }.toSet())
        assertEquals(3, report.summary.patientCount)
        assertEquals(2, report.summary.psychoCount)
        assertEquals(2, report.summary.escortCount)
        val text = report.textChunks.joinToString("\n")
        assertFalse(text.contains("سحلب"))
        assertFalse(text.contains("متة"))
        assertTrue(text.contains("عدد المرضى: 3"))
    }

    @Test fun longRosterFollowsShiftOrderRatherThanRegistryOrder() = runBlocking<Unit> {
        val rows = (1..40).map { doctor("tree-$it", "أرز $it") }
        doctors.upsertAll(rows)
        val ids = rows.map { it.id }.reversed()
        roster(ids)
        val report = builder().build("shift-1")
        assertEquals(ids, report.doctors.map { it.id })
        assertEquals(ids, report.summary.doctors.map { it.id })
        val text = report.textChunks.joinToString("\n")
        val positions = rows.reversed().map { text.indexOf(Markdown.escape(it.fullName)) }
        assertTrue(positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test fun historicalAssignmentsRetainDeletedDoctorNames() = runBlocking<Unit> {
        roster()
        val historical = doctor("historical", "بلوط")
        doctors.upsert(historical)
        patients.upsert(Patient(id = "tea", name = "شاي", responsibleResidentId = historical.id), "shift-1")
        doctors.softDelete(historical.id)
        val report = builder().build("shift-1")
        assertEquals(historical.fullName, report.doctorNames[historical.id])
        assertTrue(report.registryDoctors.single { it.id == historical.id }.isDeleted)
        assertTrue(report.textChunks.joinToString("\n").contains(Markdown.escape(historical.fullName)))
        assertEquals(listOf(admin.id), report.doctors.map { it.id })
    }

    @Test fun configuredSortKeepsPriorityFirstAndRenumbersOnlyReportRows() = runBlocking<Unit> {
        roster()
        val shift = requireNotNull(shifts.getById("shift-1"))
        shifts.upsert(shift.copy(sortSpecJson = SortSpecCodec.encode(
            SortSpec(listOf(SortLevel(SortField.NAME, SortDirection.DESC))))))
        patients.upsertAll(listOf(
            Patient(id = "a", name = "A شاي", sortOrder = 90),
            Patient(id = "b", name = "B قهوة", sortOrder = 20),
            Patient(id = "priority", name = "A كاكاو", isPriority = true, sortOrder = 50)
        ), "shift-1")
        val report = builder().build("shift-1")
        assertEquals(listOf("priority", "b", "a"), report.patients.map { it.id })
        assertEquals(listOf(1, 2, 3), report.patients.map { it.sortOrder })
        val text = report.textChunks.joinToString("\n")
        assertTrue(text.indexOf("A كاكاو") < text.indexOf("B قهوة"))
        assertTrue(text.indexOf("B قهوة") < text.indexOf("A شاي"))
        assertEquals(90, patients.getById("a")?.sortOrder)
        assertEquals(20, patients.getById("b")?.sortOrder)
        assertEquals(50, patients.getById("priority")?.sortOrder)
    }

    @Test fun explicitManualSortUsesStoredPositions() = runBlocking<Unit> {
        roster()
        shifts.upsert(requireNotNull(shifts.getById("shift-1")).copy(
            sortSpecJson = SortSpecCodec.encode(SortSpec.EMPTY)))
        patients.upsertAll(listOf(Patient(id = "a", name = "A شاي", sortOrder = 8),
            Patient(id = "z", name = "Z قهوة", sortOrder = 2)), "shift-1")
        val report = builder().build("shift-1")
        assertEquals(listOf("z", "a"), report.patients.map { it.id })
        assertEquals(listOf(1, 2), report.patients.map { it.sortOrder })
    }

    @Test fun persistedClinicalFieldsAndBothTaskStatesReachRenderedReport() = runBlocking<Unit> {
        roster()
        val row = Patient(id = "tea", name = "شاي", initialDiagnosis = "تشخيص [تجريبي]",
            treatmentPlan = "خطة\nسطر ثان", labs = "Na=140", followUp = "متابعة *تجريبية*",
            tasks = listOf(PatientTask("pending", "فحص معلق"),
                PatientTask("done", "فحص مكتمل", done = true, completedByDoctorId = admin.id,
                    completedByName = admin.fullName, completedAtEpochMillis = 1000)))
        patients.upsert(row, "shift-1")
        val report = builder().build("shift-1")
        assertEquals(row.tasks, report.patients.single().tasks)
        val text = report.textChunks.joinToString("\n")
        listOf(row.initialDiagnosis, row.treatmentPlan, row.labs, row.followUp,
            "فحص معلق", "فحص مكتمل").forEach { assertTrue(text.contains(Markdown.escape(it))) }
    }

    @Test fun buildingBothExportsDoesNotMutateClinicalOrPublicationState() = runBlocking<Unit> {
        roster()
        patients.upsert(Patient(id = "tea", name = "شاي", sortOrder = 17, revision = 4), "shift-1")
        settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true)
        val beforeShift = shifts.getById("shift-1")
        val beforePatients = patients.getForShift("shift-1")
        val beforeDoctors = doctors.getAllIncludingDeleted()
        val beforeAudit = db.auditDao().getRecent(100)
        builder().build("shift-1")
        builder().buildAssignments("shift-1")
        assertEquals(beforeShift, shifts.getById("shift-1"))
        assertEquals(beforePatients, patients.getForShift("shift-1"))
        assertEquals(beforeDoctors, doctors.getAllIncludingDeleted())
        assertEquals(beforeAudit, db.auditDao().getRecent(100))
        assertTrue(settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false))
    }
}
