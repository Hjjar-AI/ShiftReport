package com.hos.rushdpatients.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.BiometricHelper
import com.hos.rushdpatients.domain.auth.PasswordHasher
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.ui.login.LoginScreen
import com.hos.rushdpatients.ui.about.IntroAboutScreen
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
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
    private val biometricHelper: BiometricHelper,
    private val auditRepository: AuditRepository,
    private val doctorRepository: DoctorRepository,
    private val hasher: PasswordHasher,
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

    val biometricAvailable: Boolean = biometricHelper.isAvailable()

    private var backgroundedAt: Instant? = null
    private var failedPinAttempts = 0

    fun onBackground() {
        backgroundedAt = Instant.now()
    }

    suspend fun shouldPromptUnlock(): Boolean {
        val current = sessionManager.current() ?: return false
        if (doctorRepository.getActiveById(current.doctorId) == null) {
            sessionManager.clear()
            return false
        }
        val threshold = settingsRepository.getAutoLockMinutes().toLong()
        val inactiveSince = backgroundedAt ?: current.unlockedAt
        backgroundedAt = null
        return Duration.between(inactiveSince, Instant.now()).toMinutes() >= threshold
    }

    fun unlockWithPin(pin: String) {
        val current = sessionManager.current() ?: return
        viewModelScope.launch {
            _unlocking.value = true
            val doctor = doctorRepository.getById(current.doctorId)
            val stored = doctor?.extraOptions
                ?.firstOrNull { it.startsWith("pin:") }
                ?.removePrefix("pin:")
            val ok = stored != null && hasher.verify(pin, stored)
            _unlocking.value = false
            if (ok) {
                failedPinAttempts = 0
                _unlockError.value = null
                sessionManager.markUnlocked()
            } else {
                failedPinAttempts++
                _unlockError.value = if (failedPinAttempts >= AppConstants.LOGIN_MAX_ATTEMPTS) {
                    signOut()
                    "تم تسجيل الخروج بعد محاولات خاطئة متعددة"
                } else {
                    "الرقم السري غير صحيح"
                }
            }
        }
    }

    fun markUnlockedAfterBiometric() {
        sessionManager.markUnlocked()
    }

    fun biometricHelper(): BiometricHelper = biometricHelper

    fun signOut() {
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
    viewModel: RootViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val unlockError by viewModel.unlockError.collectAsStateWithLifecycle()
    val unlocking by viewModel.unlocking.collectAsStateWithLifecycle()
    val projectConfig by viewModel.projectConfig.collectAsStateWithLifecycle()

    var needsUnlock by remember { mutableStateOf(false) }
    var showingSplash by remember { mutableStateOf(true) }
    var foregroundTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foregroundTick++
                Lifecycle.Event.ON_STOP -> {
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
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(12_000)
        showingSplash = false
    }

    if (showingSplash) {
        IntroAboutScreen(splash = true, onBack = { showingSplash = false })
        return
    }

    val current = session
    when {
        !projectConfig.initialized -> ProjectSetupScreen()
        projectConfig.demoMode -> DemoWardScreen(onExit = viewModel::exitDemo)
        current == null -> LoginScreen()
        needsUnlock -> PinUnlockScreen(
            doctorName = current.doctorName,
            error = unlockError,
            busy = unlocking,
            onUnlock = { pin ->
                viewModel.unlockWithPin(pin)
            },
            onBiometric = if (viewModel.biometricAvailable) {
                {
                    viewModel.biometricHelper().prompt(
                        activity = activity,
                        onSuccess = {
                            viewModel.markUnlockedAfterBiometric()
                            needsUnlock = false
                        },
                        onError = {},
                        onCancel = {}
                    )
                }
            } else null,
            onSignOut = {
                viewModel.signOut()
                needsUnlock = false
            }
        )
        else -> WardNavHost(
            session = current,
            onSignOut = {
                viewModel.signOut()
                needsUnlock = false
            }
        )
    }
}
