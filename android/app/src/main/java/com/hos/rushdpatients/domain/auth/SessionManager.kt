package com.hos.rushdpatients.domain.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.hos.rushdpatients.data.model.Role
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val masterKeyAlias: String =
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        FILE_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _sessionFlow = MutableStateFlow(read())
    val sessionFlow: StateFlow<Session?> = _sessionFlow.asStateFlow()

    fun current(): Session? = _sessionFlow.value

    fun start(session: Session) {
        prefs.edit()
            .putString(KEY_DOCTOR_ID, session.doctorId)
            .putString(KEY_DOCTOR_NAME, session.doctorName)
            .putLong(KEY_TELEGRAM_ID, session.telegramId ?: 0L)
            .putString(KEY_ROLE, session.role.name)
            .putLong(KEY_UNLOCKED_AT, session.unlockedAt.toEpochMilli())
            .apply()
        _sessionFlow.value = session
    }

    fun clear() {
        prefs.edit().clear().apply()
        _sessionFlow.value = null
    }

    fun markUnlocked() {
        val current = _sessionFlow.value ?: return
        start(current.copy(unlockedAt = Instant.now()))
    }

    private fun read(): Session? {
        val id = prefs.getString(KEY_DOCTOR_ID, null) ?: return null
        val name = prefs.getString(KEY_DOCTOR_NAME, null) ?: return null
        val roleText = prefs.getString(KEY_ROLE, null) ?: return null
        val unlockedAt = prefs.getLong(KEY_UNLOCKED_AT, 0L)
        if (unlockedAt == 0L) return null

        val tgId = prefs.getLong(KEY_TELEGRAM_ID, 0L).takeIf { it != 0L }

        return Session(
            doctorId = id,
            doctorName = name,
            telegramId = tgId,
            role = runCatching { Role.valueOf(roleText) }.getOrDefault(Role.NON_ADMIN),
            unlockedAt = Instant.ofEpochMilli(unlockedAt)
        )
    }

    private companion object {
        const val FILE_NAME = "rushd_session_encrypted"
        const val KEY_DOCTOR_ID = "doctor_id"
        const val KEY_DOCTOR_NAME = "doctor_name"
        const val KEY_TELEGRAM_ID = "telegram_id"
        const val KEY_ROLE = "role"
        const val KEY_UNLOCKED_AT = "unlocked_at"
    }
}
