package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientRoundsNavigationTest {
    @Test fun `rounds follow displayed filtered order and guard both boundaries`() {
        val ids = listOf("third", "first", "second")
        assertEquals(PatientRoundsNavigation(0, 3, null, "first"), patientRoundsNavigation(ids, "third"))
        assertEquals(PatientRoundsNavigation(1, 3, "third", "second"), patientRoundsNavigation(ids, "first"))
        assertEquals(PatientRoundsNavigation(2, 3, "first", null), patientRoundsNavigation(ids, "second"))
        assertNull(patientRoundsNavigation(ids, "filtered-out"))
        assertNull(patientRoundsNavigation(emptyList(), "first"))
    }
}
