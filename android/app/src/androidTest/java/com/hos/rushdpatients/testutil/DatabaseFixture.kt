package com.hos.rushdpatients.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.mapper.ShiftMapper
import com.hos.rushdpatients.data.model.Shift
import java.time.Instant
import java.time.LocalDate
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.runBlocking

/** Fresh production schema only; never opens or upgrades an installed clinical database. */
abstract class DatabaseFixture {
    protected lateinit var db: AppDatabase
    @Before fun openDatabase() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        seedShift("shift-1", LocalDate.of(2026, 1, 1))
        seedShift("shift-2", LocalDate.of(2026, 1, 2))
    }
    protected suspend fun seedShift(id: String, date: LocalDate) {
        db.shiftDao().upsert(ShiftMapper.toEntity(Shift(id = id, date = date, doctorIds = emptyList(),
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)))
    }
    @After fun closeDatabase() { if (::db.isInitialized) db.close() }
}
