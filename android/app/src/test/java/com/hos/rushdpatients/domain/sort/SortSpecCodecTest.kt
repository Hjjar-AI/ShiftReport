package com.hos.rushdpatients.domain.sort

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SortSpecCodecTest {
    @Test fun `all fields directions and empty spec round trip`() {
        for (spec in listOf(SortSpec.EMPTY, SortSpec.WORKBOOK,
            SortSpec(SortField.entries.map { SortLevel(it, SortDirection.DESC) }))) {
            assertEquals(spec, SortSpecCodec.decode(SortSpecCodec.encode(spec)))
        }
    }
    @Test fun `absent malformed and unsupported enum input fall back to default`() {
        for (raw in listOf(null, "", "broken", "{\"levels\":[{\"field\":\"UNKNOWN\"}]}")) {
            assertNull(SortSpecCodec.decodeOrNull(raw))
            assertEquals(SortSpec.DEFAULT, SortSpecCodec.decode(raw))
        }
    }
    @Test fun `new unrelated fields do not discard understood sort rules`() {
        assertEquals(SortSpec.EMPTY, SortSpecCodec.decode("{\"levels\":[],\"future\":true}"))
    }
}
