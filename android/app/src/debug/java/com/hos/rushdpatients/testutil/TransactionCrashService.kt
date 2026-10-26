package com.hos.rushdpatients.testutil

import android.app.Service
import android.content.Intent
import android.os.*
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.db.entity.SettingEntity
import kotlinx.coroutines.runBlocking

/** Dies only in the debug app's dedicated test process, using a uniquely named test database. */
class TransactionCrashService : Service() {
    override fun onBind(intent: Intent): IBinder = Messenger(Handler(Looper.getMainLooper()) { message ->
        val name = message.data.getString("database") ?: return@Handler true
        require(name.startsWith("instrumentation-crash-"))
        require(java.util.UUID.fromString(name.removePrefix("instrumentation-crash-")).toString() == name.removePrefix("instrumentation-crash-"))
        val mode = message.what
        val reply = message.replyTo
        val backupUri = message.data.getString("backupUri")
        Thread {
            runBlocking {
                val die: () -> Nothing = {
                    reply?.send(Message.obtain().apply { what = 101; arg1 = mode })
                    Process.killProcess(Process.myPid())
                    error("Crash worker unexpectedly survived")
                }
                val beforeWorkflowCommit = mode == 5 || mode == 7
                val db = Room.databaseBuilder(this@TransactionCrashService, AppDatabase::class.java, name)
                    .setQueryCallback(RoomDatabase.QueryCallback { sql, _ ->
                        // Both real operations write patients before upserting the shift in the same transaction.
                        // Kill here to prove those already-executed clinical writes roll back too.
                        val statement = sql.trimStart().uppercase(java.util.Locale.ROOT)
                        if (beforeWorkflowCommit && (statement.startsWith("INSERT") || statement.startsWith("UPDATE")) &&
                            statement.contains("`SHIFTS`")) die()
                    }, java.util.concurrent.Executor { it.run() }).build()
                val workflow = if (mode >= 3) CrashWorkflowHarness(CrashPreferencesContext(this@TransactionCrashService, name), db) else null
                when (mode) {
                    3, 4 -> { workflow!!.receiptAndDie(mode == 4, die); return@runBlocking }
                    5, 6 -> { workflow!!.restoreAndDie(android.net.Uri.parse(requireNotNull(backupUri)), mode == 5, die); return@runBlocking }
                    7, 8 -> { workflow!!.undoAndDie(mode == 7, die); return@runBlocking }
                    9, 10 -> { workflow!!.exportAndDie(mode == 10, die); return@runBlocking }
                    11 -> { workflow!!.unconfirmedDeliveryAndDie(die); return@runBlocking }
                }
                require(mode == 1 || mode == 2)
                db.withTransaction {
                    db.settingDao().put(SettingEntity("clinical-marker", "new"))
                    db.settingDao().put(SettingEntity("pending-marker", "true"))
                    if (mode == 1) die()
                }
                die()
            }
        }.start()
        true
    }).binder
}
