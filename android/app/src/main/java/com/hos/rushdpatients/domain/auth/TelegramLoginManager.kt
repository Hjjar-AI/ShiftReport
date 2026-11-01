package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.TelegramException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginVerificationResult {
    data class Success(val telegramId: Long) : LoginVerificationResult
    data object Timeout : LoginVerificationResult
    data object Incorrect : LoginVerificationResult
    data object Exhausted : LoginVerificationResult
}

/** Private challenge, like desktop: never reads or acknowledges the bot-global update queue. */
@Singleton
class TelegramLoginManager @Inject constructor(
    private val telegram: TelegramClient,
    private val topics: Topics
) {
    private data class Challenge(val doctorId: String, val telegramId: Long,
        val salt: ByteArray, val hash: ByteArray, val started: Long,
        val connectionUnchanged: () -> Boolean, var attempts: Int = 0)
    private val random = SecureRandom()
    private val mutex = Mutex()
    private var challenge: Challenge? = null
    private fun now() = android.os.SystemClock.elapsedRealtime()
    private fun hash(salt: ByteArray, code: String) =
        MessageDigest.getInstance("SHA-256").digest(salt + code.toByteArray(Charsets.UTF_8))

    suspend fun beginVerification(doctorId: String, expectedTelegramId: Long): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(expectedTelegramId > 0)
            val old = challenge
            if (old != null && old.doctorId == doctorId && old.telegramId == expectedTelegramId &&
                old.connectionUnchanged() && now() - old.started < AppConstants.TELEGRAM_LOGIN_TIMEOUT_MS) {
                // Reuse an unexpired challenge after a failed/uncertain request or cancelled input.
                return@withLock
            }
            require(old == null || now() - old.started >= 30_000) { "انتظر 30 ثانية قبل طلب رمز جديد" }
            val code = random.nextInt(1_000_000).toString().padStart(6, '0')
            val salt = ByteArray(16).also(random::nextBytes)
            challenge = Challenge(doctorId, expectedTelegramId, salt, hash(salt, code), now(), topics.connectionUnchangedCheck())
            try {
                telegram.sendPrivateLoginCode(expectedTelegramId, code)
            } catch (error: TelegramException) {
                if (error.isDefiniteRejection) challenge = null
                throw error
            }
        }
    }

    suspend fun remainingSeconds(doctorId: String): Int = mutex.withLock {
        challenge?.takeIf { it.doctorId == doctorId && it.connectionUnchanged() }?.let {
            ((AppConstants.TELEGRAM_LOGIN_TIMEOUT_MS - (now() - it.started)).coerceAtLeast(0L) / 1000L).toInt()
        } ?: 0
    }

    suspend fun verify(doctorId: String, telegramId: Long, submitted: String): LoginVerificationResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = challenge
            if (current == null || current.doctorId != doctorId || current.telegramId != telegramId ||
                !current.connectionUnchanged() || now() - current.started >= AppConstants.TELEGRAM_LOGIN_TIMEOUT_MS)
                return@withLock LoginVerificationResult.Timeout
            if (current.attempts >= AppConstants.LOGIN_MAX_ATTEMPTS) return@withLock LoginVerificationResult.Exhausted
            current.attempts++
            val code = submitted.trim().map { digit ->
                Character.digit(digit, 10).takeIf { it >= 0 }?.let { ('0'.code + it).toChar() } ?: digit
            }.joinToString("")
            if (!MessageDigest.isEqual(hash(current.salt, code), current.hash))
                return@withLock if (current.attempts >= AppConstants.LOGIN_MAX_ATTEMPTS)
                    LoginVerificationResult.Exhausted else LoginVerificationResult.Incorrect
            challenge = null
            LoginVerificationResult.Success(current.telegramId)
        }
    }
}
