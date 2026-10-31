package com.hos.rushdpatients.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.doctor.DoctorEditInput
import com.hos.rushdpatients.domain.auth.PasswordHasher
import com.hos.rushdpatients.testutil.ClinicalRepositoryFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoctorMutationRepositoryTest : ClinicalRepositoryFixture() {
    private fun input(role: ClinicalRole = ClinicalRole.RESIDENT, pin: String? = null) =
        DoctorEditInput("بلوط", "شجرة", Gender.MALE, role, pin, null, null)
    private suspend fun target(): Doctor {
        doctors.upsert(doctor("target"))
        settings.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
        return requireNotNull(doctors.getById("target"))
    }
    @Test fun realAddHashesPinMarksPendingWithoutCreatingAudit() = runBlocking<Unit> {
        mutations.add(input(pin = "2468")) // Synthetic test PIN only.
        val added = doctors.getAll().single { it.id != admin.id }
        assertTrue(PasswordHasher().verify("2468", added.extraOptions.single().removePrefix("pin:")))
        assertTrue(settings.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false))
        assertFalse(added.extraOptions.any { it == "pin:2468" })
        assertTrue(db.auditDao().getRecent(100).isEmpty())
    }
    @Test fun liveDemotionBlocksMutationDespiteAdminSession() = runBlocking<Unit> {
        val before = target()
        doctors.upsert(admin.copy(rank = 0, isPermanentAdmin = false))
        val auditCount = db.auditDao().getRecent(100).size
        settings.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
        rejected { mutations.edit(before, input()) }
        assertEquals(before, doctors.getById(before.id))
        assertEquals(auditCount, db.auditDao().getRecent(100).size)
        assertFalse(settings.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, true))
    }
    @Test fun staleDoctorCannotOverwriteNewerEdit() = runBlocking<Unit> {
        val stale = target()
        doctors.upsert(stale.copy(customTitle = "لقب جديد"))
        val live = doctors.getById(stale.id)
        assertTrue(rejected { mutations.edit(stale, input()) } is StaleDoctorEditException)
        assertEquals(live, doctors.getById(stale.id))
    }
    @Test fun activePatientProtectsDoctorButLegacyTaskOwnerDoesNotBlockDeletion() = runBlocking<Unit> {
        val target = target()
        patients.upsert(Patient(id = "p", name = "شاي", responsibleResidentId = target.id), "shift-1")
        rejected { mutations.edit(target, input(ClinicalRole.SUPERVISOR)) }
        rejected { mutations.delete(target) }
        patients.upsert(Patient(id = "p", name = "شاي", tasks = listOf(PatientTask("t", "فحص", ownerDoctorId = target.id))), "shift-1")
        mutations.delete(target)
        assertTrue(requireNotNull(doctors.getById(target.id)).isDeleted)
    }
    @Test fun permanentAdminAndEqualRankAreProtectedFromDemotion() = runBlocking<Unit> {
        rejected { mutations.demote(admin, admin.id) }
        val other = target()
        doctors.upsert(admin.copy(isPermanentAdmin = false))
        doctors.upsert(other.copy(rank = 10))
        login(requireNotNull(doctors.getById(admin.id)))
        rejected { mutations.demote(requireNotNull(doctors.getById(other.id)), admin.id) }
        assertTrue(requireNotNull(doctors.getById(other.id)).isAdmin)
    }
    @Test fun wrongActorCannotPromote() = runBlocking<Unit> {
        val target = target()
        rejected { mutations.promote(target, null, "other-actor") }
        assertFalse(requireNotNull(doctors.getById(target.id)).isAdmin)
    }
    @Test fun importPreviewRechecksRegistryAndActor() = runBlocking<Unit> {
        val imported = doctor("imported")
        val plan = mutations.prepareImport(listOf(imported))
        doctors.upsert(doctor("other", "صنوبر"))
        assertTrue(rejected { mutations.importRegistry(plan) } is StaleDoctorImportException)
        assertNull(doctors.getById(imported.id))
                doctors.upsert(doctor("alternate", "سرو", 5))
        val withActor = mutations.prepareImport(listOf(imported))
        login(requireNotNull(doctors.getById("alternate")))
        assertTrue(rejected { mutations.importRegistry(withActor) } is StaleDoctorImportException)
        assertNull(doctors.getById(imported.id))
    }
    @Test fun importPreservesLocalIdentityAndPinButIgnoresImportedSecrets() = runBlocking<Unit> {
        val target = target()
        doctors.upsert(target.copy(telegramId = 123, extraOptions = setOf("pin:local-hash")))
        val incoming = target.copy(id = "portable-id", telegramId = 123,
            extraOptions = setOf("pin:untrusted-hash"))
        assertEquals(1, mutations.importRegistry(mutations.prepareImport(listOf(incoming))))
        val saved = requireNotNull(doctors.getById(target.id))
        assertTrue("pin:local-hash" in saved.extraOptions)
        assertFalse(saved.extraOptions.any { "untrusted" in it })
        assertNull(doctors.getById(incoming.id))
    }
    @Test fun changedTelegramIdentityInvalidatesOldPinOnImport() = runBlocking<Unit> {
        val target = target()
        doctors.upsert(target.copy(telegramId = 123, extraOptions = setOf("pin:local-hash")))
        mutations.importRegistry(mutations.prepareImport(listOf(target.copy(telegramId = 456))))
        assertFalse(requireNotNull(doctors.getById(target.id)).extraOptions.any { it.startsWith("pin:") })
    }
    @Test fun importCannotCreateTwoRowsForOneLocalIdentity() = runBlocking<Unit> {
        val target = target()
        val before = doctors.getAllIncludingDeleted()
        rejected { mutations.prepareImport(listOf(target.copy(id = "one"), target.copy(id = "two"))) }
        assertEquals(before, doctors.getAllIncludingDeleted())
    }
    @Test fun importCannotGrantRankAtOrAboveExecutingAdmin() = runBlocking<Unit> {
        doctors.upsert(admin.copy(rank = 5, isPermanentAdmin = false))
        login(requireNotNull(doctors.getById(admin.id)))
        rejected { mutations.prepareImport(listOf(doctor("high", "سرو", 5))) }
        assertNull(doctors.getById("high"))
    }
    @Test fun thirtyThreeRowCsvImportCommitsThroughRealRepository() = runBlocking<Unit> {
        val names = listOf("أرز", "بلوط", "صنوبر", "سرو", "صفصاف", "زيتون")
        val imported = (1..33).map { i -> doctor("import-$i", names[i % names.size]).copy(
            fullName = "د. ${names[i % names.size]} شجرة ${names[i / names.size]}",
            lastName = "شجرة ${names[i / names.size]}") }
        val csv = com.hos.rushdpatients.domain.doctor.DoctorCsvCodec.encode(imported)
        val parsed = com.hos.rushdpatients.domain.doctor.DoctorCsvCodec.decode(csv)
        assertEquals(33, mutations.importRegistry(mutations.prepareImport(parsed.doctors)))
        assertEquals(34, doctors.count())
    }
    @Test fun importCannotCreateOrPromoteAnotherPermanentOwner() = runBlocking<Unit> {
        val target = target()
        val before = doctors.getAllIncludingDeleted()
        rejected { mutations.prepareImport(listOf(target.copy(rank = 20, isPermanentAdmin = true))) }
        rejected { mutations.prepareImport(listOf(doctor("new-owner", "سرو", 20).copy(isPermanentAdmin = true))) }
        assertEquals(before, doctors.getAllIncludingDeleted())
    }
    @Test fun importKeepsExistingPermanentOwnerDespiteIncomingDemotion() = runBlocking<Unit> {
        mutations.importRegistry(mutations.prepareImport(listOf(admin.copy(rank = 0, isPermanentAdmin = false))))
        assertTrue(requireNotNull(doctors.getById(admin.id)).isPermanentAdmin)
        assertEquals(admin.rank, doctors.getById(admin.id)?.rank)
    }

}
