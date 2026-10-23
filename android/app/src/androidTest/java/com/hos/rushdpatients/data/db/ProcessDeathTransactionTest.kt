package com.hos.rushdpatients.data.db

import android.content.*
import android.os.*
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.data.db.entity.SettingEntity
import com.hos.rushdpatients.testutil.TransactionCrashService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProcessDeathTransactionTest {
    private fun crashAndReopen(commit: Boolean) = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "instrumentation-crash-${UUID.randomUUID()}"
        val connected = CountDownLatch(1); val died = CountDownLatch(1)
        var messenger: Messenger? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { messenger = Messenger(binder); connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) { died.countDown() }
        }
        var bound = false
        try {
            Room.databaseBuilder(context, AppDatabase::class.java, name).build().let { db ->
                try { db.settingDao().put(SettingEntity("clinical-marker", "old")) }
                finally { db.close() }
            }
            bound = context.bindService(Intent(context, TransactionCrashService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound)
            assertTrue("Crash worker did not connect", connected.await(10, TimeUnit.SECONDS))
            messenger!!.send(Message.obtain().apply {
                what = if (commit) 2 else 1
                data = Bundle().apply { putString("database", name) }
            })
            assertTrue("Crash worker did not exit", died.await(15, TimeUnit.SECONDS))
            Room.databaseBuilder(context, AppDatabase::class.java, name).build().let { db ->
                try {
                    assertEquals(if (commit) "new" else "old", db.settingDao().get("clinical-marker"))
                    assertEquals(if (commit) "true" else null, db.settingDao().get("pending-marker"))
                } finally { db.close() }
            }
        } finally {
            if (bound) context.unbindService(connection)
            context.deleteDatabase(name)
        }
    }
    @Test fun processDeathBeforeCommitPreservesOldStateWithoutPartialPendingMarker() = crashAndReopen(false)
    @Test fun processDeathAfterCommitPreservesBothNewStateAndPendingMarker() = crashAndReopen(true)
}
