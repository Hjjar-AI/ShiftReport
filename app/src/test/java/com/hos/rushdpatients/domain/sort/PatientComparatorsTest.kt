package com.hos.rushdpatients.domain.sort

import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class PatientComparatorsTest {

    private val doctors = mapOf(
        "d-ahmed" to "د. أحمد",
        "d-sara" to "د. سارة"
    )

    @Test
    fun `empty spec sorts by sortOrder`() {
        val patients = listOf(
            Fixtures.patient(id = "c", sortOrder = 3),
            Fixtures.patient(id = "a", sortOrder = 1),
            Fixtures.patient(id = "b", sortOrder = 2)
        )
        val sorted = patients.sortedWith(
            PatientComparators.forSpec(SortSpec.EMPTY, doctors)
        )
        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
    }

    @Test
    fun `name ascending sorts alphabetically`() {
        val patients = listOf(
            Fixtures.patient(id = "1", name = "حسن"),
            Fixtures.patient(id = "2", name = "أحمد"),
            Fixtures.patient(id = "3", name = "سعد")
        )
        val spec = SortSpec(listOf(SortLevel(SortField.NAME, SortDirection.ASC)))
        val sorted = patients.sortedWith(PatientComparators.forSpec(spec, doctors))
        assertEquals(listOf("2", "1", "3"), sorted.map { it.id })
    }

    @Test
    fun `name descending sorts reverse alphabetically`() {
        val patients = listOf(
            Fixtures.patient(id = "1", name = "حسن"),
            Fixtures.patient(id = "2", name = "أحمد"),
            Fixtures.patient(id = "3", name = "سعد")
        )
        val spec = SortSpec(listOf(SortLevel(SortField.NAME, SortDirection.DESC)))
        val sorted = patients.sortedWith(PatientComparators.forSpec(spec, doctors))
        assertEquals(listOf("3", "1", "2"), sorted.map { it.id })
    }

    @Test
    fun `gender ascending puts males first`() {
        val patients = listOf(
            Fixtures.patient(id = "1", gender = Gender.FEMALE),
            Fixtures.patient(id = "2", gender = Gender.MALE),
            Fixtures.patient(id = "3", gender = Gender.FEMALE)
        )
        val spec = SortSpec(listOf(SortLevel(SortField.GENDER, SortDirection.ASC)))
        val sorted = patients.sortedWith(PatientComparators.forSpec(spec, doctors))
        assertEquals(Gender.MALE, sorted.first().gender)
    }

    @Test
    fun `diagnosis custom order puts psycho before addiction`() {
        val patients = listOf(
            Fixtures.patient(id = "1", diagnosis = "إدمان"),
            Fixtures.patient(id = "2", diagnosis = "نفسي"),
            Fixtures.patient(id = "3", diagnosis = "أخرى")
        )
        val spec = SortSpec(listOf(SortLevel(SortField.DIAGNOSIS, SortDirection.ASC)))
        val sorted = patients.sortedWith(PatientComparators.forSpec(spec, doctors))
        // CustomOrder.DEFAULT = ["نفسي", "إدمان", unknown]
        assertEquals("نفسي", sorted[0].diagnosis)
        assertEquals("إدمان", sorted[1].diagnosis)
        assertEquals("أخرى", sorted[2].diagnosis)
    }

    @Test
    fun `days ascending puts shorter stays first`() {
        val patients = listOf(
            Fixtures.patient(id = "1", admittanceDays = 10),
            Fixtures.patient(id = "2", admittanceDays = 3),
            Fixtures.patient(id = "3", admittanceDays = 7)
        )
        val spec = SortSpec(listOf(SortLevel(SortField.DAYS_OF_ADMITTANCE, SortDirection.ASC)))
        val sorted = patients.sortedWith(PatientComparators.forSpec(spec, doctors))
        assertEquals(listOf(3, 7, 10), sorted.map { it.admittanceDays })
    }

    @Test
    fun `days descending puts longer stays first`() {
        val patients = listOf(
            Fixtures.patient(id = "1", admittanceDays = 10),
            Fixtures.patient(id = "2", admittanceDays = 3),
            Fixtures.patient(id = "3", admittanceDays = 7)
        )
        val spec = SortSpec(listOf(SortLevel(SortField.DAYS_OF_ADMITTANCE, SortDirection.DESC)))
        val sorted = patients.sortedWith(PatientComparators.forSpec(spec, doctors))
        assertEquals(listOf(10, 7, 3), sorted.map { it.admittanceDays })
    }

    @Test
    fun `multi-level sort applies levels in order`() {
        val patients = listOf(
            Fixtures.patient(id = "1", gender = Gender.FEMALE, name = "حسن"),
            Fixtures.patient(id = "2", gender = Gender.MALE, name = "سعد"),
            Fixtures.patient(id = "3", gender = Gender.FEMALE, name = "أحمد"),
            Fixtures.patient(id = "4", gender = Gender.MALE, name = "بكر")
        )
        val spec = SortSpec(
            listOf(
                SortLevel(SortField.GENDER, SortDirection.ASC),
                SortLevel(SortField.NAME, SortDirection.ASC)
            )
        )
        val sorted = patients.sortedWith(PatientComparators.forSpec(spec, doctors))
        // Males first, alphabetically: بكر (4), سعد (2)
        // Then females, alphabetically: أحمد (3), حسن (1)
        assertEquals(listOf("4", "2", "3", "1"), sorted.map { it.id })
    }

    @Test
    fun `supervisor sort uses doctor name lookup`() {
        val patients = listOf(
            Fixtures.patient(id = "1", supervisorId = "d-sara"),
            Fixtures.patient(id = "2", supervisorId = "d-ahmed")
        )
        val spec = SortSpec(listOf(SortLevel(SortField.SUPERVISOR, SortDirection.ASC)))
        val sorted = patients.sortedWith(PatientComparators.forSpec(spec, doctors))
        // أحمد < سارة
        assertEquals("d-ahmed", sorted.first().supervisorId)
    }
}