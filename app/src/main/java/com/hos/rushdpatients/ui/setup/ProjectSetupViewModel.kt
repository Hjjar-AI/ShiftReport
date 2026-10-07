package com.hos.rushdpatients.ui.setup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.InitialAdminConfig
import com.hos.rushdpatients.config.ProjectConfig
import com.hos.rushdpatients.config.ProjectDataCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.config.ProjectProvisioningManager
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
    private val settingsRepository: SettingsRepository,
    private val provisioningManager: ProjectProvisioningManager
) : ViewModel() {
    private val _state = MutableStateFlow(ProjectSetupUiState())
    val state: StateFlow<ProjectSetupUiState> = _state.asStateFlow()

    fun setMode(value: ProjectSetupMode) = update {
        if (mode == value || busy) this else copy(mode = value, error = null, status = null,
            importedProvisioning = false, telegramDataKey = "", encryptTelegram = false)
    }
    fun setEncryptTelegram(value: Boolean) = update {
        if (mode == ProjectSetupMode.CREATE && !busy) copy(encryptTelegram = value, error = null) else this
    }
    fun setHospitalName(value: String) = update { copy(hospitalName = value, error = null) }
    fun setBotToken(value: String) = update { copy(botToken = value.trim(), error = null) }
    fun setChatId(value: String) = update { copy(chatId = numeric(value, signed = true), error = null) }
    fun setReportsTopicId(value: String) = update { copy(reportsTopicId = numeric(value), error = null) }
    fun setAnnouncementsTopicId(value: String) = update { copy(announcementsTopicId = numeric(value), error = null) }
    fun setCsvTopicId(value: String) = update { copy(csvTopicId = numeric(value), error = null) }
    fun setDoctorsTopicId(value: String) = update { copy(doctorsTopicId = numeric(value), error = null) }
    fun setProvisioningPassphrase(value: String) = update {
        copy(provisioningPassphrase = value, error = null)
    }
    fun setAdminName(value: String) = update { copy(adminName = value, error = null) }
    fun setAdminTelegramId(value: String) = update { copy(adminTelegramId = numeric(value), error = null) }
    fun setAdminGender(value: String) = update { copy(adminGenderCode = value, error = null) }
    fun setAdminClinicalRole(value: String) = update { copy(adminClinicalRoleCode = value, error = null) }

    fun importProvisioning(uri: Uri) {
        val passphrase = _state.value.provisioningPassphrase
        if (passphrase.length < ProjectProvisioningManager.MIN_PASSWORD_LENGTH) {
            _state.update { it.copy(error = "أدخل عبارة المرور المكونة من 10 محارف على الأقل") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "جارٍ فتح ملف الانضمام…", error = null) }
            runCatching { provisioningManager.importConfig(uri, passphrase.toCharArray()) }
                .onSuccess { config ->
                    _state.update {
                        it.copy(
                            hospitalName = config.hospitalName,
                            botToken = config.botToken,
                            chatId = config.chatId.toString(),
                            reportsTopicId = config.reportsTopicId.takeIf { id -> id > 0 }?.toString().orEmpty(),
                            announcementsTopicId = config.announcementsTopicId.takeIf { id -> id > 0 }?.toString().orEmpty(),
                            csvTopicId = config.csvTopicId.takeIf { id -> id > 0 }?.toString().orEmpty(),
                            doctorsTopicId = config.doctorsTopicId.takeIf { id -> id > 0 }?.toString().orEmpty(),
                            provisioningPassphrase = "",
                            importedProvisioning = true,
                            encryptTelegram = config.telegramDataKey.isNotEmpty(),
                            telegramDataKey = config.telegramDataKey,
                            busy = false,
                            status = "تم تحميل إعدادات المشروع. اضغط التحقق والانضمام.",
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            provisioningPassphrase = "",
                            busy = false,
                            status = null,
                            error = error.message ?: "تعذر فتح ملف الانضمام"
                        )
                    }
                }
        }
    }

    fun initialize() {
        if (_state.value.busy) return
        val input = _state.value
        if (input.mode == ProjectSetupMode.DEMO) {
            sessionManager.clear()
            projectConfigStore.enterDemo()
            return
        }
        if (input.mode == ProjectSetupMode.JOIN && !input.importedProvisioning) {
            _state.update { it.copy(error = "استورد ملف الانضمام المشفر من المدير أولاً") }
            return
        }
        val config = runCatching { validate(input) }.getOrElse { error ->
            _state.update { it.copy(error = error.message ?: "تحقق من البيانات") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "جارٍ التحقق من البوت والمجموعة…", error = null) }
            try {
                val configured = if (input.mode == ProjectSetupMode.CREATE && input.encryptTelegram) {
                    val key = input.telegramDataKey.takeIf { ProjectDataCipher.validKey(it) }
                        ?: withContext(Dispatchers.IO) { ProjectDataCipher.generateKey() }
                    _state.update { it.copy(telegramDataKey = key) }
                    config.copy(telegramDataKey = key)
                } else config
                projectConfigStore.saveDraft(configured)
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
            telegramDataKey = if (input.mode == ProjectSetupMode.JOIN || input.encryptTelegram) input.telegramDataKey else "",
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
