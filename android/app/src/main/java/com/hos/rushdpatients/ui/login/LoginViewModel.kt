package com.hos.rushdpatients.ui.login

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

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
import com.hos.rushdpatients.domain.auth.LoginVerificationResult
import com.hos.rushdpatients.domain.auth.LocalPinLoginManager
import com.hos.rushdpatients.domain.auth.PasswordHasher
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.auth.TelegramLoginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val uiText: UiStrings,
    @ApplicationContext private val context: Context,
    private val doctorRepository: DoctorRepository,
    private val sessionManager: SessionManager,
    private val bootstrapManager: BootstrapManager,
    private val loginManager: TelegramLoginManager,
    private val localPinLogin: LocalPinLoginManager,
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
                            uiText(R.string.ui_login_view_model_001)
                        ),
                        busy = false
                    )
                }
                is BootstrapResult.UnrecognizedPin -> _state.update {
                    it.copy(
                        step = LoginStep.BootstrapFailed(
                            uiText(R.string.ui_login_view_model_002)
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

    fun pickDoctor(doctor: Doctor) {
        if (_state.value.busy) return
        verifyJob?.cancel()
        if (doctor.telegramId != null && doctor.options.pinHash != null) {
            _state.update { it.copy(step = LoginStep.EnterPin(doctor), busy = false, error = null) }
        } else {
            verifyViaTelegram(doctor)
        }
    }

    fun loginWithPin(pin: String) {
        if (_state.value.busy) return
        val step = _state.value.step as? LoginStep.EnterPin ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val doctor = localPinLogin.verify(step.doctor.id, pin)
                startSession(doctor, checkNotNull(doctor.telegramId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = if (e is IllegalArgumentException || e is IllegalStateException)
                        com.hos.rushdpatients.ui.components.failureText(uiText, e) ?: uiText(R.string.ui_login_view_model_003) else uiText(R.string.ui_login_view_model_004))
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun verifyViaTelegram(doctor: Doctor) {
        if (_state.value.busy) return
        val telegramId = doctor.telegramId
        if (telegramId == null) {
            _state.update { it.copy(error = uiText(R.string.ui_login_view_model_005)) }
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
                        step = LoginStep.VerifyFailed(doctor, uiText(R.string.ui_login_view_model_006)),
                        busy = false
                    )
                }
                is LoginVerificationResult.Timeout -> _state.update {
                    it.copy(
                        step = LoginStep.VerifyFailed(doctor, uiText(R.string.ui_login_view_model_007)),
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
        val hasPin = doctor.options.pinHash != null
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

    fun retryVerify(doctor: Doctor) = verifyViaTelegram(doctor)

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
            _state.update { it.copy(error = uiText(R.string.ui_login_view_model_008)) }
            return
        }
        if (pin != confirm) {
            _state.update { it.copy(error = uiText(R.string.ui_login_view_model_009)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val updatedExtra = doctor.options.withPinHash(hasher.hash(pin))
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
        _state.update { it.copy(busy = false) }
    }
}
