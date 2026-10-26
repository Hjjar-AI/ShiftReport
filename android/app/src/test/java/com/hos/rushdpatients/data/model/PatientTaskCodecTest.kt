package com.hos.rushdpatients.data.model

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientTaskCodecTest {
    @Test fun `round trip retains all task fields including completion identity`() {
        val tasks = listOf(Fixtures.task().copy(description = "فحص، \"تجريبي\"\nسطر",
            ownerDoctorId = "doc", ownerName = "أرز", priority = TaskPriority.HIGH,
            dueAtEpochMillis = 100, dueShiftDate = "2026-01-01"), Fixtures.task("done", true))
        assertEquals(tasks, PatientTaskCodec.decode(PatientTaskCodec.encode(tasks)))
        assertEquals(emptyList<PatientTask>(), PatientTaskCodec.decode(" "))
    }
    @Test fun `decode rejects corrupt JSON and malformed checklist records`() {
        assertThrows(Exception::class.java) { PatientTaskCodec.decode("broken") }
        assertThrows(IllegalArgumentException::class.java) {
            PatientTaskCodec.decode(PatientTaskCodec.encode(listOf(Fixtures.task().copy(description = " "))))
        }
    }
}
