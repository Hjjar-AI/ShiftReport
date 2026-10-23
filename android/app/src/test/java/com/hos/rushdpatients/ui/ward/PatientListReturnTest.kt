package com.hos.rushdpatients.ui.ward

import org.junit.Assert.*
import org.junit.Test

class PatientListReturnTest {
    @Test fun `ungrouped return includes the search header`() {
        assertEquals(1, patientGridReturnIndex("a", listOf("a", "b"), emptyList(), emptySet()))
        assertEquals(2, patientGridReturnIndex("b", listOf("a", "b"), emptyList(), emptySet()))
        assertNull(patientGridReturnIndex("missing", listOf("a"), emptyList(), emptySet()))
    }
    @Test fun `grouped return counts headers and visible rows`() {
        val groups = listOf("empty" to emptyList(), "one" to listOf("a", "b"), "two" to listOf("c"))
        assertEquals(2, patientGridReturnIndex("a", emptyList(), groups, emptySet()))
        assertEquals(3, patientGridReturnIndex("b", emptyList(), groups, emptySet()))
        assertEquals(5, patientGridReturnIndex("c", emptyList(), groups, emptySet()))
    }
    @Test fun `collapsed groups contribute a header but no patient rows`() {
        val groups = listOf("one" to listOf("a", "b"), "two" to listOf("c"))
        assertNull(patientGridReturnIndex("a", listOf("a"), groups, setOf("one")))
        assertEquals(3, patientGridReturnIndex("c", emptyList(), groups, setOf("one")))
        assertNull(patientGridReturnIndex("c", emptyList(), groups, setOf("one", "two")))
    }
}
