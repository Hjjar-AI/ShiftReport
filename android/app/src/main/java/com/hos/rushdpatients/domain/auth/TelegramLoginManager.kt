package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.network.telegram.TelegramClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginVerificationResult {
    data class Success(val telegramId: Long) : LoginVerificationResult
    data class WrongUser(val actualTelegramId: Long) : LoginVerificationResult
    data object Timeout : LoginVerificationResult
    data class Error(val message: String) : LoginVerificationResult
}

@Singleton
class TelegramLoginManager @Inject constructor(
    private val telegram: TelegramClient
) {
    private val random = SecureRandom()

    // Avoids 0/O/1/I which are easy to misread.
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateNonce(): String = buildString(AppConstants.TELEGRAM_LOGIN_NONCE_LENGTH) {
        repeat(AppConstants.TELEGRAM_LOGIN_NONCE_LENGTH) {
            append(alphabet[random.nextInt(alphabet.length)])
        }
    }

    suspend fun awaitVerification(
        nonce: String,
        expectedTelegramId: Long,
        onTick: suspend (remainingSeconds: Int) -> Unit = {}
    ): LoginVerificationResult = withContext(Dispatchers.IO) {
        // Drain pending updates so we don't match an old nonce from a previous attempt.
        val startOffset: Long = try {
            val drained = telegram.getUpdates(offset = null, limit = 1, timeoutSeconds = 0)
            (drained.maxOfOrNull { it.updateId } ?: -1L) + 1
        } catch (e: Exception) {
            return@withContext LoginVerificationResult.Error(
                e.message ?: "تعذر الاتصال بتليجرام"
            )
        }

        var nextOffset = startOffset
        val start = System.currentTimeMillis()
        val timeoutMs = AppConstants.TELEGRAM_LOGIN_TIMEOUT_MS

        while (true) {
            val elapsed = System.currentTimeMillis() - start
            if (elapsed >= timeoutMs) return@withContext LoginVerificationResult.Timeout
            onTick(((timeoutMs - elapsed) / 1000).toInt())

            try {
                val updates = telegram.getUpdates(
                    offset = nextOffset,
                    limit = 100,
                    timeoutSeconds = 0
                )
                for (update in updates) {
                    nextOffset = maxOf(nextOffset, update.updateId + 1)
                    val message = update.message ?: continue
                    val from = message.from ?: continue
                    if (from.isBot) continue
                    val text = message.text ?: continue
                    if (!text.contains(nonce, ignoreCase = true)) continue
                    return@withContext if (from.id == expectedTelegramId) {
                        LoginVerificationResult.Success(from.id)
                    } else {
                        LoginVerificationResult.WrongUser(from.id)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext LoginVerificationResult.Error(
                    e.message ?: "فشل الاتصال بتليجرام"
                )
            }

            delay(AppConstants.TELEGRAM_LOGIN_POLL_INTERVAL_MS)
        }
        @Suppress("UNREACHABLE_CODE")
        LoginVerificationResult.Timeout
    }
}