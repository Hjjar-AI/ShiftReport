package com.hos.rushdpatients.data.mapper

import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.Fixtures
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PatientLabGroupsMapperTest {
    @Test fun `persisted panels retain identity dates order and clinical bookkeeping`() {
        val patient = Fixtures.patientWithLabs().copy(revision = 17, sortOrder = 9,
            deletedAt = Instant.parse("2026-01-01T12:00:00Z"), assignmentChangedOn = LocalDate.of(2026, 1, 1),
            tasks = listOf(Fixtures.task()), isPriority = true)
        val entity = PatientMapper.toEntity(patient, "ward")
        assertEquals("ward", entity.shiftId)
        assertEquals(patient, PatientMapper.fromEntity(entity))
        assertEquals(patient.labText, entity.labs)
    }
    @Test fun `one ungrouped field remains readable with stable fallback identity`() {
        val patient = Fixtures.patient(labs = "  Hb=12\r\nNa=140  ")
        val restored = PatientMapper.fromEntity(PatientMapper.toEntity(patient, "ward"))
        assertEquals(patient, restored)
        assertEquals(patient.labs, restored.effectiveLabGroups.single().results)
        assertNull(restored.effectiveLabGroups.single().date)
        assertEquals("${patient.id}-labs", restored.effectiveLabGroups.single().id)
    }
    @Test fun `corrupt stored json and inconsistent projection are never silently accepted`() {
        val entity = PatientMapper.toEntity(Fixtures.patientWithLabs(), "ward")
        listOf(entity.copy(labGroupsJson = "{"), entity.copy(labs = "different retained content"),
            entity.copy(labGroupsJson = "null")).forEach { broken ->
            assertThrows(RuntimeException::class.java) { PatientMapper.fromEntity(broken) }
        }
        assertEquals(Fixtures.patientWithLabs(), PatientMapper.fromEntity(entity))
    }
    @Test fun `clearing the final panel cannot resurrect its old plain text`() {
        val patient = Fixtures.patientWithLabs().copy(labGroups = emptyList(), labs = "")
        val restored = PatientMapper.fromEntity(PatientMapper.toEntity(patient, "ward"))
        assertEquals("", restored.labText)
        assertTrue(restored.effectiveLabGroups.isEmpty())
    }
}
