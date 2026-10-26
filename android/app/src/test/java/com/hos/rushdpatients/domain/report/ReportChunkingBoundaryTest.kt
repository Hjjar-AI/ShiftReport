package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.network.telegram.Markdown
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class ReportChunkingBoundaryTest {
    private fun assertSafe(chunks: List<String>, limit: Int) {
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertTrue("Message length ${chunk.length} exceeds $limit", chunk.length <= limit)
            assertFalse(chunk.isEmpty())
            assertFalse(Character.isHighSurrogate(chunk.last()))
            assertFalse(Character.isLowSurrogate(chunk.first()))
            // A message may end with an escaped literal backslash, never half an escape.
            assertEquals(0, chunk.takeLastWhile { it == '\\' }.length % 2)
        }
    }

    @Test fun `exact limit stays in one message and one character less splits`() {
        val text = "عنوان\nمريض\n\nملخص"
        assertEquals(listOf(text), ReportChunking.chunk("عنوان", listOf("مريض"), "ملخص", text.length))
        val split = ReportChunking.chunk("عنوان", listOf("مريض"), "ملخص", text.length - 1)
        assertTrue(split.size > 1)
        assertSafe(split, text.length - 1)
    }

    @Test fun `oversized patient retains every Arabic and emoji character`() {
        val patient = "متابعة 💊 ونتيجة ☕ ".repeat(800)
        val chunks = ReportChunking.chunk("عنوان", listOf(patient), "ملخص", 100)
        assertSafe(chunks, 100)
        assertEquals(patient, chunks.joinToString("").removePrefix("عنوان").removePrefix("\n")
            .removeSuffix("ملخص").removeSuffix("\n\n"))
        assertTrue(chunks.first().startsWith("عنوان"))
        assertTrue(chunks.last().endsWith("ملخص"))
    }

    @Test fun `escaped special characters and literal backslashes survive continuation`() {
        val escaped = Markdown.escape("نتيجة [x] * ! \\ 1.2 mg ".repeat(100))
        val chunks = ReportChunking.chunk("T", listOf(escaped), "S", 37)
        assertSafe(chunks, 37)
        assertEquals(escaped, chunks.joinToString("").removePrefix("T").removePrefix("\n").removeSuffix("S").removeSuffix("\n\n"))
    }

    @Test fun `long bold patient name has balanced formatting in every continuation`() {
        val name = "شاي💊".repeat(100)
        val chunks = ReportChunking.chunk("T", listOf(Markdown.bold(name)), "S", 40)
        assertSafe(chunks, 40)
        val continued = chunks.drop(1).map { it.removeSuffix("S").removeSuffix("\n\n") }.filter { it.isNotEmpty() }
        continued.forEach { assertTrue(it.startsWith("*") && it.endsWith("*")) }
        assertEquals(name, continued.joinToString("") { it.removePrefix("*").removeSuffix("*") })
    }

    @Test fun `underline remains balanced across continuation messages`() {
        val text = "تحاليل".repeat(100)
        val chunks = ReportChunking.chunk("T", listOf(Markdown.underline(text)), "S", 30)
        assertSafe(chunks, 30)
        val continued = chunks.drop(1).map { it.removeSuffix("S").removeSuffix("\n\n") }.filter { it.isNotEmpty() }
        continued.forEach { assertTrue(it.startsWith("__") && it.endsWith("__")) }
        assertEquals(text, continued.joinToString("") { it.removePrefix("__").removeSuffix("__") })
    }

    @Test fun `links remain whole when long formatted title is split`() {
        val links = (1..30).map { Markdown.link("أرز $it", "tg://user?id=$it") }
        val chunks = ReportChunking.chunk("*" + links.joinToString(" ") + "*", emptyList(), "S", 100)
        assertSafe(chunks, 100)
        links.forEach { link -> assertEquals(1, chunks.count { link in it }) }
        chunks.dropLast(1).forEach { assertTrue(it.startsWith("*") && it.endsWith("*")) }
    }

    @Test fun `indivisible oversized link fails explicitly instead of returning an invalid message`() {
        val link = Markdown.link("أرز".repeat(100), "tg://user?id=123")
        assertThrows(IllegalArgumentException::class.java) { ReportChunking.chunk("*$link*", emptyList(), "S", 40) }
    }

    @Test fun `oversized summary is retained exactly once`() {
        val summary = "ملخص ".repeat(100)
        val chunks = ReportChunking.chunk("T", listOf("patient"), summary, 50)
        assertSafe(chunks, 50)
        assertEquals(summary, chunks.joinToString("").removePrefix("T\npatient").removePrefix("\n\n"))
    }

    @Test fun `invalid limits and blank titles fail explicitly`() {
        listOf(0, -1).forEach { limit ->
            assertThrows(IllegalArgumentException::class.java) { ReportChunking.chunk("T", emptyList(), "S", limit) }
        }
        assertThrows(IllegalArgumentException::class.java) { ReportChunking.chunk(" \n", emptyList(), "S", 100) }
    }

    @Test fun `multiple oversized patients retain exact order with no duplicate content`() {
        val blocks = listOf("شاي💊".repeat(40), "قهوة☕".repeat(40), "كاكاو".repeat(40))
        val chunks = ReportChunking.chunk("T", blocks, "S", 31)
        assertSafe(chunks, 31)
        assertEquals(blocks.joinToString(""), chunks.joinToString("").removePrefix("T").removePrefix("\n").removeSuffix("S").removeSuffix("\n\n"))
    }

    @Test fun `empty optional blocks never create empty continuation messages`() {
        val block = "متابعة💊".repeat(50)
        val chunks = ReportChunking.chunk("T", listOf("", block, ""), "", 31)
        assertSafe(chunks, 31)
        assertEquals(block, chunks.drop(1).joinToString(""))
    }

    @Test fun `real text builder bounds one patient with very long clinical content`() {
        val patient = Fixtures.patient(labs = "Na=140 💊 [تجريبي] ".repeat(1500),
            treatmentPlan = "خطة ".repeat(1500), followUp = "متابعة ".repeat(1500))
        val builder = TextReportBuilder()
        val shift = Fixtures.shift()
        val chunks = builder.build(shift, listOf(patient), listOf(Fixtures.doctor()),
            ReportSummary(1, 1, 0, shift.date, emptyList()), emptyMap(), emptyMap())
        assertSafe(chunks, AppConstants.TELEGRAM_SAFE_CHARS)
        // Formatting may close/reopen only at boundaries. Escaped clinical text must remain exact.
        val text = chunks.joinToString("")
        assertTrue(text.contains(Markdown.escape(patient.labs)))
        assertTrue(text.contains(Markdown.escape(patient.treatmentPlan)))
        assertTrue(text.contains(Markdown.escape(patient.followUp)))
        assertEquals(1, Regex("عدد المرضى:").findAll(text).count())
    }
}
