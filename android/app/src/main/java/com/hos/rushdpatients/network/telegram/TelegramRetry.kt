package com.hos.rushdpatients.network.telegram

import kotlinx.coroutines.delay

object TelegramRetry {

    suspend fun <T> withRetry(
        maxAttempts: Int = TelegramConfig.MAX_RETRIES,
        allowServerRetry: Boolean = true,
        block: suspend () -> T
    ): T {
        var waitedMillis = 0L
        var attempt = 0
        var lastError: TelegramException? = null

        while (attempt < maxAttempts) {
            attempt++
            try {
                return block()
            } catch (e: TelegramException) {
                lastError = e
                // Code -1 is ambiguous: Telegram may have accepted a POST before
                // the connection or response parser failed. Retrying could duplicate it.
                val isRetryable = e.isRateLimited ||
                        (allowServerRetry && e.code in 500..599)
                if (!isRetryable || attempt >= maxAttempts) {
                    throw e
                }
                val waitSeconds: Long = e.retryAfterSeconds?.toLong()
                    ?: (attempt * 2).toLong()
                val waitMillis = waitSeconds.coerceAtLeast(1L) * 1000L
                if (waitedMillis + waitMillis > 25_000L) throw e
                waitedMillis += waitMillis
                delay(waitMillis)
            }
        }
        throw lastError ?: TelegramException(
            code = -1,
            message = "Retry loop exited without result or error"
        )
    }
}
