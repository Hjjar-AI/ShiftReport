package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DoctorCsvCodecTest {
    private fun decode(vararg doctors: Doctor) = DoctorCsvCodec.decode(DoctorCsvCodec.encode(doctors.toList()))
    @Test fun `portable round trip retains team and strips local secrets`() {
        val doctor = Fixtures.doctor().copy(extraOptions = setOf("pin:synthetic", "teamSupervisor:s", "display"))
        val decoded = decode(doctor)
        assertEquals(doctor.copy(extraOptions = setOf("teamSupervisor:s", "display")), decoded.doctors.single())
        assertEquals(1, decoded.activeCount)
        assertEquals(0, decoded.adminCount)
    }
    @Test fun `BOM Windows endings and quoted multiline fields preserve content`() {
        val doctor = Fixtures.doctor(fullName = "أرز، \"شجرة\"\nسطر")
        val csv = "\uFEFF" + DoctorCsvCodec.encode(listOf(doctor)).replace("\n", "\r\n")
        assertEquals(doctor.fullName.replace("\n", "\r\n"), DoctorCsvCodec.decode(csv).doctors.single().fullName)
    }
    @Test fun `duplicate identities names telegram accounts and active ranks are rejected`() {
        val first = Fixtures.doctor(id = "a", fullName = "أرز", telegramId = 100, rank = 1)
        val second = Fixtures.doctor(id = "b", fullName = "بلوط", telegramId = 200, rank = 2)
        listOf(second.copy(id = "a"), second.copy(fullName = "أرز"), second.copy(telegramId = 100),
            second.copy(rank = 1)).forEach { duplicate ->
            assertThrows(IllegalArgumentException::class.java) { decode(first, duplicate) }
        }
    }
    @Test fun `invalid supervisor routing permanent rank header and quotes fail explicitly`() {
        listOf(Fixtures.doctor().copy(supervisorGroupChatId = -100),
            Fixtures.doctor().copy(clinicalRole = ClinicalRole.SUPERVISOR, supervisorGroupChatId = 100),
            Fixtures.doctor(isPermanentAdmin = true, rank = 0)).forEach {
            assertThrows(IllegalArgumentException::class.java) { decode(it) }
        }
        assertThrows(IllegalArgumentException::class.java) { DoctorCsvCodec.decode("id,name\n1,أرز") }
        assertThrows(IllegalArgumentException::class.java) { DoctorCsvCodec.decode("\"unclosed") }
    }
    @Test fun `deleted rows are retained for review with correct summary counts`() {
        val result = decode(Fixtures.doctor(id = "a", fullName = "أرز", rank = 1),
            Fixtures.doctor(id = "b", fullName = "بلوط").copy(deletedAt = Fixtures.timestamp))
        assertEquals(1, result.activeCount)
        assertEquals(1, result.adminCount)
        assertEquals(1, result.deletedCount)
    }
}
