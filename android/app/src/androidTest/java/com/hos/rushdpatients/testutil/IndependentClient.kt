package com.hos.rushdpatients.testutil

import androidx.room.Room
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.*
import com.hos.rushdpatients.domain.report.*
import com.hos.rushdpatients.sync.*
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import java.time.Instant
import java.time.LocalDate

/** Own Room, preferences, session, cursor, undo and locks; only the remote is shared. */
class IndependentClient(val label: String, val server: SharedTelegramServer) : AutoCloseable {
    val context = IsolatedStorageContext()
    val dispatchers = DefaultDispatcherProvider()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val settings = SettingsRepository(db.settingDao(), dispatchers)
    val patients = PatientRepository(db.patientDao(), db, dispatchers)
    val shifts = ShiftRepository(db.shiftDao(), dispatchers)
    val doctors = DoctorRepository(db.doctorDao(), patients, db, settings, dispatchers)
    val audit = AuditRepository(db.auditDao(), dispatchers)
    val sessions = SessionManager(context)
    val authorizer = AdminAuthorizer(sessions, doctors)
    val projects = ProjectConfigStore(context).apply {
        saveDraft(ProjectConfig(hospitalName = "مشفى تجريبي", botToken = "12345:" + "a".repeat(24),
            chatId = -100, csvTopicId = 3, doctorsTopicId = 4, reportsTopicId = 2)); markInitialized()
    }
    val topics = Topics(projects)
    val telegram = server.client(label, projects, dispatchers)
    val cursors = SyncStateRepository(db.syncStateDao(), dispatchers)
    val remote = RemoteSyncStateStore(telegram, topics, server.json)
    val undo = LocalDownloadUndo(db, shifts, patients, settings, audit, sessions, dispatchers, server.json)
    val doctorSync = DoctorSyncCoordinator(context, telegram, topics, doctors, cursors, settings, db, audit,
        authorizer, sessions, dispatchers, remote)
    val reports = ReportBuilder(patients, doctors, shifts, TextReportBuilder(), db, dispatchers)
    val sync = SyncService(context, telegram, topics, shifts, patients, doctors, cursors, settings, db,
        audit, dispatchers, remote, doctorSync, reports, undo)
    val wardMutations = WardMutationRepository(db, patients, doctors, shifts, settings, audit, dispatchers)
    val doctorMutations = DoctorMutationRepository(db, doctors, patients, authorizer, PasswordHasher(), audit, dispatchers)
    val actor = Doctor(id = "cedar", fullName = "د. أرز شجرة", firstName = "أرز", lastName = "شجرة",
        gender = Gender.MALE, telegramId = 123, rank = 1, isPermanentAdmin = true, updatedAt = Instant.EPOCH)
    suspend fun initialize() {
        doctors.upsert(actor)
        sessions.start(Session(actor.id, actor.fullName, actor.telegramId, actor.role, Instant.now()))
        settings.put(AppConstants.SETTING_DOCTORS_BASE_REGISTRY, DoctorsRegistryCodec.encode(listOf(actor)))
        settings.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
        settings.put(AppConstants.SETTING_DEVICE_ID, "synthetic-$label")
    }
    suspend fun seedPatient(): Patient {
        val shift = Shift(id = "today", date = ShiftDate.current(), doctorIds = listOf(actor.id), revision = 1)
        shifts.upsert(shift)
        val patient = Patient(id = "tea", name = "شاي", admittanceNumber = "1", admittanceDate = shift.date,
            birthDate = LocalDate.of(1990, 1, 1), initialDiagnosis = "تشخيص تجريبي", treatmentPlan = "خطة",
            followUp = "متابعة", sortOrder = 1, revision = 1)
        patients.upsert(patient, shift.id)
        settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true)
        return patient
    }
    suspend fun publish() = sync.uploadReviewedCsv(sync.prepareCsvPublication("today", forceLocal = true), forceLocal = true).getOrThrow()
    override fun close() {
        try { db.close() } finally { context.cleanUp() }
    }
}
