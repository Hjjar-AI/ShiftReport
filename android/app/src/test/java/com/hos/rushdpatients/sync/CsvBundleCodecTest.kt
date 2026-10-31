package com.hos.rushdpatients.sync

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CsvBundleCodecTest {
    @Test fun `bundle preserves one current ward and rejects multiple wards`() {
        val snapshot = ShiftCsvSnapshot(Fixtures.shift(id = "current"), listOf(Fixtures.patient(id = "patient")))
        val parsed = CsvBundleCodec.decode(CsvBundleCodec.encode(listOf(snapshot))).single()
        assertEquals("current", parsed.shiftId)
        assertEquals("patient", parsed.patients.single().id)
        assertThrows(IllegalArgumentException::class.java) { CsvBundleCodec.encode(listOf(snapshot, snapshot)) }
        val csv = CsvCodec.encode(snapshot.shift, snapshot.patients).trimEnd()
        assertThrows(IllegalArgumentException::class.java) {
            CsvBundleCodec.decode("# rushdShiftBundle=1\n$csv\n#==RUSHD_SHIFT_BOUNDARY_7F3A9C==\n$csv\n")
        }
    }
    @Test fun `unwrapped CSV and empty bundle are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { CsvBundleCodec.decode(CsvCodec.encode(Fixtures.shift(), emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { CsvBundleCodec.encode(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { CsvBundleCodec.decode("# rushdShiftBundle=1\n") }
    }
}
