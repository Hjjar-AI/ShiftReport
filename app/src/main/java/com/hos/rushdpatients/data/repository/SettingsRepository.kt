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
        getBoolean(AppConstants.SETTING_REPORT_AS_PDF, false)

    suspend fun setReportAsPdf(enabled: Boolean) =
        putBoolean(AppConstants.SETTING_REPORT_AS_PDF, enabled)

    // Auto sync defaults to OFF. The user must opt in from Settings.
    suspend fun isAutoSyncEnabled(): Boolean =
        getBoolean(AppConstants.SETTING_AUTO_SYNC, false)

    suspend fun setAutoSync(enabled: Boolean) =
        putBoolean(AppConstants.SETTING_AUTO_SYNC, enabled)

    suspend fun isSyncWifiOnly(): Boolean =
        getBoolean(AppConstants.SETTING_SYNC_WIFI_ONLY, false)

    suspend fun setSyncWifiOnly(wifiOnly: Boolean) =
        putBoolean(AppConstants.SETTING_SYNC_WIFI_ONLY, wifiOnly)

    suspend fun getAutoLockMinutes(): Int =
        getInt(AppConstants.SETTING_AUTO_LOCK_MINUTES, 20).coerceIn(1, 60)

    suspend fun setAutoLockMinutes(minutes: Int) =
        putInt(AppConstants.SETTING_AUTO_LOCK_MINUTES, minutes.coerceIn(1, 60))
}
