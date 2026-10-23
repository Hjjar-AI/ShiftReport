package com.hos.rushdpatients.testutil

import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.*
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.fail
import java.time.Instant

/** Real authorization and repositories with private preferences and a fresh database. */
abstract class ClinicalRepositoryFixture : DatabaseFixture() {
    protected val dispatchers = DefaultDispatcherProvider()
    protected lateinit var preferences: IsolatedStorageContext
    protected lateinit var patients: PatientRepository
    protected lateinit var doctors: DoctorRepository
    protected lateinit var shifts: ShiftRepository
    protected lateinit var settings: SettingsRepository
    protected lateinit var audit: AuditRepository
    protected lateinit var sessions: SessionManager
    protected lateinit var authorizer: AdminAuthorizer
    protected lateinit var mutations: DoctorMutationRepository
    protected lateinit var admin: Doctor
    @Before fun openRepositories() = runBlocking<Unit> {
        preferences = IsolatedStorageContext()
        patients = PatientRepository(db.patientDao(), db, dispatchers)
        settings = SettingsRepository(db.settingDao(), dispatchers)
        doctors = DoctorRepository(db.doctorDao(), patients, db, settings, dispatchers)
        shifts = ShiftRepository(db.shiftDao(), dispatchers)
        audit = AuditRepository(db.auditDao(), dispatchers)
        sessions = SessionManager(preferences)
        authorizer = AdminAuthorizer(sessions, doctors)
        mutations = DoctorMutationRepository(db, doctors, patients, authorizer, PasswordHasher(), audit, dispatchers)
        doctors.upsert(doctor("admin", "أرز", rank = 10).copy(isPermanentAdmin = true))
        admin = requireNotNull(doctors.getById("admin"))
        login(admin)
        settings.putBoolean(com.hos.rushdpatients.config.AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
    }
    protected fun doctor(id: String, name: String = "بلوط", rank: Int = 0) = Doctor(id = id,
        fullName = "د. $name شجرة", firstName = name, lastName = "شجرة", gender = Gender.MALE,
        rank = rank, telegramId = null)
    protected fun login(doctor: Doctor) = sessions.start(Session(doctor.id, doctor.fullName,
        doctor.telegramId, doctor.role, Instant.now()))
    protected suspend fun rejected(block: suspend () -> Unit): Exception {
        try { block() } catch (e: Exception) { return e }
        fail("Unsafe operation unexpectedly succeeded")
        error("unreachable")
    }
    @After fun clearPrivatePreferences() { if (::preferences.isInitialized) preferences.cleanUp() }
}
