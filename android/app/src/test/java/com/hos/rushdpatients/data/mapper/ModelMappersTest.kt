package com.hos.rushdpatients.data.mapper

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ModelMappersTest {
    @Test fun `patient persistence retains clinical fields badges tasks audit revision and deletion`() {
        val patient = Fixtures.patient().copy(tasks = listOf(Fixtures.task(), Fixtures.task("done", true)),
            badges = listOf(PatientBadge("تنبيه؛ متعدد", PatientBadgePriority.HIGH)), isPriority = true,
            responsibleResidentId = "r", responsibleSpecialistId = "s", revision = 37,
            lastEditedByDoctorId = "editor", lastEditedByName = "أرز", deletedAt = Fixtures.timestamp)
        val entity = PatientMapper.toEntity(patient, "shift")
        assertEquals("shift", entity.shiftId)
        assertEquals(patient, PatientMapper.fromEntity(entity))
    }
    @Test fun `doctor persistence retains role routing local options team and administrative identity`() {
        val doctor = Fixtures.doctor(rank = 1, isPermanentAdmin = true).copy(
            clinicalRole = ClinicalRole.SUPERVISOR, supervisorGroupChatId = -100,
            extraOptions = setOf("pin:synthetic", "teamSupervisor:s"), deletedAt = Fixtures.timestamp)
        assertEquals(doctor, DoctorMapper.fromEntity(DoctorMapper.toEntity(doctor)))
    }
    @Test fun `shift persistence retains unlimited roster order and publication lineage`() {
        val shift = Fixtures.shift(doctorIds = (1..30).map { "d$it" }, multiDoctorMode = true).copy(
            revision = 12, snapshotId = "current", baseSnapshotId = "base", publishedByDeviceId = "device",
            reportMessageId = 1, pdfMessageId = 2, csvMessageId = 3, sentAt = Fixtures.timestamp,
            sortSpecJson = com.hos.rushdpatients.domain.sort.SortSpecCodec.encode(com.hos.rushdpatients.domain.sort.SortSpec.WORKBOOK))
        assertEquals(shift, ShiftMapper.fromEntity(ShiftMapper.toEntity(shift)))
        val empty = Fixtures.shift(doctorIds = emptyList())
        assertEquals(empty, ShiftMapper.fromEntity(ShiftMapper.toEntity(empty)))
    }
}
