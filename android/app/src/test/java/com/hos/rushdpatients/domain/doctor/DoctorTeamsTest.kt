package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DoctorTeamsTest {
    private val supervisor = Fixtures.doctor(id = "s").copy(clinicalRole = ClinicalRole.SUPERVISOR)
    private val resident = DoctorTeams.assign(Fixtures.doctor(id = "r"), "s")
    @Test fun `assign replaces old team while preserving unrelated options`() {
        val doctor = resident.copy(extraOptions = resident.extraOptions + "displayOption")
        assertEquals(setOf("displayOption", "teamSupervisor:new"), DoctorTeams.assign(doctor, "new").extraOptions)
        assertEquals(setOf("displayOption"), DoctorTeams.assign(doctor, null).extraOptions)
        assertThrows(IllegalArgumentException::class.java) { DoctorTeams.assign(doctor, "bad,id") }
    }
    @Test fun `membership requires a distinct active supervisor and resident role`() {
        DoctorTeams.validate(listOf(resident, supervisor))
        listOf(listOf(resident), listOf(resident, supervisor.copy(deletedAt = Instant.EPOCH)),
            listOf(resident, supervisor.copy(clinicalRole = ClinicalRole.RESIDENT)),
            listOf(resident.copy(clinicalRole = ClinicalRole.SUPERVISOR), supervisor),
            listOf(DoctorTeams.assign(resident, "r")),
            listOf(resident.copy(extraOptions = setOf("teamSupervisor:s", "teamSupervisor:x")), supervisor)
        ).forEach { doctors -> assertThrows(IllegalArgumentException::class.java) { DoctorTeams.validate(doctors) } }
    }
    @Test fun `patient cover does not reassign team and aliases remap supervisor identity`() {
        assertTrue(DoctorTeams.isCrossTeamCover(resident, "other"))
        assertFalse(DoctorTeams.isCrossTeamCover(resident, "s"))
        assertFalse(DoctorTeams.isCrossTeamCover(resident, null))
        assertEquals("s", resident.teamSupervisorId)
        assertEquals("new", DoctorTeams.remap(listOf(resident), mapOf("s" to "new")).single().teamSupervisorId)
        assertEquals(setOf("local", "teamSupervisor:s"), DoctorTeams.sharedOptions(
            setOf("local", "teamSupervisor:old"), resident))
    }
}
