package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ArabicSearchNormalizerTest {
    @Test fun `Arabic variants diacritics and whitespace normalize consistently`() {
        assertEquals("احمد علي", ArabicSearchNormalizer.normalize("  أَحْمَدُ\tعَلَى  "))
        assertEquals("ايه ويو", ArabicSearchNormalizer.normalize("آية وئؤ"))
    }
    @Test fun `matching supports Latin case multiple nullable fields and empty query`() {
        assertTrue(ArabicSearchNormalizer.matches("CBC", null, "cbc normal"))
        assertTrue(ArabicSearchNormalizer.matches("احمد", "أحمد"))
        assertTrue(ArabicSearchNormalizer.matches(" ", null))
        assertFalse(ArabicSearchNormalizer.matches("مفقود", null, "شاي"))
    }
}
