package com.hos.rushdpatients.network.telegram

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramRateLimiter internal constructor(private val nowMillis: () -> Long) {

    @Inject constructor() : this(System::currentTimeMillis)

    private val mutexes = ConcurrentHashMap<Long, Mutex>()
    private val lastCallAt = ConcurrentHashMap<Long, Long>()

    suspend fun <T> withRateLimit(chatId: Long, block: suspend () -> T): T {
        val mutex = mutexes.computeIfAbsent(chatId) { Mutex() }
        return mutex.withLock {
            val last = lastCallAt[chatId]
            val elapsed = last?.let { nowMillis() - it }
            val min = TelegramConfig.MIN_CALL_INTERVAL_MS
            if (elapsed != null && elapsed in 0 until min) {
                delay(min - elapsed)
            }
            try {
                block()
            } finally {
                lastCallAt[chatId] = nowMillis()
            }
        }
    }

    fun reset(chatId: Long) {
        lastCallAt.remove(chatId)
    }
}
