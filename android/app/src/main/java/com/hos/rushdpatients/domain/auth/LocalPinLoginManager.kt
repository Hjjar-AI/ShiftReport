package com.hos.rushdpatients.domain.auth

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

/** Re-entry on an enrolled device; never creates a PIN or enrolls a new identity. */
@Singleton
class LocalPinLoginManager @Inject constructor(
    private val doctors: DoctorRepository,
    private val settings: SettingsRepository,
    private val hasher: PasswordHasher,
    private val dispatchers: DispatcherProvider
) {
    private val mutex = Mutex()

    suspend fun verify(doctorId: String, pin: String): Doctor = withContext(dispatchers.io) {
        mutex.withLock {
            require(pin.length in AppConstants.PIN_MIN_LENGTH..AppConstants.PIN_MAX_LENGTH &&
                pin.all(Char::isDigit)) { "الرقم السري يجب أن يكون من 4 إلى 8 أرقام" }
            val doctor = doctors.getActiveById(doctorId) ?: error("الطبيب لم يعد متاحاً في السجل")
            val hash = doctor.extraOptions.firstOrNull { it.startsWith("pin:") }
                ?.removePrefix("pin:") ?: error("تحقق عبر تليجرام أولاً لتعيين رقم سري على هذا الجهاز")
            require(doctor.telegramId != null) { "حساب تليجرام لهذا الطبيب غير مرتبط" }

            // Room is encrypted. Persist only counters/deadlines, never the submitted PIN.
            val key = "local_pin_login:$doctorId"
            val stored = settings.get(key)?.split(':') ?: listOf("0", "0")
            check(stored.size == 2) { "تعذر قراءة حالة محاولات الدخول" }
            val previousAttempts = stored[0].toInt()
            val previousBlockedUntil = stored[1].toLong()
            val now = System.currentTimeMillis()
            require(now >= previousBlockedUntil) {
                "محاولات خاطئة متعددة؛ انتظر خمس دقائق أو تحقق عبر تليجرام"
            }
            val attempts = (if (previousBlockedUntil > 0) 0 else previousAttempts) + 1
            val blockedUntil = if (attempts >= AppConstants.LOGIN_MAX_ATTEMPTS) now + LOCKOUT_MS else 0L
            // Count before hashing so cancellation/recreation cannot erase a submitted attempt.
            withContext(NonCancellable) { settings.put(key, "$attempts:$blockedUntil") }
            require(hasher.verify(pin, hash)) {
                if (blockedUntil > 0) "محاولات خاطئة متعددة؛ انتظر خمس دقائق أو تحقق عبر تليجرام"
                else "الرقم السري غير صحيح"
            }

            val current = doctors.getActiveById(doctorId) ?: error("الطبيب لم يعد متاحاً في السجل")
            require(current.telegramId == doctor.telegramId &&
                current.extraOptions.firstOrNull { it.startsWith("pin:") } == "pin:$hash") {
                "تغير حساب الطبيب أو رقمه السري؛ أعد اختيار اسمك"
            }
            settings.delete(key)
            // Session privileges come from the live record, not the selected card's snapshot.
            current
        }
    }

    private companion object {
        const val LOCKOUT_MS = 5 * 60 * 1000L
    }
}
