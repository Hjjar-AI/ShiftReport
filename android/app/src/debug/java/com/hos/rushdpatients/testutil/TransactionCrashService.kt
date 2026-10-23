package com.hos.rushdpatients.testutil

import android.app.Service
import android.content.Intent
import android.os.*
import androidx.room.Room
import androidx.room.withTransaction
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.db.entity.SettingEntity
import kotlinx.coroutines.runBlocking

/** Dies only in the debug app's dedicated test process, using a uniquely named test database. */
class TransactionCrashService : Service() {
    override fun onBind(intent: Intent): IBinder = Messenger(Handler(Looper.getMainLooper()) { message ->
        val name = message.data.getString("database") ?: return@Handler true
        require(name.startsWith("instrumentation-crash-"))
        val crashBeforeCommit = message.what == 1
        Thread {
            runBlocking {
                val db = Room.databaseBuilder(this@TransactionCrashService, AppDatabase::class.java, name).build()
                db.withTransaction {
                    db.settingDao().put(SettingEntity("clinical-marker", "new"))
                    db.settingDao().put(SettingEntity("pending-marker", "true"))
                    if (crashBeforeCommit) Process.killProcess(Process.myPid())
                }
                Process.killProcess(Process.myPid())
            }
        }.start()
        true
    }).binder
}
