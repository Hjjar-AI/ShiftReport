package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.network.telegram.Markdown
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class TextReportBuilderTest {
    private val builder = TextReportBuilder()
    private val shift = Fixtures.shift()
    private fun summary(count: Int = 1) = ReportSummary(count, count, 0, shift.date, emptyList())
    @Test fun `patient block retains clinical content tasks badges and team names`() {
        val patient = Fixtures.patient(name = "شاي *تجريبي*", hasCompanion = true,
            labs = "Na=140 [تجريبي]", responsibleResidentId = "r", responsibleSpecialistId = "s")
            .copy(tasks = listOf(Fixtures.task().copy(description = "فحص [تجريبي]")),
                badges = listOf(PatientBadge("تنبيه!", PatientBadgePriority.HIGH)))
        val block = builder.buildPatientBlock(patient, mapOf("r" to "أرز"), mapOf("s" to "بلوط"))
        listOf(patient.name, patient.initialDiagnosis, patient.treatmentPlan, patient.followUp,
            patient.labs, patient.tasks.single().description, "تنبيه!", "أرز", "بلوط").forEach {
            assertTrue("Missing $it", block.contains(Markdown.escape(it)))
        }
        assertTrue(block.contains("مرافق"))
    }
    @Test fun `missing assignments are readable and optional empty sections omitted`() {
        val block = builder.buildPatientBlock(Fixtures.patient(treatmentPlan = "", followUp = ""), emptyMap(), emptyMap())
        assertTrue(block.contains("مقيم غير محدد"))
        assertTrue(block.contains("اختصاصي غير محدد"))
        listOf("الخطة العلاجية", "المتابعة", "التحاليل", "المهام").forEach { assertFalse(block.contains(it)) }
    }
    @Test fun `follow up text never creates inferred structured tasks`() {
        val block = builder.buildPatientBlock(Fixtures.patient(followUp = "إجراء فحص غداً"), emptyMap(), emptyMap())
        assertTrue(block.contains("إجراء فحص غداً"))
        assertFalse(block.contains("المهام"))
    }
    @Test fun `completed and pending tasks both remain in report`() {
        val patient = Fixtures.patient().copy(tasks = listOf(Fixtures.task().copy(description = "معلقة تجريبية"),
            Fixtures.task("done", true).copy(description = "مكتملة تجريبية")))
        val block = builder.buildPatientBlock(patient, emptyMap(), emptyMap())
        assertTrue(block.contains("معلقة تجريبية"))
        assertTrue(block.contains("مكتملة تجريبية"))
        assertTrue(block.contains("أتمها أرز"))
    }
    @Test fun `title escapes doctor labels and links only linked doctors`() {
        val title = builder.buildTitle(shift, listOf(Fixtures.doctor(fullName = "أرز [شجرة]", telegramId = 123),
            Fixtures.doctor(id = "plain", fullName = "بلوط")))
        assertTrue(title.contains(Markdown.link(Markdown.escape("أرز [شجرة]"), "tg://user?id=123")))
        assertTrue(title.contains("بلوط"))
        assertFalse(title.contains("tg://user?id=null"))
    }
    @Test fun `long roster is retained across bounded messages without splitting links`() {
        val doctors = (1..180).map { Fixtures.doctor(id = "d-$it", fullName = "شجرة تجريبية $it", telegramId = it.toLong()) }
        val chunks = builder.build(shift, emptyList(), doctors, summary(0), emptyMap(), emptyMap())
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= AppConstants.TELEGRAM_SAFE_CHARS })
        doctors.forEach { doctor ->
            val link = Markdown.link(Markdown.escape(doctor.fullName), requireNotNull(doctor.telegramDeepLink))
            assertEquals(1, chunks.count { it.contains(link) })
        }
    }
    @Test fun `patient blocks retain caller ordering and one summary`() {
        val patients = listOf(Fixtures.patient(id = "b", name = "قهوة", sortOrder = 1),
            Fixtures.patient(id = "a", name = "شاي", sortOrder = 2))
        val output = builder.build(shift, patients, listOf(Fixtures.doctor()), summary(2), emptyMap(), emptyMap()).joinToString("\n")
        assertTrue(output.indexOf("قهوة") < output.indexOf("شاي"))
        assertEquals(1, Regex("عدد المرضى:").findAll(output).count())
        assertTrue(output.contains("عدد المرضى: 2"))
    }
    @Test fun `summary omits zero psychiatric count without losing patient or escort totals`() {
        val result = builder.buildSummary(summary(0).copy(escortCount = 2))
        assertTrue(result.contains("عدد المرضى: 0"))
        assertTrue(result.contains("عدد المرافقين: 2"))
        assertFalse(result.contains("نفسي:"))
    }
}
