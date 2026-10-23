package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

import com.hos.rushdpatients.config.AppConstants

class DoctorValidatorTest {
    @Test fun `Arabic names optional title and title limit are accepted`() {
        assertEquals(DoctorValidationResult.Valid, DoctorValidator.validate("أرز", "شجرة", null))
        assertEquals(DoctorValidationResult.Valid, DoctorValidator.validate("أرز", "شجرة",
            "س".repeat(AppConstants.CUSTOM_TITLE_MAX_LENGTH)))
    }
    @Test fun `forbidden delimiters are rejected in every editable name field`() {
        listOf('\\', '"', '\'', '=', ',', '|', '@', '+', '&').forEach { delimiter ->
            listOf(Triple("أرز$delimiter", "شجرة", null), Triple("أرز", "شجرة$delimiter", null),
                Triple("أرز", "شجرة", "لقب$delimiter")).forEach { (first, last, title) ->
                val result = DoctorValidator.validate(first, last, title) as DoctorValidationResult.Invalid
                assertTrue(result.errors.contains(DoctorValidationError.FORBIDDEN_CHARACTERS))
            }
        }
    }
    @Test fun `blank names and excessive title collect separate errors`() {
        val result = DoctorValidator.validate(" ", "", "س".repeat(AppConstants.CUSTOM_TITLE_MAX_LENGTH + 1)) as DoctorValidationResult.Invalid
        assertEquals(setOf(DoctorValidationError.FIRST_NAME_EMPTY, DoctorValidationError.LAST_NAME_EMPTY,
            DoctorValidationError.CUSTOM_TITLE_TOO_LONG), result.errors.toSet())
    }
}
