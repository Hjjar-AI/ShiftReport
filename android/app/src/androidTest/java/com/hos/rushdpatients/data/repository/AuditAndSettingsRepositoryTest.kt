package com.hos.rushdpatients.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import com.hos.rushdpatients.testutil.DatabaseFixture
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room DAOs in fresh memory only; no installed clinical DB/preferences. */
@RunWith(AndroidJUnit4::class)
class AuditAndSettingsRepositoryTest : DatabaseFixture() {
    private val dispatchers = DefaultDispatcherProvider()
    private fun settings() = SettingsRepository(db.settingDao(), dispatchers)
    private fun audit() = AuditRepository(db.auditDao(), dispatchers)
    private fun entry(patient: String?, at: Long) = AuditEntryEntity(actorDoctorId = "tree", actorName = "أرز",
        action = "synthetic", patientId = patient, beforeValue = "قبل", afterValue = "بعد", detail = "تفصيل", atEpochMillis = at)
    @Test fun auditHistoryIsPatientScopedAndNewestFirst() = runBlocking<Unit> {
        db.auditDao().insert(entry("tea", 10)); db.auditDao().insert(entry("coffee", 30)); db.auditDao().insert(entry("tea", 20))
        val history = audit().getForPatient("tea")
        assertEquals(listOf(20L, 10L), history.map { it.atEpochMillis })
        assertTrue(history.all { it.patientId == "tea" })
    }
    @Test fun globalAuditRespectsLimitAndIncludesProjectEvents() = runBlocking<Unit> {
        db.auditDao().insert(entry("tea", 10)); db.auditDao().insert(entry(null, 30)); db.auditDao().insert(entry("coffee", 20))
        assertEquals(listOf(30L, 20L), audit().getRecent(2).map { it.atEpochMillis })
        assertTrue(audit().getRecent(0).isEmpty())
    }
    @Test fun auditRecordRetainsActorClinicalBeforeAfterAndDetail() = runBlocking<Unit> {
        audit().record("tree", "أرز", "synthetic edit", "تفصيل", "tea", "قبل", "بعد")
        val saved = audit().getForPatient("tea").single()
        assertEquals("tree", saved.actorDoctorId); assertEquals("أرز", saved.actorName)
        assertEquals("synthetic edit", saved.action); assertEquals("تفصيل", saved.detail)
        assertEquals("قبل", saved.beforeValue); assertEquals("بعد", saved.afterValue)
        assertTrue(saved.atEpochMillis > 0)
    }
    @Test fun anonymousProjectAuditIsRetainedWithoutPatientAttribution() = runBlocking<Unit> {
        audit().record(null, null, "synthetic project event")
        val saved = audit().getRecent().single()
        assertNull(saved.actorDoctorId); assertNull(saved.actorName); assertNull(saved.patientId)
        assertTrue(audit().getForPatient("tea").isEmpty())
    }
    @Test fun booleanSettingsRejectMalformedValuesAndRespectCallerDefaults() = runBlocking<Unit> {
        val settings = settings()
        for (raw in listOf("TRUE", "1", "yes", "")) {
            settings.put("synthetic", raw)
            assertFalse(settings.getBoolean("synthetic", false))
            assertTrue(settings.getBoolean("synthetic", true))
        }
        settings.putBoolean("synthetic", false)
        assertFalse(settings.getBoolean("synthetic", true))
    }
    @Test fun overflowingNumericSettingsUseDefaultsWithoutCorruptingOtherKeys() = runBlocking<Unit> {
        val settings = settings()
        settings.put("int", "999999999999999999999999"); settings.put("long", "999999999999999999999999")
        assertEquals(7, settings.getInt("int", 7)); assertEquals(9L, settings.getLong("long", 9))
        settings.putInt("int", Int.MAX_VALUE); settings.putLong("long", Long.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, settings.getInt("int", 0)); assertEquals(Long.MAX_VALUE, settings.getLong("long", 0))
    }
    @Test fun lockTimeoutReadsAndWritesStayWithinOneToSixtyMinutes() = runBlocking<Unit> {
        val settings = settings()
        assertEquals(20, settings.getAutoLockMinutes())
        settings.setAutoLockMinutes(0); assertEquals(1, settings.getAutoLockMinutes())
        settings.setAutoLockMinutes(61); assertEquals(60, settings.getAutoLockMinutes())
        settings.putInt(AppConstants.SETTING_AUTO_LOCK_MINUTES, Int.MIN_VALUE)
        assertEquals(1, settings.getAutoLockMinutes())
        settings.putInt(AppConstants.SETTING_AUTO_LOCK_MINUTES, Int.MAX_VALUE)
        assertEquals(60, settings.getAutoLockMinutes())
    }
    @Test fun batchPreferenceWritePreservesOtherKeysAndObservesDeletion() = runBlocking<Unit> {
        val settings = settings()
        settings.put("keep", "clinical marker")
        settings.putAll(mapOf("density" to "COMPACT", "motion" to "true"))
        assertEquals(mapOf("keep" to "clinical marker", "density" to "COMPACT", "motion" to "true"), settings.getAll())
        assertEquals("COMPACT", settings.observe("density").first())
        settings.delete("density")
        assertNull(settings.observe("density").first())
        assertEquals("clinical marker", settings.get("keep"))
    }
}
