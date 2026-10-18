package com.hos.rushdpatients.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.data.db.dao.DoctorDao
import com.hos.rushdpatients.data.db.entity.DoctorEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoctorDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DoctorDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.doctorDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        id: String,
        fullName: String = "د. أحمد",
        telegramId: Long? = null,
        rank: Int = 0,
        isPermanentAdmin: Boolean = false,
        deleted: Boolean = false
    ) = DoctorEntity(
        id = id,
        fullName = fullName,
        firstName = "أحمد",
        lastName = "علي",
        gender = "M",
        telegramId = telegramId,
        telegramUsername = null,
        customTitle = null,
        rank = rank,
        isPermanentAdmin = isPermanentAdmin,
        extraOptions = "",
        updatedAtEpochMillis = 1_000L,
        deletedAtEpochMillis = if (deleted) 2_000L else null
    )

    @Test
    fun `upsert and read back`() = runTest {
        dao.upsert(entity("d1", fullName = "د. علي"))
        assertEquals("د. علي", dao.getById("d1")?.fullName)
    }

    @Test
    fun `getByTelegramId finds matching doctor`() = runTest {
        dao.upsert(entity("d1", telegramId = 100L))
        dao.upsert(entity("d2", telegramId = 200L))
        val found = dao.getByTelegramId(200L)
        assertEquals("d2", found?.id)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `unique telegram id rejects second insert with different id`() = runTest {
        dao.upsert(entity("d1", fullName = "د. أحمد", telegramId = 42L))
        dao.upsert(entity("d2", fullName = "د. سارة", telegramId = 42L))
    }

    @Test
    fun `upsert with same primary key replaces row`() = runTest {
        dao.upsert(entity("d1", telegramId = 42L, rank = 0))
        dao.upsert(entity("d1", telegramId = 42L, rank = 1))
        val row = dao.getById("d1")
        assertEquals(1, row?.rank)
        assertEquals(1, dao.getAll().size)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `unique full name rejects second insert with different id`() = runTest {
        dao.upsert(entity("d1", fullName = "د. أحمد"))
        dao.upsert(entity("d2", fullName = "د. أحمد"))
    }

    @Test
    fun `getAdmins returns only rank greater than zero`() = runTest {
        dao.upsert(entity("d1", fullName = "أ", rank = 0))
        dao.upsert(entity("d2", fullName = "ب", rank = 1))
        dao.upsert(entity("d3", fullName = "ج", rank = 2, isPermanentAdmin = true))
        val admins = dao.getAdmins()
        assertEquals(2, admins.size)
        assertTrue(admins.all { it.rank > 0 })
    }

    @Test
    fun `getNonAdmins returns only rank zero`() = runTest {
        dao.upsert(entity("d1", fullName = "أ", rank = 0))
        dao.upsert(entity("d2", fullName = "ب", rank = 1))
        val nonAdmins = dao.getNonAdmins()
        assertEquals(1, nonAdmins.size)
        assertEquals(0, nonAdmins.first().rank)
    }

    @Test
    fun `getMaxRank returns highest rank`() = runTest {
        dao.upsert(entity("d1", fullName = "أ", rank = 1))
        dao.upsert(entity("d2", fullName = "ب", rank = 5))
        dao.upsert(entity("d3", fullName = "ج", rank = 3))
        assertEquals(5, dao.getMaxRank())
    }

    @Test
    fun `getMaxRank returns zero when no doctors`() = runTest {
        assertEquals(0, dao.getMaxRank())
    }

    @Test
    fun `soft delete clears telegram id and rank`() = runTest {
        dao.upsert(entity("d1", telegramId = 999L, rank = 3))
        dao.softDelete("d1", deletedAtMillis = 5_000L)
        val after = dao.getById("d1")
        assertNotNull(after)
        assertNull(after?.telegramId)
        assertEquals(0, after?.rank)
    }

    @Test
    fun `soft delete hides from getAll`() = runTest {
        dao.upsert(entity("d1", fullName = "أ"))
        dao.upsert(entity("d2", fullName = "ب"))
        dao.softDelete("d1", deletedAtMillis = 1L)
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("d2", all.first().id)
    }

    @Test
    fun `count excludes soft deleted`() = runTest {
        dao.upsert(entity("d1", fullName = "أ"))
        dao.upsert(entity("d2", fullName = "ب"))
        dao.softDelete("d1", deletedAtMillis = 1L)
        assertEquals(1, dao.count())
    }

    @Test
    fun `countAll counts everything including soft deleted`() = runTest {
        dao.upsert(entity("d1", fullName = "أ"))
        dao.upsert(entity("d2", fullName = "ب"))
        dao.softDelete("d1", deletedAtMillis = 1L)
        assertEquals(2, dao.countAll())
    }
}