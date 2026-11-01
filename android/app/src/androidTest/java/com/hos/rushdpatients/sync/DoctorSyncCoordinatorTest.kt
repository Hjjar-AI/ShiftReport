package com.hos.rushdpatients.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.testutil.SyncFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoctorSyncCoordinatorTest : SyncFixture() {
    @Test fun fullRegistryPullPreservesLocalPinAndRecordsBaseline() = runBlocking<Unit> {
        doctors.upsert(admin.copy(telegramId = 123, extraOptions = setOf("pin:local-test-hash")))
        admin = requireNotNull(doctors.getById(admin.id))
        val incoming = listOf(admin.copy(customTitle = "لقب منشور"), doctor("new"))
        settings.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
        settings.put(AppConstants.SETTING_DOCTORS_BASE_REGISTRY, DoctorsRegistryCodec.encode(doctors.getAll()))
        wire.state = wire.state.copy(doctorsFileId = wire.registry(DoctorsRegistryCodec.encode(incoming)), doctorsMessageId = 10)
        assertEquals(2, doctorSync.fetchAndApplyLatestDoctors().getOrThrow())
        assertTrue("pin:local-test-hash" in requireNotNull(doctors.getById(admin.id)).extraOptions)
        assertEquals("لقب منشور", doctors.getById(admin.id)?.customTitle)
        assertNotNull(doctors.getById("new"))
        assertFalse(doctors.hasPendingSharedChanges())
    }
    @Test fun pendingLocalRegistryChangesAreReplacedWithLocalUndo() = runBlocking<Unit> {
        doctors.upsert(doctor("local"))
        seedShift("selected", com.hos.rushdpatients.util.ShiftDate.current())
        shifts.selectCurrent("selected")
        assertTrue(doctorSync.fetchAndApplyLatestDoctors().isSuccess)
        assertNull(doctors.getById("local")?.takeUnless { it.isDeleted })
        undo.undo()
        assertNotNull(doctors.getById("local")?.takeUnless { it.isDeleted })
        assertFalse("sendDocument" in wire.methods)
        assertFalse("editMessageText" in wire.methods)
    }
    @Test fun publishedRegistryNeedsLinkedPermanentSuccessor() = runBlocking<Unit> {
        wire.state = wire.state.copy(doctorsFileId = wire.registry(DoctorsRegistryCodec.encode(listOf(doctor("other", "بلوط", 1)))))
        assertTrue(doctorSync.fetchAndApplyLatestDoctors().isFailure)
        assertEquals(admin, doctors.getById(admin.id))
        val successor = doctor("successor", "صفصاف", 1).copy(isPermanentAdmin = true, telegramId = 999005)
        wire.state = wire.state.copy(doctorsFileId = wire.registry(DoctorsRegistryCodec.encode(listOf(successor))))
        doctorSync.fetchAndApplyLatestDoctors().getOrThrow()
        assertTrue(requireNotNull(doctors.getById(successor.id)).isPermanentAdmin)
        assertTrue(requireNotNull(doctors.getById(admin.id)).isDeleted)
    }
    @Test fun publishedAuthorityRequiresMatchingLiveTelegramIdentity() = runBlocking<Unit> {
        doctors.upsert(admin.copy(telegramId = 123))
        admin = requireNotNull(doctors.getById(admin.id)); login(admin)
        wire.state = wire.state.copy(doctorsFileId = wire.registry(DoctorsRegistryCodec.encode(listOf(admin))))
        assertEquals(admin, doctorSync.requirePublishedAdmin(wire.state))
        wire.state = wire.state.copy(doctorsFileId = wire.registry(DoctorsRegistryCodec.encode(listOf(admin.copy(telegramId = 456)))))
        rejected { doctorSync.requirePublishedAdmin(wire.state) }
        rejected { doctorSync.requirePublishedClinician(wire.state) }
    }

    @Test fun publishedIdentitySwapReplacesBindingsAndUndoIsLocal() = runBlocking<Unit> {
        val oak = doctor("oak", "بلوط").copy(telegramId = 999002, extraOptions = setOf("pin:synthetic-oak"))
        val pine = doctor("pine", "صنوبر").copy(telegramId = 999003, extraOptions = setOf("pin:synthetic-pine"))
        doctors.upsertAll(listOf(oak, pine))
        val incoming = listOf(admin, oak.copy(telegramId = pine.telegramId), pine.copy(telegramId = oak.telegramId))
        wire.state = wire.state.copy(doctorsFileId = wire.registry(DoctorsRegistryCodec.encode(incoming)))
        doctorSync.fetchAndApplyLatestDoctors().getOrThrow()
        assertEquals(pine.telegramId, doctors.getById(oak.id)?.telegramId)
        assertEquals(oak.telegramId, doctors.getById(pine.id)?.telegramId)
        assertNull(doctors.getById(oak.id)?.options?.pinHash)
        assertNull(doctors.getById(pine.id)?.options?.pinHash)
        wire.methods.clear()
        undo.undo()
        assertEquals(oak.telegramId, doctors.getById(oak.id)?.telegramId)
        assertEquals(pine.telegramId, doctors.getById(pine.id)?.telegramId)
        assertTrue(wire.methods.isEmpty())
    }
}
