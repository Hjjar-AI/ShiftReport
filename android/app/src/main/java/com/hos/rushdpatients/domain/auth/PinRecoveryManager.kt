package com.hos.rushdpatients.domain.auth

import android.os.SystemClock
import com.hos.rushdpatients.R
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.ConnectionChange
import com.hos.rushdpatients.config.ProjectConfig
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.config.ProjectConnectionManager
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.sync.DoctorSyncCoordinator
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.util.DispatcherProvider
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Opaque, memory-only handle; it contains no connection credentials or PIN. */
class PinRecoveryAuthorization internal constructor()

/** Both protected-file reconnection and Telegram proof are required before replacing a local PIN. */
@Singleton
class PinRecoveryManager @Inject constructor(
    private val connections: ProjectConnectionManager,
    private val projects: ProjectConfigStore,
    private val doctors: DoctorRepository,
    private val doctorSync: DoctorSyncCoordinator,
    private val telegramLogin: TelegramLoginManager,
    private val localPins: LocalPinLoginManager,
    private val dispatchers: DispatcherProvider,
    private val uiText: UiStrings
) {
    private class Grant(val handle: PinRecoveryAuthorization, val project: ProjectConfig,
        val doctor: Doctor, val expiresAt: Long, var verified: Boolean = false)
    private val active = AtomicReference<Grant?>(null)
    private val mutex = Mutex()

    internal suspend fun confirmRejoin(change: ConnectionChange, doctorId: String): PinRecoveryAuthorization =
        withContext(dispatchers.io) {
            mutex.withLock {
                require(change.fromProtectedFile) { uiText(R.string.pin_recovery_file_required) }
                active.set(null)
                connections.apply(change)
                val project = projects.current()
                doctorSync.refreshDoctorsForLogin().getOrThrow()
                check(projects.current() == project) { uiText(R.string.login_connection_changed) }
                val doctor = requireNotNull(doctors.getActiveById(doctorId)) { uiText(R.string.pin_recovery_doctor_unavailable) }
                require(doctor.telegramId != null) { uiText(R.string.ui_login_view_model_005) }
                val handle = PinRecoveryAuthorization()
                active.set(Grant(handle, project, doctor,
                    SystemClock.elapsedRealtime() + AppConstants.TELEGRAM_LOGIN_TIMEOUT_MS))
                handle
            }
        }

    fun cancel(handle: PinRecoveryAuthorization) {
        val grant = active.get()
        if (grant?.handle === handle) active.compareAndSet(grant, null)
    }

    private fun requireGrant(handle: PinRecoveryAuthorization): Grant {
        val grant = active.get()
        require(grant != null && grant.handle === handle) { uiText(R.string.pin_recovery_expired) }
        if (SystemClock.elapsedRealtime() >= grant.expiresAt || projects.current() != grant.project) {
            active.compareAndSet(grant, null)
            error(uiText(R.string.pin_recovery_expired))
        }
        return grant
    }

    private fun invalidate(grant: Grant): Nothing {
        active.compareAndSet(grant, null)
        error(uiText(R.string.pin_recovery_identity_changed))
    }

    private suspend fun currentDoctor(grant: Grant): Doctor {
        val doctor = doctors.getActiveById(grant.doctor.id) ?: invalidate(grant)
        if (doctor.telegramId != grant.doctor.telegramId || doctor.options.pinHash != grant.doctor.options.pinHash) {
            invalidate(grant)
        }
        return doctor
    }

    suspend fun beginVerification(handle: PinRecoveryAuthorization, doctorId: String): Doctor = withContext(dispatchers.io) {
        mutex.withLock {
            val grant = requireGrant(handle)
            require(grant.doctor.id == doctorId) { uiText(R.string.pin_recovery_identity_changed) }
            val doctor = currentDoctor(grant)
            grant.verified = false
            telegramLogin.beginVerification(doctor.id, checkNotNull(doctor.telegramId))
            requireGrant(handle)
            doctor
        }
    }

    suspend fun verify(handle: PinRecoveryAuthorization, doctorId: String, telegramId: Long,
        code: String): LoginVerificationResult = withContext(dispatchers.io) {
        mutex.withLock {
            val grant = requireGrant(handle)
            require(grant.doctor.id == doctorId && grant.doctor.telegramId == telegramId) {
                uiText(R.string.pin_recovery_identity_changed)
            }
            val doctor = currentDoctor(grant)
            val result = telegramLogin.verify(doctor.id, checkNotNull(doctor.telegramId), code)
            requireGrant(handle)
            if (result is LoginVerificationResult.Success) grant.verified = true
            if (result == LoginVerificationResult.Timeout || result == LoginVerificationResult.Exhausted) {
                active.compareAndSet(grant, null)
            }
            result
        }
    }

    suspend fun verifiedDoctor(handle: PinRecoveryAuthorization): Doctor = withContext(dispatchers.io) {
        mutex.withLock {
            val grant = requireGrant(handle)
            require(grant.verified) { uiText(R.string.pin_recovery_verification_required) }
            currentDoctor(grant)
        }
    }

    suspend fun replacePin(handle: PinRecoveryAuthorization, doctorId: String, pin: String): Doctor = withContext(dispatchers.io) {
        mutex.withLock {
            val grant = requireGrant(handle)
            require(grant.doctor.id == doctorId) { uiText(R.string.pin_recovery_identity_changed) }
            require(grant.verified) { uiText(R.string.pin_recovery_verification_required) }
            val doctor = currentDoctor(grant)
            val updated = localPins.enrollVerified(doctor.id, checkNotNull(doctor.telegramId), pin) { current ->
                check(requireGrant(handle) === grant)
                require(current.options.pinHash == grant.doctor.options.pinHash) { uiText(R.string.pin_recovery_identity_changed) }
            }
            active.compareAndSet(grant, null)
            updated
        }
    }
}
