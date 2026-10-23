package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReportReviewTest {
    private val shift = Fixtures.shift()
    private val actor = Fixtures.doctor()
    private val patient = Fixtures.patient().copy(tasks = listOf(Fixtures.task()))
    private fun report() = BuiltReport(shift, listOf(patient), listOf(actor), mapOf(actor.id to actor.fullName),
        ReportSummary(1, 1, 0, shift.date, listOf(actor)), listOf("synthetic report"))
    @Test fun `publication metadata and local editor metadata do not invalidate review`() {
        val original = report()
        val changed = original.copy(shift = shift.copy(revision = 100, snapshotId = "new", sentAt = Instant.EPOCH),
            patients = listOf(patient.copy(revision = 99, updatedAt = Instant.EPOCH,
                lastEditedByDoctorId = "other", lastEditedByName = "بلوط")),
            registryDoctors = listOf(actor.copy(updatedAt = Instant.EPOCH, extraOptions = setOf("local"), telegramUsername = "synthetic")))
        ReportReview.requireMatches(original, changed)
        assertEquals(ReportReview.fingerprint(original), ReportReview.fingerprint(changed))
    }
    @Test fun `clinical content tasks roster order and destination changes invalidate review`() {
        val original = report()
        val changes = listOf(original.copy(patients = listOf(patient.copy(labs = "changed"))),
            original.copy(patients = listOf(patient.copy(tasks = listOf(Fixtures.task().copy(description = "changed"))))),
            original.copy(shift = shift.copy(doctorIds = listOf("other", actor.id))),
            original.copy(registryDoctors = listOf(actor.copy(supervisorGroupChatId = -100))),
            original.copy(textChunks = listOf("changed")))
        changes.forEach { changed ->
            assertThrows(ReportChangedSinceReviewException::class.java) { ReportReview.requireMatches(original, changed) }
            assertNotEquals(ReportReview.fingerprint(original), ReportReview.fingerprint(changed))
        }
    }
    @Test fun `unrelated doctors are ignored and supervisor subsets get distinct fingerprints`() {
        val original = report()
        val changed = original.copy(registryDoctors = listOf(Fixtures.doctor(id = "unrelated"), actor))
        ReportReview.requireMatches(original, changed)
        assertEquals(ReportReview.fingerprint(original), ReportReview.fingerprint(changed))
        assertNotEquals(ReportReview.supervisorFingerprint(original, "s1"), ReportReview.supervisorFingerprint(original, "s2"))
        assertEquals(64, ReportReview.fingerprint(original).length)
    }
}
