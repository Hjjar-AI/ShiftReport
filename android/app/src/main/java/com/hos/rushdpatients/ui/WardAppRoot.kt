package com.hos.rushdpatients.ui

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.BiometricHelper
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.ui.login.LoginScreen
import com.hos.rushdpatients.ui.login.PinUnlockScreen
import com.hos.rushdpatients.ui.navigation.WardNavHost
import com.hos.rushdpatients.ui.setup.ProjectSetupScreen
import com.hos.rushdpatients.ui.demo.DemoWardScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val uiText: UiStrings,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
    private val biometricHelper: BiometricHelper,
    private val auditRepository: AuditRepository,
    private val doctorRepository: DoctorRepository,
    private val localPinLogin: com.hos.rushdpatients.domain.auth.LocalPinLoginManager,
    private val projectConfigStore: ProjectConfigStore
) : ViewModel() {

    val projectConfig = projectConfigStore.config

    val session: StateFlow<Session?> = sessionManager.sessionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = sessionManager.current()
    )

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    private val _unlocking = MutableStateFlow(false)
    val unlocking: StateFlow<Boolean> = _unlocking.asStateFlow()

    private val _biometricAvailable = MutableStateFlow(false)
    val biometricAvailable: StateFlow<Boolean> = _biometricAvailable.asStateFlow()
    private var unlockRequired = false

    private var backgroundedAt: Instant? = null

    fun onBackground() {
        backgroundedAt = Instant.now()
    }

    suspend fun shouldPromptUnlock(): Boolean {
        val current = sessionManager.current() ?: run {
            unlockRequired = false
            return false
        }
        _biometricAvailable.value = biometricHelper.isAvailable() &&
            settingsRepository.getBoolean(AppConstants.SETTING_BIOMETRIC_ENABLED, true)
        if (doctorRepository.getActiveById(current.doctorId) == null) {
            sessionManager.clear()
            unlockRequired = false
            return false
        }
        val threshold = settingsRepository.getAutoLockMinutes().toLong()
        val inactiveSince = backgroundedAt ?: current.unlockedAt
        backgroundedAt = null
        // Once locked, a cancelled system prompt/short background visit cannot
        // clear the lock merely because its new inactivity interval is short.
        unlockRequired = unlockRequired || Duration.between(inactiveSince, Instant.now()).toMinutes() >= threshold
        return unlockRequired
    }

    fun unlockWithPin(pin: String) {
        val current = sessionManager.current() ?: return
        if (_unlocking.value) return
        _unlocking.value = true
        viewModelScope.launch {
            try {
                val doctor = localPinLogin.verify(current.doctorId, pin)
                if (sessionManager.current() != current) return@launch
                if (doctor.telegramId != current.telegramId) {
                    signOut()
                    return@launch
                }
                _unlockError.value = null
                unlockRequired = false
                sessionManager.markUnlocked()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _unlockError.value = com.hos.rushdpatients.ui.components.failureText(uiText, e) ?: uiText(R.string.ui_pin_unlock_retry)
            } finally {
                _unlocking.value = false
            }
        }
    }

    fun markUnlockedAfterBiometric(expected: Session) {
        if (_unlocking.value || sessionManager.current() != expected) return
        _unlocking.value = true
        viewModelScope.launch {
            try {
                val doctor = doctorRepository.getActiveById(expected.doctorId)
                if (sessionManager.current() != expected) return@launch
                if (doctor == null || doctor.telegramId != expected.telegramId) {
                    signOut()
                    return@launch
                }
                _unlockError.value = null
                unlockRequired = false
                sessionManager.markUnlocked()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _unlockError.value = uiText(R.string.ui_biometric_retry_pin)
            } finally {
                _unlocking.value = false
            }
        }
    }

    fun biometricFailed() {
        _unlockError.value = uiText(R.string.ui_biometric_retry_pin)
    }

    fun biometricHelper(): BiometricHelper = biometricHelper

    fun signOut() {
        unlockRequired = false
        val actor = sessionManager.current()
        sessionManager.clear()
        viewModelScope.launch {
            runCatching {
                auditRepository.record(
                    actorDoctorId = actor?.doctorId,
                    actorName = actor?.doctorName,
                    action = AppConstants.AUDIT_LOGOUT
                )
            }
        }
    }

    fun exitDemo() {
        sessionManager.clear()
        projectConfigStore.clear()
    }
}

@Composable
fun WardAppRoot(
    activity: FragmentActivity,
    viewModel: RootViewModel = hiltViewModel(),
    navController: @Composable () -> NavHostController = { rememberNavController() }
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val unlockError by viewModel.unlockError.collectAsStateWithLifecycle()
    val unlocking by viewModel.unlocking.collectAsStateWithLifecycle()
    val biometricAvailable by viewModel.biometricAvailable.collectAsStateWithLifecycle()
    val projectConfig by viewModel.projectConfig.collectAsStateWithLifecycle()

    var needsUnlock by remember { mutableStateOf(false) }
    var unlockDecisionReady by remember { mutableStateOf(false) }
    var foregroundTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foregroundTick++
                Lifecycle.Event.ON_STOP -> {
                    unlockDecisionReady = false
                    viewModel.onBackground()
                    // Replace clinical content before Android captures the background task card.
                    if (session != null) needsUnlock = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Decide whether to prompt for PIN on this foreground entry.
    androidx.compose.runtime.LaunchedEffect(session, foregroundTick) {
        val current = session
        if (current == null) {
            needsUnlock = false
            return@LaunchedEffect
        }
        needsUnlock = viewModel.shouldPromptUnlock()
        unlockDecisionReady = true
    }

    val current = session
    // A temporary privacy/PIN screen must not discard the back stack or editor ViewModels.
    // Clinical screens still leave composition while locked; sign-out discards this controller.
    val clinicalNavigation = if (projectConfig.initialized && !projectConfig.demoMode && current != null) {
        navController()
    } else null
    // NavHost's screen state includes the saved ActivityResult registration keys.
    // Keep it while privacy hides the host, so a real picker result returns to
    // the same registration. Signing out removes both this holder and controller.
    val clinicalScreenState = if (clinicalNavigation != null) rememberSaveableStateHolder() else null
    when {
        !projectConfig.initialized -> ProjectSetupScreen()
        projectConfig.demoMode -> DemoWardScreen(onExit = viewModel::exitDemo)
        current == null -> LoginScreen()
        !unlockDecisionReady -> Column(Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.app_emblem_transparent), contentDescription = null,
                modifier = Modifier.padding(com.hos.rushdpatients.ui.theme.UiSpace.xxxl))
        }
        needsUnlock -> PinUnlockScreen(
            doctorName = current.doctorName,
            error = unlockError,
            busy = unlocking,
            onUnlock = { pin ->
                viewModel.unlockWithPin(pin)
            },
            onBiometric = if (unlockDecisionReady && biometricAvailable) {
                {
                    viewModel.biometricHelper().prompt(
                        activity = activity,
                        onSuccess = {
                            viewModel.markUnlockedAfterBiometric(current)
                        },
                        onError = { viewModel.biometricFailed() },
                        onCancel = {}
                    )
                }
            } else null,
            onSignOut = {
                viewModel.signOut()
                needsUnlock = false
            }
        )
        else -> checkNotNull(clinicalScreenState).SaveableStateProvider("clinical-navigation") {
            WardNavHost(
                session = current,
                navController = checkNotNull(clinicalNavigation),
                onSignOut = {
                    viewModel.signOut()
                    needsUnlock = false
                }
            )
        }
    }
}
