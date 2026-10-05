package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorsRegistryCodecTest {

    @Test
    fun `encode produces expected header and rows`() {
        val doctors = listOf(
            Fixtures.doctor(id = "d1", fullName = "د. أحمد", rank = 0),
            Fixtures.doctor(id = "d2", fullName = "د. سارة", rank = 1, isPermanentAdmin = false)
        )
        val text = DoctorsRegistryCodec.encode(doctors)
        assertTrue(text.startsWith("#,name,gender,id,title,options"))
        assertTrue(text.contains("د. أحمد"))
        assertTrue(text.contains("د. سارة"))
        assertTrue(text.contains("@1"))
    }

    @Test
    fun `round trip preserves names and ranks`() {
        val doctors = listOf(
            Fixtures.doctor(id = "a", fullName = "د. أحمد", rank = 1),
            Fixtures.doctor(id = "b", fullName = "د. سارة", rank = 2, isPermanentAdmin = true),
            Fixtures.doctor(id = "c", fullName = "د. ليلى", rank = 0)
        )
        val text = DoctorsRegistryCodec.encode(doctors)
        val decoded = DoctorsRegistryCodec.decode(text)

        assertEquals(3, decoded.doctors.size)
        val ahmed = decoded.doctors.first { it.fullName == "د. أحمد" }
        assertEquals(1, ahmed.rank)
        assertFalse(ahmed.isPermanentAdmin)

        val sara = decoded.doctors.first { it.fullName == "د. سارة" }
        assertEquals(2, sara.rank)
        assertTrue(sara.isPermanentAdmin)

        val laila = decoded.doctors.first { it.fullName == "د. ليلى" }
        assertEquals(0, laila.rank)
    }

    @Test
    fun `telegram id is round-tripped`() {
        val d = Fixtures.doctor(id = "x", fullName = "د. علي", telegramId = 123456789L)
        val text = DoctorsRegistryCodec.encode(listOf(d))
        val decoded = DoctorsRegistryCodec.decode(text)
        assertEquals(123456789L, decoded.doctors.first().telegramId)
    }

    @Test
    fun `null telegram id encodes as empty`() {
        val d = Fixtures.doctor(id = "x", fullName = "د. علي", telegramId = null)
        val text = DoctorsRegistryCodec.encode(listOf(d))
        val decoded = DoctorsRegistryCodec.decode(text)
        assertEquals(null, decoded.doctors.first().telegramId)
    }

    @Test
    fun `forbidden characters are stripped from names`() {
        val d = Fixtures.doctor(id = "x", fullName = "د. أ|ح@م,د")
        val text = DoctorsRegistryCodec.encode(listOf(d))
        val decoded = DoctorsRegistryCodec.decode(text)
        val decodedName = decoded.doctors.first().fullName
        assertFalse("Name still contains |", decodedName.contains("|"))
        assertFalse("Name still contains @", decodedName.contains("@"))
        assertFalse("Name still contains ,", decodedName.contains(","))
    }

    @Test
    fun `gender is preserved`() {
        val female = Fixtures.doctor(id = "x", fullName = "د. سارة", gender = Gender.FEMALE)
        val male = Fixtures.doctor(id = "y", fullName = "د. أحمد", gender = Gender.MALE)
        val text = DoctorsRegistryCodec.encode(listOf(female, male))
        val decoded = DoctorsRegistryCodec.decode(text)
        assertEquals(
            Gender.FEMALE,
            decoded.doctors.first { it.fullName == "د. سارة" }.gender
        )
        assertEquals(
            Gender.MALE,
            decoded.doctors.first { it.fullName == "د. أحمد" }.gender
        )
    }

    @Test
    fun `deleted doctors are excluded from encoding`() {
        val alive = Fixtures.doctor(id = "a", fullName = "د. أحمد")
        val dead = Fixtures.doctor(id = "b", fullName = "د. ميت").copy(
            deletedAt = java.time.Instant.now()
        )
        val text = DoctorsRegistryCodec.encode(listOf(alive, dead))
        assertFalse(text.contains("د. ميت"))
    }

    @Test
    fun `lastId is max rank`() {
        val doctors = listOf(
            Fixtures.doctor(id = "a", fullName = "أ", rank = 3),
            Fixtures.doctor(id = "b", fullName = "ب", rank = 7),
            Fixtures.doctor(id = "c", fullName = "ج", rank = 0)
        )
        val text = DoctorsRegistryCodec.encode(doctors)
        val lastId = text.substringAfterLast("@").trim().toIntOrNull()
        assertEquals(7, lastId)
    }

    @Test
    fun `decoding blank text returns empty`() {
        val decoded = DoctorsRegistryCodec.decode("")
        assertTrue(decoded.doctors.isEmpty())
        assertEquals(0, decoded.lastId)
    }

    @Test
    fun `custom title is preserved`() {
        val d = Fixtures.doctor(id = "x", fullName = "د. أحمد", customTitle = "مدير الدوام")
        val text = DoctorsRegistryCodec.encode(listOf(d))
        val decoded = DoctorsRegistryCodec.decode(text)
        assertEquals("مدير الدوام", decoded.doctors.first().customTitle)
    }
}