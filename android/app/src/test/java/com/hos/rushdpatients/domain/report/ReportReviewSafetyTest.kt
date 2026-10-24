package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.data.model.TaskPriority
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReportReviewSafetyTest {
    private val actor = Fixtures.doctor()
    private val supervisor = Fixtures.doctor(id = "supervisor", fullName = "بلوط")
        .copy(clinicalRole = ClinicalRole.SUPERVISOR)
    private val owner = Fixtures.doctor(id = "owner", fullName = "صنوبر")
    private val task = Fixtures.task().copy(ownerDoctorId = owner.id)
    private val patient = Fixtures.patient(responsibleResidentId = actor.id,
        responsibleSpecialistId = supervisor.id).copy(tasks = listOf(task))
    private val shift = Fixtures.shift()

    private fun report() = BuiltReport(shift, listOf(patient), listOf(actor),
        mapOf(actor.id to actor.fullName, supervisor.id to supervisor.fullName, owner.id to owner.fullName),
        ReportSummary(1, 1, 0, shift.date, listOf(actor)), listOf("تقرير تجريبي"),
        registryDoctors = listOf(actor, supervisor, owner))

    private fun assertInvalidated(before: BuiltReport, after: BuiltReport) {
        assertThrows(ReportChangedSinceReviewException::class.java) { ReportReview.requireMatches(before, after) }
        assertNotEquals(ReportReview.fingerprint(before), ReportReview.fingerprint(after))
        assertNotEquals(ReportReview.supervisorFingerprint(before, supervisor.id),
            ReportReview.supervisorFingerprint(after, supervisor.id))
    }

    @Test fun `demographic and clinical changes invalidate review even with unchanged rendered chunks`() {
        val original = report()
        val changes = listOf(
            patient.copy(name = "قهوة"), patient.copy(gender = Gender.FEMALE),
            patient.copy(birthDate = LocalDate.of(1991, 1, 1)),
            patient.copy(admittanceNumber = "changed"), patient.copy(admittanceDate = null),
            patient.copy(hasCompanion = true), patient.copy(diagnosisType = DiagnosisType.DUAL),
            patient.copy(initialDiagnosis = "تشخيص آخر"), patient.copy(treatmentPlan = "خطة أخرى"),
            patient.copy(followUp = "متابعة أخرى"), patient.copy(labs = "Na=140"),
            patient.copy(badges = listOf(PatientBadge("تنبيه تجريبي"))),
            patient.copy(isPriority = true), patient.copy(sortOrder = 2),
            patient.copy(deletedAt = Instant.EPOCH)
        )
        changes.forEach { assertInvalidated(original, original.copy(patients = listOf(it))) }
    }

    @Test fun `assignment and structured task changes invalidate review`() {
        val original = report()
        listOf(patient.copy(responsibleResidentId = null), patient.copy(responsibleSpecialistId = null),
            patient.copy(tasks = emptyList())).forEach {
            assertInvalidated(original, original.copy(patients = listOf(it)))
        }
        listOf(task.copy(id = "new-task"), task.copy(description = "فحص آخر"),
            task.copy(ownerDoctorId = null), task.copy(ownerName = "صنوبر"),
            task.copy(priority = TaskPriority.HIGH), task.copy(dueAtEpochMillis = 1000),
            task.copy(dueShiftDate = shift.date.toString()),
            task.copy(done = true, completedByDoctorId = actor.id,
                completedByName = actor.fullName, completedAtEpochMillis = 1000)).forEach {
            assertInvalidated(original, original.copy(patients = listOf(patient.copy(tasks = listOf(it)))))
        }
    }

    @Test fun `shift identity date mode and configured sort invalidate review`() {
        val original = report()
        listOf(shift.copy(id = "other-shift"), shift.copy(date = shift.date.plusDays(1)),
            shift.copy(multiDoctorMode = true), shift.copy(sortSpecJson = "{\"levels\":[]}")).forEach {
            assertInvalidated(original, original.copy(shift = it))
        }
    }

    @Test fun `reordering patients and tasks invalidates review`() {
        val original = report().copy(patients = listOf(patient, patient.copy(id = "coffee", name = "قهوة")))
        assertInvalidated(original, original.copy(patients = original.patients.reversed()))
        val twoTasks = report().copy(patients = listOf(patient.copy(tasks = listOf(task, task.copy(id = "second")))))
        assertInvalidated(twoTasks, twoTasks.copy(patients = listOf(patient.copy(
            tasks = twoTasks.patients.single().tasks.reversed()))))
    }

    @Test fun `message boundaries and order remain part of reviewed content`() {
        val original = report().copy(textChunks = listOf("أول", "ثان"))
        assertInvalidated(original, original.copy(textChunks = original.textChunks.reversed()))
        assertInvalidated(original, original.copy(textChunks = listOf("أول\nثان")))
    }

    @Test fun `supervisor identity role destination and deletion changes invalidate review`() {
        val original = report()
        listOf(supervisor.copy(fullName = "أرز"), supervisor.copy(telegramId = 123),
            supervisor.copy(supervisorGroupChatId = -100), supervisor.copy(clinicalRole = ClinicalRole.RESIDENT),
            supervisor.copy(deletedAt = Instant.EPOCH)).forEach { changed ->
            assertInvalidated(original, original.copy(registryDoctors = listOf(actor, changed, owner)))
        }
    }

    @Test fun `task owner outside shift roster participates in review`() {
        val original = report()
        assertFalse(owner.id in original.shift.doctorIds)
        assertInvalidated(original, original.copy(registryDoctors = listOf(actor, supervisor,
            owner.copy(fullName = "بلوط جديد"))))
        assertInvalidated(original, original.copy(registryDoctors = listOf(actor, supervisor)))
    }

    @Test fun `registry order and unrelated doctor edits do not invalidate review`() {
        val unrelated = Fixtures.doctor(id = "unrelated", fullName = "صفصاف")
        val original = report().copy(registryDoctors = report().registryDoctors + unrelated)
        val changed = original.copy(registryDoctors = listOf(unrelated.copy(fullName = "سرو",
            supervisorGroupChatId = -100, deletedAt = Instant.EPOCH), owner, supervisor, actor))
        ReportReview.requireMatches(original, changed)
        assertEquals(ReportReview.fingerprint(original), ReportReview.fingerprint(changed))
        assertEquals(ReportReview.supervisorFingerprint(original, supervisor.id),
            ReportReview.supervisorFingerprint(changed, supervisor.id))
    }

    @Test fun `device secrets usernames and timestamps do not invalidate either fingerprint`() {
        val original = report()
        val changed = original.copy(registryDoctors = original.registryDoctors.map {
            it.copy(extraOptions = setOf("pin:synthetic", "syncAlias:synthetic"),
                telegramUsername = "synthetic", updatedAt = Instant.EPOCH)
        })
        ReportReview.requireMatches(original, changed)
        assertEquals(ReportReview.fingerprint(original), ReportReview.fingerprint(changed))
        assertEquals(ReportReview.supervisorFingerprint(original, supervisor.id),
            ReportReview.supervisorFingerprint(changed, supervisor.id))
    }

    @Test fun `supervisor fingerprints reject blank identity and differ from general report`() {
        val original = report()
        listOf("", " ", "\n\t").forEach { blank ->
            assertThrows(IllegalArgumentException::class.java) { ReportReview.supervisorFingerprint(original, blank) }
        }
        assertNotEquals(ReportReview.fingerprint(original), ReportReview.supervisorFingerprint(original, supervisor.id))
    }
}
