package com.hos.rushdpatients.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.BootstrapManager
import com.hos.rushdpatients.domain.auth.BootstrapResult
import com.hos.rushdpatients.domain.auth.BootstrapSeeder
import com.hos.rushdpatients.domain.auth.LoginVerificationResult
import com.hos.rushdpatients.domain.auth.PasswordHasher
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.auth.TelegramLoginManager
import com.hos.rushdpatients.sync.AutoSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val doctorRepository: DoctorRepository,
    private val sessionManager: SessionManager,
    private val bootstrapManager: BootstrapManager,
    private val bootstrapSeeder: BootstrapSeeder,
    private val loginManager: TelegramLoginManager,
    private val hasher: PasswordHasher,
    private val auditRepository: AuditRepository,
    private val settingsRepository: SettingsRepository,
    projectConfigStore: ProjectConfigStore
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(projectName = projectConfigStore.current().hospitalName)
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var verifyJob: Job? = null

    init { boot() }

    fun boot() {
        verifyJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(step = LoginStep.Bootstrapping, busy = true, error = null) }

            val existing = doctorRepository.getAll()
            if (existing.isNotEmpty()) {
                _state.update { it.copy(step = LoginStep.PickDoctor(existing), busy = false) }
                return@launch
            }

            when (val result = bootstrapManager.bootstrap()) {
                is BootstrapResult.Success -> {
                    val doctors = doctorRepository.getAll()
                    _state.update { it.copy(step = LoginStep.PickDoctor(doctors), busy = false) }
                }
                is BootstrapResult.NoPin -> _state.update {
                    it.copy(
                        step = LoginStep.BootstrapFailed(
                            "لا توجد رسالة مثبّتة في المجموعة. اطلب من المالك تثبيت ملف bootstrap."
                        ),
                        busy = false
                    )
                }
                is BootstrapResult.UnrecognizedPin -> _state.update {
                    it.copy(
                        step = LoginStep.BootstrapFailed(
                            "الرسالة المثبّتة غير معروفة. تأكد من تثبيت ملف bootstrap الصحيح."
                        ),
                        busy = false
                    )
                }
                is BootstrapResult.Failed -> _state.update {
                    it.copy(step = LoginStep.BootstrapFailed(result.message), busy = false)
                }
            }
        }
    }

    /**
     * Hidden recovery path. Uploads and pins the initial administrator stored by project setup.
     *
     * After a successful seed, press the retry button normally to load it.
     */
    fun seedBootstrap() {
        if (_state.value.seeding) return
        _state.update { it.copy(seeding = true, error = null) }
        viewModelScope.launch {
            when (val result = bootstrapSeeder.seed()) {
                is BootstrapSeeder.Result.Success -> {
                    _state.update {
                        it.copy(
                            seeding = false,
                            step = LoginStep.BootstrapFailed(
                                "تم رفع سجل الأطباء الأولي وتثبيته (${result.messageId}). " +
                                        "اضغط إعادة المحاولة."
                            )
                        )
                    }
                }
                is BootstrapSeeder.Result.Failure -> {
                    _state.update {
                        it.copy(
                            seeding = false,
                            step = LoginStep.BootstrapFailed(result.message)
                        )
                    }
                }
            }
        }
    }

    fun pickDoctor(doctor: Doctor) {
        val telegramId = doctor.telegramId
        if (telegramId == null) {
            _state.update { it.copy(error = "لم يتم ربط حساب تليجرام لهذا الطبيب") }
            return
        }
        val nonce = loginManager.generateNonce()
        _state.update {
            it.copy(
                step = LoginStep.Verifying(doctor, nonce, remainingSeconds = 300),
                error = null
            )
        }

        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            val result = loginManager.awaitVerification(
                nonce = nonce,
                expectedTelegramId = telegramId
            ) { remaining ->
                _state.update { current ->
                    val v = current.step as? LoginStep.Verifying ?: return@update current
                    current.copy(step = v.copy(remainingSeconds = remaining))
                }
            }
            when (result) {
                is LoginVerificationResult.Success -> onVerified(doctor, telegramId)
                is LoginVerificationResult.WrongUser -> _state.update {
                    it.copy(
                        step = LoginStep.VerifyFailed(doctor, "الرسالة وصلت من حساب مختلف"),
                        busy = false
                    )
                }
                is LoginVerificationResult.Timeout -> _state.update {
                    it.copy(
                        step = LoginStep.VerifyFailed(doctor, "انتهى الوقت. حاول مرة أخرى"),
                        busy = false
                    )
                }
                is LoginVerificationResult.Error -> _state.update {
                    it.copy(
                        step = LoginStep.VerifyFailed(doctor, result.message),
                        busy = false
                    )
                }
            }
        }
    }

    private suspend fun onVerified(doctor: Doctor, telegramId: Long) {
        val hasPin = doctor.extraOptions.any { it.startsWith("pin:") }
        if (hasPin) {
            startSession(doctor, telegramId)
        } else {
            _state.update { it.copy(step = LoginStep.SetPin(doctor, telegramId), busy = false) }
        }
    }

    fun cancelVerification() {
        verifyJob?.cancel()
        verifyJob = null
        backToDoctors()
    }

    fun retryVerify(doctor: Doctor) = pickDoctor(doctor)

    fun backToDoctors() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    step = LoginStep.PickDoctor(doctorRepository.getAll()),
                    busy = false,
                    error = null
                )
            }
        }
    }

    fun setPin(doctor: Doctor, telegramId: Long, pin: String, confirm: String) {
        if (pin.length !in AppConstants.PIN_MIN_LENGTH..AppConstants.PIN_MAX_LENGTH ||
            !pin.all(Char::isDigit)
        ) {
            _state.update { it.copy(error = "الرقم السري يجب أن يكون من 4 إلى 8 أرقام") }
            return
        }
        if (pin != confirm) {
            _state.update { it.copy(error = "الرقم السري وتأكيده غير متطابقين") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val updatedExtra = doctor.extraOptions
                .filterNot { it.startsWith("pin:") }
                .toSet() + "pin:${hasher.hash(pin)}"
            val updated = doctor.copy(extraOptions = updatedExtra, updatedAt = Instant.now())
            doctorRepository.upsert(updated)
            startSession(updated, telegramId)
        }
    }

    private suspend fun startSession(doctor: Doctor, telegramId: Long) {
        sessionManager.start(
            Session(
                doctorId = doctor.id,
                doctorName = doctor.fullName,
                telegramId = telegramId,
                role = doctor.role,
                unlockedAt = Instant.now()
            )
        )
        runCatching {
            auditRepository.record(doctor.id, doctor.fullName, AppConstants.AUDIT_LOGIN)
        }
        AutoSyncScheduler.configure(
            context,
            settingsRepository.isAutoSyncEnabled(),
            settingsRepository.isSyncWifiOnly()
        )
        _state.update { it.copy(busy = false) }
    }
}
