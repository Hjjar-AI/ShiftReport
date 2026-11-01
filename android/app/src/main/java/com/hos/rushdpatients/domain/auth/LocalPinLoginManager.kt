package com.hos.rushdpatients.domain.auth

import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class LocalPinFailure { INVALID_FORMAT, UNAVAILABLE, NOT_ENROLLED, NOT_LINKED, STATE_UNREADABLE, BLOCKED, INCORRECT, ACCOUNT_CHANGED }
class LocalPinException(val reason: LocalPinFailure, val waitMinutes: Long = 0) : IllegalStateException(reason.name)

/** Re-entry on an enrolled device; never creates a PIN or enrolls a new identity. */
@Singleton
class LocalPinLoginManager @Inject constructor(
    private val doctors: DoctorRepository,
    private val settings: SettingsRepository,
    private val hasher: PasswordHasher,
    private val dispatchers: DispatcherProvider,
    private val database: com.hos.rushdpatients.data.db.AppDatabase
) {
    private val mutex = Mutex()

    private fun enrollmentKey(id: String) = "local_pin_enrollment:$id"
    private fun enrollmentIdentity(doctor: Doctor): String {
        val text = "${doctor.id}|${doctor.telegramId}|${doctor.options.pinHash}"
        return java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    suspend fun isEnrolled(doctor: Doctor): Boolean = withContext(dispatchers.io) {
        doctor.telegramId != null && doctor.options.pinHash != null &&
            settings.get(enrollmentKey(doctor.id)) == enrollmentIdentity(doctor)
    }

    /** Called after Telegram proof; administrators' preset PINs never enroll another identity. */
    suspend fun enrollVerified(doctorId: String, telegramId: Long, pin: String): Doctor = withContext(dispatchers.io) {
        require(pin.length in AppConstants.PIN_MIN_LENGTH..AppConstants.PIN_MAX_LENGTH && pin.all(Char::isDigit))
        val hash = hasher.hash(pin)
        mutex.withLock {
            database.withTransaction {
                val doctor = requireNotNull(doctors.getActiveById(doctorId)) { "الطبيب غير متاح" }
                require(doctor.telegramId == telegramId) { "تغيرت هوية الطبيب؛ أعد التحقق عبر تليجرام" }
                val updated = doctor.copy(extraOptions = doctor.options.withPinHash(hash))
                doctors.upsert(updated)
                settings.put(enrollmentKey(doctorId), enrollmentIdentity(updated))
                settings.delete("local_pin_login:$doctorId")
                requireNotNull(doctors.getActiveById(doctorId))
            }
        }
    }

    suspend fun verify(doctorId: String, pin: String): Doctor = withContext(dispatchers.io) {
        mutex.withLock {
            if (pin.length !in AppConstants.PIN_MIN_LENGTH..AppConstants.PIN_MAX_LENGTH ||
                !pin.all(Char::isDigit)) throw LocalPinException(LocalPinFailure.INVALID_FORMAT)
            val doctor = doctors.getActiveById(doctorId) ?: throw LocalPinException(LocalPinFailure.UNAVAILABLE)
            val hash = doctor.options.pinHash ?: throw LocalPinException(LocalPinFailure.NOT_ENROLLED)
            if (doctor.telegramId == null) throw LocalPinException(LocalPinFailure.NOT_LINKED)
            if (!isEnrolled(doctor)) throw LocalPinException(LocalPinFailure.NOT_ENROLLED)

            // Room is encrypted. Persist only counters/deadlines, never the submitted PIN.
            val key = "local_pin_login:$doctorId"
            val stored = settings.get(key)?.split(':') ?: listOf("0", "0")
            if (stored.size !in 2..3) throw LocalPinException(LocalPinFailure.STATE_UNREADABLE)
            val previousAttempts = stored[0].toIntOrNull()?.takeIf { it >= 0 }
                ?: throw LocalPinException(LocalPinFailure.STATE_UNREADABLE)
            val previousBlockedUntil = stored[1].toLongOrNull()?.takeIf { it >= 0 }
                ?: throw LocalPinException(LocalPinFailure.STATE_UNREADABLE)
            val level = if (stored.size == 2) 0 else stored[2].toIntOrNull()?.takeIf { it in 0..3 }
                ?: throw LocalPinException(LocalPinFailure.STATE_UNREADABLE)
            val now = System.currentTimeMillis()
            if (now < previousBlockedUntil) throw LocalPinException(LocalPinFailure.BLOCKED,
                (previousBlockedUntil - now + 59_999) / 60_000)
            val attempts = (if (previousBlockedUntil > 0) 0 else previousAttempts) + 1
            val nextLevel = if (attempts >= AppConstants.LOGIN_MAX_ATTEMPTS) (level + 1).coerceAtMost(3) else level
            val duration = listOf(5L, 15L, 60L)[(nextLevel - 1).coerceAtLeast(0)]
            val blockedUntil = if (attempts >= AppConstants.LOGIN_MAX_ATTEMPTS) now + duration * 60_000 else 0L
            // Count before hashing so cancellation/recreation cannot erase a submitted attempt.
            withContext(NonCancellable) { settings.put(key, "$attempts:$blockedUntil:$nextLevel") }
            if (!hasher.verify(pin, hash)) throw if (blockedUntil > 0)
                LocalPinException(LocalPinFailure.BLOCKED, duration) else LocalPinException(LocalPinFailure.INCORRECT)

            val current = doctors.getActiveById(doctorId) ?: throw LocalPinException(LocalPinFailure.UNAVAILABLE)
            if (current.telegramId != doctor.telegramId ||
                current.options.pinHash != hash || !isEnrolled(current))
                throw LocalPinException(LocalPinFailure.ACCOUNT_CHANGED)
            settings.delete(key)
            // Session privileges come from the live record, not the selected card's snapshot.
            current
        }
    }

}
