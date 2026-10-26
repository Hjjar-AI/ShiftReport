package com.hos.rushdpatients.ui.setup

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

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
    private val uiText: UiStrings,
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
    private data class PinnedProjectReview(val config: ProjectConfig, val pin: BootstrapSeeder.PinReview)
    private var pendingPinnedProjectReview: PinnedProjectReview? = null

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
            _state.update { it.copy(error = uiText(R.string.ui_project_setup_view_model_001)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = uiText(R.string.ui_project_setup_view_model_002), error = null) }
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
                            status = uiText(R.string.ui_project_setup_view_model_003),
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
                            error = error.message ?: uiText(R.string.ui_project_setup_view_model_004)
                        )
                    }
                }
        }
    }

    fun initialize() {
        dismissPinnedProjectReplacement()
        initializeReviewed(null)
    }

    fun dismissPinnedProjectReplacement() {
        pendingPinnedProjectReview = null
        _state.update { it.copy(confirmPinnedProjectReplacement = false) }
    }

    fun confirmPinnedProjectReplacement() {
        if (_state.value.busy) return
        val review = pendingPinnedProjectReview ?: return
        dismissPinnedProjectReplacement()
        initializeReviewed(review)
    }

    private fun initializeReviewed(review: PinnedProjectReview?) {
        if (_state.value.busy) return
        if (projectConfigStore.current().initialized) {
            _state.update { it.copy(error = uiText(R.string.ui_setup_existing_local_project)) }
            return
        }
        val input = _state.value
        if (input.mode == ProjectSetupMode.DEMO) {
            sessionManager.clear()
            projectConfigStore.enterDemo()
            return
        }
        if (input.mode == ProjectSetupMode.JOIN && !input.importedProvisioning) {
            _state.update { it.copy(error = uiText(R.string.ui_project_setup_view_model_005)) }
            return
        }
        val config = runCatching { validate(input) }.getOrElse { error ->
            _state.update { it.copy(error = error.message ?: uiText(R.string.ui_project_setup_view_model_006)) }
            return
        }
        if (review != null && (input.mode != ProjectSetupMode.CREATE || config != review.config)) {
            _state.update { it.copy(error = uiText(R.string.ui_setup_pin_review_changed)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = uiText(R.string.ui_project_setup_view_model_007), error = null) }
            try {
                val configured = if (input.mode == ProjectSetupMode.CREATE && input.encryptTelegram) {
                    val key = input.telegramDataKey.takeIf { ProjectDataCipher.validKey(it) }
                        ?: withContext(Dispatchers.IO) { ProjectDataCipher.generateKey() }
                    _state.update { it.copy(telegramDataKey = key) }
                    config.copy(telegramDataKey = key)
                } else config
                projectConfigStore.saveDraft(configured)
                val bot = telegram.getMe()
                require(bot.isBot) { uiText(R.string.ui_project_setup_view_model_008) }
                val chat = telegram.getChat(config.chatId)
                require(chat.type == "group" || chat.type == "supergroup") {
                    uiText(R.string.ui_project_setup_view_model_009)
                }

                val bootstrap = if (input.mode == ProjectSetupMode.CREATE) {
                    if (review == null && chat.pinnedMessage != null) {
                        pendingPinnedProjectReview = PinnedProjectReview(configured,
                            BootstrapSeeder.PinReview(config.chatId, chat.pinnedMessage))
                        _state.update { it.copy(busy = false, status = null, confirmPinnedProjectReplacement = true) }
                        return@launch
                    }
                    _state.update { it.copy(status = uiText(R.string.ui_project_setup_view_model_011)) }
                    when (val seeded = bootstrapSeeder.seed(review?.pin)) {
                        is BootstrapSeeder.Result.Success -> bootstrapManager.bootstrap(expectedPinnedMessageId = seeded.messageId)
                        is BootstrapSeeder.Result.Failure -> error(seeded.message)
                    }
                } else {
                    _state.update { it.copy(status = uiText(R.string.ui_project_setup_view_model_012)) }
                    bootstrapManager.bootstrap()
                }

                when (bootstrap) {
                    is BootstrapResult.Success -> Unit
                    BootstrapResult.NoPin -> error(uiText(R.string.ui_project_setup_view_model_013))
                    BootstrapResult.UnrecognizedPin -> error(uiText(R.string.ui_project_setup_view_model_014))
                    is BootstrapResult.Failed -> error(bootstrap.message)
                }

                sessionManager.clear()
                projectConfigStore.markInitialized()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        status = null,
                        error = e.message ?: uiText(R.string.ui_project_setup_view_model_015)
                    )
                }
            }
        }
    }

    private fun validate(input: ProjectSetupUiState): ProjectConfig {
        val hospitalName = input.hospitalName.trim()
        require(hospitalName.length in 2..80) { uiText(R.string.ui_project_setup_view_model_016) }
        val token = input.botToken.trim()
        require(BOT_TOKEN.matches(token)) { uiText(R.string.ui_project_setup_view_model_017) }
        val chatId = input.chatId.toLongOrNull()
        require(chatId != null && chatId < 0L) { uiText(R.string.ui_project_setup_view_model_018) }

        val admin = if (input.mode == ProjectSetupMode.CREATE) {
            val name = input.adminName.trim()
            val telegramId = input.adminTelegramId.toLongOrNull()
            require(telegramId != null && telegramId > 0L) { uiText(R.string.ui_project_setup_view_model_020) }
            InitialAdminConfig(
                fullName = name.ifBlank { "د. بلا اسم ${com.hos.rushdpatients.domain.doctor.DoctorNaming.stableId(telegramId.toString()).take(8)}" },
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
            reportsTopicId = topic(input.reportsTopicId, uiText(R.string.ui_project_setup_view_model_021)),
            announcementsTopicId = input.announcementsTopicId.toLongOrNull() ?: 0L, // Legacy join files only.
            csvTopicId = topic(input.csvTopicId, uiText(R.string.ui_project_setup_view_model_023)),
            doctorsTopicId = topic(input.doctorsTopicId, uiText(R.string.ui_doctors)),
            initialAdmin = admin
        )
    }

    private fun topic(value: String, label: String): Long {
        if (value.isBlank()) return 0L
        return value.toLongOrNull()?.takeIf { it > 0L }
            ?: throw IllegalArgumentException(uiText(R.string.ui_project_setup_view_model_025, label))
    }

    private fun numeric(value: String, signed: Boolean = false): String =
        value.filterIndexed { index, char -> char.isDigit() || (signed && index == 0 && char == '-') }

    private inline fun update(transform: ProjectSetupUiState.() -> ProjectSetupUiState) {
        if (_state.value.busy) return
        pendingPinnedProjectReview = null
        _state.update { current -> current.transform().copy(confirmPinnedProjectReplacement = false) }
    }

    private companion object {
        val BOT_TOKEN = Regex("""^\d{5,}:[A-Za-z0-9_-]{20,}$""")
    }
}
