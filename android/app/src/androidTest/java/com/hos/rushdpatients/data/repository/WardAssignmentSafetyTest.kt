package com.hos.rushdpatients.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.ClinicalRepositoryFixture
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WardAssignmentSafetyTest : ClinicalRepositoryFixture() {
    private fun ward() = WardMutationRepository(db, patients, doctors, shifts, audit, dispatchers)
    @Test fun wrongRoleAddIsRejectedBelowUi() = runBlocking<Unit> {
        seedShift("today", ShiftDate.current())
        rejected { ward().addPatient(Patient(id = "p", name = "شاي", responsibleSpecialistId = admin.id), "today") }
        assertNull(patients.getById("p"))
        assertTrue(db.auditDao().getRecent(10).isEmpty())
    }
    @Test fun roleChangedSinceEditorOpenedBlocksSave() = runBlocking<Unit> {
        seedShift("today", ShiftDate.current())
        ward().addPatient(Patient(id = "p", name = "شاي", responsibleResidentId = admin.id), "today")
        val before = requireNotNull(patients.getById("p"))
        doctors.upsert(admin.copy(clinicalRole = ClinicalRole.SUPERVISOR))
        settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
        rejected { ward().updatePatient(before.copy(labs = "new"), "today") }
        assertEquals(before, patients.getById("p"))
        assertTrue(db.auditDao().getForPatient("p").isEmpty())
        assertFalse(settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))
    }
    @Test fun deletedResponsibleDoctorPreventsRecycleRestore() = runBlocking<Unit> {
        seedShift("today", ShiftDate.current())
        patients.upsert(Patient(id = "p", name = "شاي", responsibleResidentId = admin.id, revision = 1), "today")
        val deleted = patients.softDelete("p", "today")
        doctors.softDelete(admin.id)
        rejected { ward().restorePatient(deleted, "today") }
        assertTrue(requireNotNull(patients.getById("p")).isDeleted)
        assertEquals(deleted.revision, patients.getById("p")?.revision)
    }
    @Test fun staleRolePreventsCopyIntoAnotherSection() = runBlocking<Unit> {
        seedShift("today", ShiftDate.current())
        patients.upsert(Patient(id = "old", name = "شاي", responsibleResidentId = admin.id), "shift-1")
        val source = requireNotNull(patients.getById("old"))
        doctors.upsert(admin.copy(clinicalRole = ClinicalRole.SUPERVISOR))
        val target = requireNotNull(shifts.getById("today"))
        rejected { ward().addPatient(source.copy(id = "new"), "today") }
        assertNull(patients.getById("new"))
        assertEquals(target, shifts.getById("today"))
    }
}
