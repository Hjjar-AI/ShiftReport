package com.hos.rushdpatients.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.InitialAdminConfig
import com.hos.rushdpatients.config.ProjectConfig
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.domain.auth.BootstrapManager
import com.hos.rushdpatients.domain.auth.BootstrapResult
import com.hos.rushdpatients.domain.auth.BootstrapSeeder
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.sync.AutoSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectConfigStore: ProjectConfigStore,
    private val telegram: TelegramClient,
    private val bootstrapManager: BootstrapManager,
    private val bootstrapSeeder: BootstrapSeeder,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ProjectSetupUiState())
    val state: StateFlow<ProjectSetupUiState> = _state.asStateFlow()

    fun setMode(value: ProjectSetupMode) = update { copy(mode = value, error = null) }
    fun setHospitalName(value: String) = update { copy(hospitalName = value, error = null) }
    fun setBotToken(value: String) = update { copy(botToken = value.trim(), error = null) }
    fun setChatId(value: String) = update { copy(chatId = numeric(value, signed = true), error = null) }
    fun setReportsTopicId(value: String) = update { copy(reportsTopicId = numeric(value), error = null) }
    fun setAnnouncementsTopicId(value: String) = update { copy(announcementsTopicId = numeric(value), error = null) }
    fun setCsvTopicId(value: String) = update { copy(csvTopicId = numeric(value), error = null) }
    fun setDoctorsTopicId(value: String) = update { copy(doctorsTopicId = numeric(value), error = null) }
    fun setAdminName(value: String) = update { copy(adminName = value, error = null) }
    fun setAdminTelegramId(value: String) = update { copy(adminTelegramId = numeric(value), error = null) }
    fun setAdminGender(value: String) = update { copy(adminGenderCode = value, error = null) }
    fun setAdminClinicalRole(value: String) = update { copy(adminClinicalRoleCode = value, error = null) }

    fun initialize() {
        if (_state.value.busy) return
        val input = _state.value
        val config = runCatching { validate(input) }.getOrElse { error ->
            _state.update { it.copy(error = error.message ?: "تحقق من البيانات") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "جارٍ التحقق من البوت والمجموعة…", error = null) }
            try {
                projectConfigStore.saveDraft(config)
                val bot = telegram.getMe()
                require(bot.isBot) { "الرمز لا يعود إلى بوت تليجرام" }
                val chat = telegram.getChat(config.chatId)
                require(chat.type == "group" || chat.type == "supergroup") {
                    "المعرف لا يعود إلى مجموعة تليجرام"
                }

                val bootstrap = if (input.mode == ProjectSetupMode.CREATE) {
                    require(chat.pinnedMessage == null) {
                        "توجد رسالة مثبّتة في المجموعة؛ استخدم الانضمام إلى مشروع موجود أو مجموعة جديدة"
                    }
                    _state.update { it.copy(status = "جارٍ إنشاء سجل المدير الأول…") }
                    when (val seeded = bootstrapSeeder.seed()) {
                        is BootstrapSeeder.Result.Success -> bootstrapManager.bootstrap()
                        is BootstrapSeeder.Result.Failure -> error(seeded.message)
                    }
                } else {
                    _state.update { it.copy(status = "جارٍ تنزيل سجل المشروع…") }
                    bootstrapManager.bootstrap()
                }

                when (bootstrap) {
                    is BootstrapResult.Success -> Unit
                    BootstrapResult.NoPin -> error("لا توجد بيانات مشروع مثبّتة في المجموعة")
                    BootstrapResult.UnrecognizedPin -> error("الرسالة المثبّتة ليست بيانات ShiftReport صالحة")
                    is BootstrapResult.Failed -> error(bootstrap.message)
                }

                sessionManager.clear()
                projectConfigStore.markInitialized()
                AutoSyncScheduler.configure(
                    context = context,
                    enabled = settingsRepository.isAutoSyncEnabled(),
                    wifiOnly = settingsRepository.isSyncWifiOnly()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        status = null,
                        error = e.message ?: "تعذر تهيئة المشروع"
                    )
                }
            }
        }
    }

    private fun validate(input: ProjectSetupUiState): ProjectConfig {
        val hospitalName = input.hospitalName.trim()
        require(hospitalName.length in 2..80) { "أدخل اسم المستشفى أو المشروع" }
        val token = input.botToken.trim()
        require(BOT_TOKEN.matches(token)) { "رمز البوت غير صالح" }
        val chatId = input.chatId.toLongOrNull()
        require(chatId != null && chatId < 0L) { "معرف المجموعة يجب أن يكون رقماً سالباً" }

        val admin = if (input.mode == ProjectSetupMode.CREATE) {
            val name = input.adminName.trim()
            require(name.length >= 3) { "أدخل اسم المدير الأول" }
            val telegramId = input.adminTelegramId.toLongOrNull()
            require(telegramId != null && telegramId > 0L) { "معرف تليجرام للمدير غير صالح" }
            InitialAdminConfig(
                fullName = name,
                telegramId = telegramId,
                genderCode = input.adminGenderCode,
                clinicalRoleCode = input.adminClinicalRoleCode
            )
        } else InitialAdminConfig()

        return ProjectConfig(
            hospitalName = hospitalName,
            botToken = token,
            chatId = chatId,
            reportsTopicId = topic(input.reportsTopicId, "التقارير"),
            announcementsTopicId = topic(input.announcementsTopicId, "الإعلانات"),
            csvTopicId = topic(input.csvTopicId, "البيانات"),
            doctorsTopicId = topic(input.doctorsTopicId, "الأطباء"),
            initialAdmin = admin
        )
    }

    private fun topic(value: String, label: String): Long {
        if (value.isBlank()) return 0L
        return value.toLongOrNull()?.takeIf { it > 0L }
            ?: throw IllegalArgumentException("معرف موضوع $label غير صالح")
    }

    private fun numeric(value: String, signed: Boolean = false): String =
        value.filterIndexed { index, char -> char.isDigit() || (signed && index == 0 && char == '-') }

    private inline fun update(transform: ProjectSetupUiState.() -> ProjectSetupUiState) {
        _state.update { current -> current.transform() }
    }

    private companion object {
        val BOT_TOKEN = Regex("""^\d{5,}:[A-Za-z0-9_-]{20,}$""")
    }
}
