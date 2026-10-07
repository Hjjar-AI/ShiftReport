package com.hos.rushdpatients.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class InitialAdminConfig(
    val fullName: String = "",
    val telegramId: Long = 0L,
    val genderCode: String = "M",
    val clinicalRoleCode: String = "RESIDENT"
)

data class ProjectConfig(
    val initialized: Boolean = false,
    val demoMode: Boolean = false,
    val hospitalName: String = "",
    val botToken: String = "",
    val chatId: Long = 0L,
    val reportsTopicId: Long = 0L,
    val announcementsTopicId: Long = 0L,
    val csvTopicId: Long = 0L,
    val doctorsTopicId: Long = 0L,
    val telegramDataKey: String = "",
    val initialAdmin: InitialAdminConfig = InitialAdminConfig()
)

/** Device-local project connection. Credentials are never stored in Room or exported backups. */
@Singleton
class ProjectConfigStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        FILE_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _config = MutableStateFlow(read())
    val config: StateFlow<ProjectConfig> = _config.asStateFlow()

    fun current(): ProjectConfig = _config.value

    fun saveDraft(config: ProjectConfig) {
        val draft = config.copy(initialized = false)
        write(draft)
    }

    fun markInitialized() {
        write(_config.value.copy(initialized = true))
    }

    fun enterDemo() {
        write(
            ProjectConfig(
                initialized = true,
                demoMode = true,
                hospitalName = "مشروع تجريبي"
            )
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
        _config.value = ProjectConfig()
    }

    private fun write(config: ProjectConfig) {
        prefs.edit()
            .putBoolean(KEY_INITIALIZED, config.initialized)
            .putBoolean(KEY_DEMO_MODE, config.demoMode)
            .putString(KEY_HOSPITAL_NAME, config.hospitalName)
            .putString(KEY_BOT_TOKEN, config.botToken)
            .putString(KEY_TELEGRAM_DATA_KEY, config.telegramDataKey)
            .putLong(KEY_CHAT_ID, config.chatId)
            .putLong(KEY_REPORTS_TOPIC, config.reportsTopicId)
            .putLong(KEY_ANNOUNCEMENTS_TOPIC, config.announcementsTopicId)
            .putLong(KEY_CSV_TOPIC, config.csvTopicId)
            .putLong(KEY_DOCTORS_TOPIC, config.doctorsTopicId)
            .putString(KEY_ADMIN_NAME, config.initialAdmin.fullName)
            .putLong(KEY_ADMIN_TELEGRAM_ID, config.initialAdmin.telegramId)
            .putString(KEY_ADMIN_GENDER, config.initialAdmin.genderCode)
            .putString(KEY_ADMIN_CLINICAL_ROLE, config.initialAdmin.clinicalRoleCode)
            .apply()
        _config.value = config
    }

    private fun read() = ProjectConfig(
        initialized = prefs.getBoolean(KEY_INITIALIZED, false),
        demoMode = prefs.getBoolean(KEY_DEMO_MODE, false),
        hospitalName = prefs.getString(KEY_HOSPITAL_NAME, "").orEmpty(),
        botToken = prefs.getString(KEY_BOT_TOKEN, "").orEmpty(),
        telegramDataKey = prefs.getString(KEY_TELEGRAM_DATA_KEY, "").orEmpty(),
        chatId = prefs.getLong(KEY_CHAT_ID, 0L),
        reportsTopicId = prefs.getLong(KEY_REPORTS_TOPIC, 0L),
        announcementsTopicId = prefs.getLong(KEY_ANNOUNCEMENTS_TOPIC, 0L),
        csvTopicId = prefs.getLong(KEY_CSV_TOPIC, 0L),
        doctorsTopicId = prefs.getLong(KEY_DOCTORS_TOPIC, 0L),
        initialAdmin = InitialAdminConfig(
            fullName = prefs.getString(KEY_ADMIN_NAME, "").orEmpty(),
            telegramId = prefs.getLong(KEY_ADMIN_TELEGRAM_ID, 0L),
            genderCode = prefs.getString(KEY_ADMIN_GENDER, "M").orEmpty(),
            clinicalRoleCode = prefs.getString(KEY_ADMIN_CLINICAL_ROLE, "RESIDENT").orEmpty()
        )
    )

    private companion object {
        const val FILE_NAME = "shift_report_project_encrypted"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_DEMO_MODE = "demo_mode"
        const val KEY_HOSPITAL_NAME = "hospital_name"
        const val KEY_BOT_TOKEN = "bot_token"
        const val KEY_TELEGRAM_DATA_KEY = "telegram_data_key"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_REPORTS_TOPIC = "reports_topic"
        const val KEY_ANNOUNCEMENTS_TOPIC = "announcements_topic"
        const val KEY_CSV_TOPIC = "csv_topic"
        const val KEY_DOCTORS_TOPIC = "doctors_topic"
        const val KEY_ADMIN_NAME = "admin_name"
        const val KEY_ADMIN_TELEGRAM_ID = "admin_telegram_id"
        const val KEY_ADMIN_GENDER = "admin_gender"
        const val KEY_ADMIN_CLINICAL_ROLE = "admin_clinical_role"
    }
}
