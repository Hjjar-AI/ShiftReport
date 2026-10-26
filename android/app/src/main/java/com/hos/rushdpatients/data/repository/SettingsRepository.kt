package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.dao.SettingDao
import com.hos.rushdpatients.data.db.entity.SettingEntity
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dao: SettingDao,
    private val dispatchers: DispatcherProvider
) {

    suspend fun get(key: String): String? = withContext(dispatchers.io) {
        dao.get(key)
    }

    fun observe(key: String): Flow<String?> = dao.observe(key)

    suspend fun put(key: String, value: String) = withContext(dispatchers.io) {
        dao.put(SettingEntity(key, value))
    }

    suspend fun putAll(settings: Map<String, String>) = withContext(dispatchers.io) {
        dao.putAll(settings.map { (k, v) -> SettingEntity(k, v) })
    }

    suspend fun delete(key: String) = withContext(dispatchers.io) {
        dao.delete(key)
    }

    suspend fun getAll(): Map<String, String> = withContext(dispatchers.io) {
        dao.getAll().associate { it.key to it.value }
    }

    /** Bound snapshot/contact cache only; clinical shifts, Undo, audit and receipts are untouched. */
    suspend fun pruneShiftSyncCache(keepShiftIds: Set<String>) = withContext(dispatchers.io) {
        val prefixes = listOf(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX,
            AppConstants.SETTING_PATIENT_PUBLISHED_AT_PREFIX, AppConstants.SETTING_PATIENT_DOWNLOAD_AT_PREFIX,
            AppConstants.SETTING_PATIENT_DOWNLOAD_MESSAGE_PREFIX)
        dao.getAll().forEach { setting ->
            val prefix = prefixes.firstOrNull { setting.key.startsWith(it) }
            if (prefix != null && setting.key.removePrefix(prefix) !in keepShiftIds) dao.delete(setting.key)
        }
    }

    // ---------- Typed helpers ----------

    suspend fun getBoolean(key: String, default: Boolean): Boolean =
        get(key)?.toBooleanStrictOrNull() ?: default

    suspend fun putBoolean(key: String, value: Boolean) = put(key, value.toString())

    suspend fun getInt(key: String, default: Int): Int =
        get(key)?.toIntOrNull() ?: default

    suspend fun putInt(key: String, value: Int) = put(key, value.toString())

    suspend fun getLong(key: String, default: Long): Long =
        get(key)?.toLongOrNull() ?: default

    suspend fun putLong(key: String, value: Long) = put(key, value.toString())

    // ---------- Domain-specific accessors ----------

    suspend fun isReportAsPdf(): Boolean =
        getBoolean(AppConstants.SETTING_REPORT_AS_PDF, true)

    suspend fun setReportAsPdf(enabled: Boolean) =
        putBoolean(AppConstants.SETTING_REPORT_AS_PDF, enabled)

    suspend fun getAutoLockMinutes(): Int =
        getInt(AppConstants.SETTING_AUTO_LOCK_MINUTES, 5).coerceIn(1, 60)

    suspend fun setAutoLockMinutes(minutes: Int) =
        putInt(AppConstants.SETTING_AUTO_LOCK_MINUTES, minutes.coerceIn(1, 60))
}
