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
import com.hos.rushdpatients.domain.auth.BootstrapManager
import com.hos.rushdpatients.domain.auth.BootstrapResult
import com.hos.rushdpatients.domain.auth.LoginVerificationResult
import com.hos.rushdpatients.domain.auth.LocalPinLoginManager
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
    private val auditRepository: AuditRepository,
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

            try {
                val existing = doctorRepository.getAll()
                if (existing.isNotEmpty()) {
                    showDoctorPicker(existing)
                    return@launch
                }

                when (val result = bootstrapManager.bootstrap()) {
                    is BootstrapResult.Success -> {
                        val doctors = doctorRepository.getAll()
                        showDoctorPicker(doctors)
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
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _state.update { it.copy(step = LoginStep.BootstrapFailed(uiText(R.string.ui_login_view_model_004)), busy = false) }
            }
        }
    }

    fun pickDoctor(doctor: Doctor) {
        if (_state.value.busy) return
        verifyJob?.cancel()
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val current = requireNotNull(doctorRepository.getActiveById(doctor.id))
                val enrolled = localPinLogin.isEnrolled(current)
                _state.update { it.copy(busy = false) }
                if (enrolled) _state.update { it.copy(step = LoginStep.EnterPin(current), error = null) }
                else verifyViaTelegram(current)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(busy = false, error = uiText(R.string.ui_login_view_model_004)) } }
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
        _state.update { it.copy(busy = true, error = null) }
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            try {
                val current = requireNotNull(doctorRepository.getActiveById(doctor.id))
                require(current.telegramId == telegramId)
                loginManager.beginVerification(current.id, telegramId)
                val remainingAtStart = loginManager.remainingSeconds(current.id)
                _state.update { it.copy(step = LoginStep.Verifying(current, remainingAtStart), busy = false) }
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    val remaining = loginManager.remainingSeconds(current.id)
                    if (remaining <= 0) {
                        _state.update { it.copy(step = LoginStep.VerifyFailed(current, uiText(R.string.ui_login_view_model_007)), busy = false) }
                        break
                    }
                    _state.update { state ->
                        val step = state.step as? LoginStep.Verifying
                        if (step?.doctor?.id == current.id) state.copy(step = step.copy(remainingSeconds = remaining)) else state
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _state.update { it.copy(step = LoginStep.VerifyFailed(doctor, uiText(R.string.login_private_code_send_failed)), busy = false) }
            }
        }
    }

    fun verifyTelegramCode(code: String) {
        if (_state.value.busy) return
        val step = _state.value.step as? LoginStep.Verifying ?: return
        val telegramId = step.doctor.telegramId ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = loginManager.verify(step.doctor.id, telegramId, code)) {
                    is LoginVerificationResult.Success -> {
                        verifyJob?.cancel()
                        onVerified(step.doctor, result.telegramId)
                    }
                    LoginVerificationResult.Incorrect -> _state.update { it.copy(error = uiText(R.string.login_private_code_incorrect)) }
                    LoginVerificationResult.Timeout, LoginVerificationResult.Exhausted -> {
                        verifyJob?.cancel()
                        _state.update { it.copy(step = LoginStep.VerifyFailed(step.doctor, uiText(R.string.login_private_code_expired))) }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(error = uiText(R.string.ui_login_view_model_004)) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    private suspend fun onVerified(doctor: Doctor, telegramId: Long) {
        try {
            val current = requireNotNull(doctorRepository.getActiveById(doctor.id))
            require(current.telegramId == telegramId) { "تغيرت هوية الطبيب؛ أعد التحقق" }
            if (localPinLogin.isEnrolled(current)) startSession(current, telegramId)
            else _state.update { it.copy(step = LoginStep.SetPin(current, telegramId), busy = false) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { _state.update { it.copy(busy = false, error = uiText(R.string.ui_login_view_model_004)) } }
    }

    fun cancelVerification() {
        verifyJob?.cancel()
        verifyJob = null
        backToDoctors()
    }

    fun retryVerify(doctor: Doctor) = verifyViaTelegram(doctor)

    private suspend fun showDoctorPicker(doctors: List<Doctor>) {
        val enrolled = doctors.filter { localPinLogin.isEnrolled(it) }.mapTo(mutableSetOf()) { it.id }
        _state.update { it.copy(step = LoginStep.PickDoctor(doctors), enrolledDoctorIds = enrolled, busy = false, error = null) }
    }

    fun backToDoctors() {
        verifyJob?.cancel()
        viewModelScope.launch {
            try { showDoctorPicker(doctorRepository.getAll()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(busy = false, error = uiText(R.string.ui_login_view_model_004)) } }
        }
    }

    fun setPin(doctor: Doctor, telegramId: Long, pin: String, confirm: String) {
        if (_state.value.busy) return
        val verified = _state.value.step as? LoginStep.SetPin ?: return
        if (verified.doctor.id != doctor.id || verified.telegramId != telegramId) return
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
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val updated = localPinLogin.enrollVerified(doctor.id, telegramId, pin)
                startSession(updated, telegramId)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(error = uiText(R.string.ui_login_view_model_004)) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    private suspend fun startSession(doctor: Doctor, telegramId: Long) {
        val current = requireNotNull(doctorRepository.getActiveById(doctor.id))
        require(current.telegramId == telegramId) { "تغيرت هوية الطبيب؛ أعد التحقق" }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            sessionManager.start(Session(
                doctorId = current.id,
                doctorName = current.fullName,
                telegramId = telegramId,
                role = current.role,
                unlockedAt = Instant.now()
            ))
        }
        com.hos.rushdpatients.util.runCatchingCancellable {
            auditRepository.record(doctor.id, doctor.fullName, AppConstants.AUDIT_LOGIN)
        }
        _state.update { it.copy(busy = false) }
    }
}
