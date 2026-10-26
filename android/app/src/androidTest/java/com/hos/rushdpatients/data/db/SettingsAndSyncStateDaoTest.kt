package com.hos.rushdpatients.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

import com.hos.rushdpatients.testutil.DatabaseFixture
import com.hos.rushdpatients.data.db.entity.SettingEntity
import com.hos.rushdpatients.data.db.entity.SyncStateEntity
import kotlinx.coroutines.flow.first

@RunWith(AndroidJUnit4::class)
class SettingsAndSyncStateDaoTest : DatabaseFixture() {
    @Test fun settingsUpsertObserveAndDeleteAreKeyScoped() = runBlocking<Unit> {
        val dao = db.settingDao()
        dao.putAll(listOf(SettingEntity("a", "one"), SettingEntity("b", "two")))
        dao.put(SettingEntity("a", "changed"))
        assertEquals("changed", dao.observe("a").first())
        dao.delete("a")
        assertNull(dao.get("a"))
        assertEquals("two", dao.get("b"))
    }
    @Test fun syncChannelsRetainIndependentOffsetsAndReceipts() = runBlocking<Unit> {
        val dao = db.syncStateDao()
        dao.upsert(SyncStateEntity("patients", 1, null, null, null))
        dao.upsert(SyncStateEntity("doctors", 2, null, null, null))
        dao.update("patients", 10, 20, "synthetic-file", 30)
        assertEquals(SyncStateEntity("patients", 10, 20, "synthetic-file", 30), dao.observe("patients").first())
        assertEquals(2L, dao.get("doctors")?.lastOffset)
        dao.delete("patients")
        assertNull(dao.get("patients"))
        assertNotNull(dao.get("doctors"))
    }
}
