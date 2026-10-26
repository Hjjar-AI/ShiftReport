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
        wire.state = wire.state.copy(doctorsData = DoctorsRegistryCodec.encode(incoming), doctorsMessageId = 10)
        assertEquals(2, doctorSync.fetchAndApplyLatestDoctors().getOrThrow())
        assertTrue("pin:local-test-hash" in requireNotNull(doctors.getById(admin.id)).extraOptions)
        assertEquals("لقب منشور", doctors.getById(admin.id)?.customTitle)
        assertNotNull(doctors.getById("new"))
        assertFalse(doctors.hasPendingSharedChanges())
    }
    @Test fun pendingLocalRegistryChangesBlockRemoteReplacement() = runBlocking<Unit> {
        doctors.upsert(doctor("local"))
        val before = doctors.getAll()
        assertTrue(doctorSync.fetchAndApplyLatestDoctors().isFailure)
        assertEquals(before, doctors.getAll())
        assertTrue(wire.methods.isEmpty())
    }
    @Test fun remoteCannotRemovePermanentAdministrator() = runBlocking<Unit> {
        wire.state = wire.state.copy(doctorsData = DoctorsRegistryCodec.encode(listOf(doctor("other", "بلوط", 1))))
        assertTrue(doctorSync.fetchAndApplyLatestDoctors().isFailure)
        assertEquals(admin, doctors.getById(admin.id))
    }
    @Test fun publishedAuthorityRequiresMatchingLiveTelegramIdentity() = runBlocking<Unit> {
        doctors.upsert(admin.copy(telegramId = 123))
        admin = requireNotNull(doctors.getById(admin.id)); login(admin)
        wire.state = wire.state.copy(doctorsData = DoctorsRegistryCodec.encode(listOf(admin)))
        assertEquals(admin, doctorSync.requirePublishedAdmin(wire.state))
        wire.state = wire.state.copy(doctorsData = DoctorsRegistryCodec.encode(listOf(admin.copy(telegramId = 456))))
        rejected { doctorSync.requirePublishedAdmin(wire.state) }
        rejected { doctorSync.requirePublishedClinician(wire.state) }
    }
}
