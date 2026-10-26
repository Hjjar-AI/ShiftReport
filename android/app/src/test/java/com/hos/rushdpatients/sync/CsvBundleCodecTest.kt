package com.hos.rushdpatients.sync

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CsvBundleCodecTest {
    @Test fun `bundle retains latest three distinct shifts in date order`() {
        val snapshots = (1..5).map { i -> ShiftCsvSnapshot(Fixtures.shift(id = "s$i", date = LocalDate.of(2026, 1, i)),
            listOf(Fixtures.patient(id = "p$i"))) }
        val parsed = CsvBundleCodec.decode(CsvBundleCodec.encode(snapshots + snapshots.last()))
        assertEquals(listOf("s5", "s4", "s3"), parsed.map { it.shiftId })
        assertEquals(listOf("p5", "p4", "p3"), parsed.map { it.patients.single().id })
    }
    @Test fun `single current CSV remains readable and empty bundle is rejected`() {
        assertEquals("shift-1", CsvBundleCodec.decode(CsvCodec.encode(Fixtures.shift(), emptyList())).single().shiftId)
        assertThrows(IllegalArgumentException::class.java) { CsvBundleCodec.encode(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { CsvBundleCodec.decode("# rushdShiftBundle=1\n") }
    }
}
