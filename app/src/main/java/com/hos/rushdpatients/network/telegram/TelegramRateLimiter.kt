package com.hos.rushdpatients.network.telegram

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramRateLimiter @Inject constructor() {

    private val mutexes = ConcurrentHashMap<Long, Mutex>()
    private val lastCallAt = ConcurrentHashMap<Long, Long>()

    suspend fun <T> withRateLimit(chatId: Long, block: suspend () -> T): T {
        val mutex = mutexes.computeIfAbsent(chatId) { Mutex() }
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val last = lastCallAt[chatId] ?: 0L
            val elapsed = now - last
            val min = TelegramConfig.MIN_CALL_INTERVAL_MS
            if (elapsed in 0 until min) {
                delay(min - elapsed)
            }
            try {
                block()
            } finally {
                lastCallAt[chatId] = System.currentTimeMillis()
            }
        }
    }

    fun reset(chatId: Long) {
        lastCallAt.remove(chatId)
    }
}