package com.hos.rushdpatients.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.data.db.dao.PatientDao
import com.hos.rushdpatients.data.db.entity.PatientEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatientDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PatientDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.patientDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        id: String,
        name: String = "مريض",
        sortOrder: Int = 1,
        deleted: Boolean = false
    ) = PatientEntity(
        id = id,
        shiftId = "shift-1",
        name = name,
        age = 30,
        gender = "M",
        diagnosis = "",
        condition = "",
        hasEscort = false,
        admittanceNumber = "",
        admittanceDateEpochDay = null,
        admittanceDays = null,
        treatment = "",
        labs = "",
        notes = "",
        residentId = null,
        supervisorId = null,
        sortOrder = sortOrder,
        updatedAtEpochMillis = 1_000L,
        deletedAtEpochMillis = if (deleted) 2_000L else null
    )

    @Test
    fun `upsert and read back`() = runTest {
        dao.upsert(entity("p1", name = "محمد"))
        val fetched = dao.getById("p1")
        assertEquals("محمد", fetched?.name)
    }

    @Test
    fun `getForShift returns only that shift`() = runTest {
        dao.upsert(entity("p1").copy(shiftId = "shift-1"))
        dao.upsert(entity("p2").copy(shiftId = "shift-2"))
        val forShift1 = dao.getForShift("shift-1")
        assertEquals(1, forShift1.size)
        assertEquals("p1", forShift1.first().id)
    }

    @Test
    fun `soft delete hides from observeForShift`() = runTest {
        dao.upsert(entity("p1"))
        dao.softDelete("p1", deletedAtMillis = 5_000L)
        val visible = dao.observeForShift("shift-1").first()
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `soft delete shows up in getSoftDeleted`() = runTest {
        dao.upsert(entity("p1"))
        dao.softDelete("p1", deletedAtMillis = 5_000L)
        val deleted = dao.getSoftDeleted()
        assertEquals(1, deleted.size)
        assertEquals("p1", deleted.first().id)
    }

    @Test
    fun `count excludes soft deleted`() = runTest {
        dao.upsert(entity("p1"))
        dao.upsert(entity("p2"))
        dao.softDelete("p2", deletedAtMillis = 1L)
        assertEquals(1, dao.countForShift("shift-1"))
    }

    @Test
    fun `purge removes old soft deleted rows`() = runTest {
        dao.upsert(entity("p1"))
        dao.upsert(entity("p2"))
        dao.softDelete("p1", deletedAtMillis = 1_000L)
        dao.softDelete("p2", deletedAtMillis = 5_000L)
        val removed = dao.purgeOlderThan(cutoffMillis = 2_000L)
        assertEquals(1, removed)
        assertNull(dao.getById("p1"))
        assertTrue(dao.getById("p2") != null)
    }

    @Test
    fun `upsertAll writes every row`() = runTest {
        val list = (1..5).map { entity("p$it", sortOrder = it) }
        dao.upsertAll(list)
        assertEquals(5, dao.countForShift("shift-1"))
    }

    @Test
    fun `updateSortOrder changes only one row`() = runTest {
        dao.upsert(entity("p1", sortOrder = 1))
        dao.upsert(entity("p2", sortOrder = 2))
        dao.updateSortOrder("p1", 99)
        assertEquals(99, dao.getById("p1")?.sortOrder)
        assertEquals(2, dao.getById("p2")?.sortOrder)
    }

    @Test
    fun `getModifiedSince returns only newer rows`() = runTest {
        dao.upsert(entity("p1").copy(updatedAtEpochMillis = 1_000L))
        dao.upsert(entity("p2").copy(updatedAtEpochMillis = 5_000L))
        val modified = dao.getModifiedSince(2_000L)
        assertEquals(1, modified.size)
        assertEquals("p2", modified.first().id)
    }
}