package com.hos.rushdpatients.testutil

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.domain.backup.EncryptedBackupManager
import com.hos.rushdpatients.domain.report.ReportDeliveryLedger
import com.hos.rushdpatients.sync.LocalDownloadUndo
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import kotlinx.serialization.json.Json
import java.time.Instant

/** Debug-only dependencies for actual production operations against a uniquely named test DB. */
internal class CrashWorkflowHarness(val context: Context, val db: AppDatabase) {
    val dispatchers = DefaultDispatcherProvider()
    val patients = PatientRepository(db.patientDao(), db, dispatchers)
    val settings = SettingsRepository(db.settingDao(), dispatchers)
    val doctors = DoctorRepository(db.doctorDao(), patients, db, settings, dispatchers)
    val shifts = ShiftRepository(db.shiftDao(), dispatchers)
    val audit = AuditRepository(db.auditDao(), dispatchers)
    val sessions = SessionManager(context)
    val ledger = ReportDeliveryLedger(settings, sessions, doctors)
    val backup = EncryptedBackupManager(context, db, shifts, patients, doctors, settings, dispatchers)
    val undo = LocalDownloadUndo(db, shifts, patients, settings, audit, sessions, dispatchers, Json)

    suspend fun signIn() {
        if (doctors.getById("crash-doctor") == null) {
            doctors.upsert(Doctor(id = "crash-doctor", fullName = "د. سدر شجرة", firstName = "سدر",
                lastName = "شجرة", gender = Gender.MALE))
        }
        val actor = requireNotNull(doctors.getById("crash-doctor"))
        sessions.start(Session(actor.id, actor.fullName, null, actor.role, Instant.now()))
    }

    suspend fun receiptAndDie(confirm: Boolean, die: () -> Nothing) {
        signIn()
        ledger.deliver("crash-shift", -100, "synthetic-content", "pdf", "synthetic crash", "crash-doctor") { record ->
            if (confirm) ledger.confirm(record.id, listOf(42L))
            die()
        }
    }

    suspend fun restoreAndDie(uri: Uri, beforeCommit: Boolean, die: () -> Nothing) {
        backup.restore(uri, "synthetic-password".toCharArray())
        check(!beforeCommit) { "Precommit kill boundary was not reached" }
        die()
    }

    suspend fun undoAndDie(beforeCommit: Boolean, die: () -> Nothing) {
        undo.undo()
        check(!beforeCommit) { "Precommit kill boundary was not reached" }
        die()
    }
}

/** Child-process session preferences never share the installed project's namespace. */
internal class CrashPreferencesContext(context: Context, private val prefix: String) : ContextWrapper(context) {
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("${prefix}_$name", mode)
}
