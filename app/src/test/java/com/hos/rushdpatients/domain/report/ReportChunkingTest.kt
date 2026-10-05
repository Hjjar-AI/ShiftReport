package com.hos.rushdpatients.domain.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportChunkingTest {

    @Test
    fun `small report fits in one chunk`() {
        val chunks = ReportChunking.chunk(
            titleBlock = "Title",
            patientBlocks = listOf("patient1"),
            summaryBlock = "Sum",
            maxChars = 1000
        )
        assertEquals(1, chunks.size)
        assertEquals("Title\npatient1\n\nSum", chunks.first())
    }

    @Test
    fun `empty patient list produces one chunk`() {
        val chunks = ReportChunking.chunk(
            titleBlock = "Title",
            patientBlocks = emptyList(),
            summaryBlock = "Sum",
            maxChars = 1000
        )
        assertEquals(1, chunks.size)
    }

    @Test
    fun `no chunk exceeds maxChars`() {
        val title = "Title"
        val blocks = (1..20).map { "patient$it-payload" }
        val summary = "Summary"

        val chunks = ReportChunking.chunk(title, blocks, summary, maxChars = 60)

        chunks.forEach { chunk ->
            assertTrue(
                "Chunk exceeds maxChars: ${chunk.length} > 60",
                chunk.length <= 60
            )
        }
    }

    @Test
    fun `title opens first chunk`() {
        val chunks = ReportChunking.chunk(
            titleBlock = "TITLE",
            patientBlocks = (1..10).map { "p$it-longpayload" },
            summaryBlock = "S",
            maxChars = 40
        )
        assertTrue(chunks.first().startsWith("TITLE"))
    }

    @Test
    fun `summary ends last chunk`() {
        val chunks = ReportChunking.chunk(
            titleBlock = "TITLE",
            patientBlocks = (1..10).map { "p$it-longpayload" },
            summaryBlock = "SUMMARY",
            maxChars = 40
        )
        assertTrue(chunks.last().endsWith("SUMMARY"))
    }

    @Test
    fun `all patients appear across chunks`() {
        val blocks = (1..30).map { "patient-$it" }
        val chunks = ReportChunking.chunk("T", blocks, "S", maxChars = 100)
        val allText = chunks.joinToString("\n")
        blocks.forEach { b ->
            assertTrue("Missing $b", allText.contains(b))
        }
    }
}