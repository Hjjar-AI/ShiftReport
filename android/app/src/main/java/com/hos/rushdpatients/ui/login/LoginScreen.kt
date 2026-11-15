package com.hos.rushdpatients.ui.login

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.components.AppBusyIndicator
import com.hos.rushdpatients.ui.components.AppSearchField
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppCenteredContent
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiOpacity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.connection.ProjectConnectionAction

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel(), recoveryDoctorId: String? = null,
    onRecoveryConsumed: () -> Unit = {}) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(recoveryDoctorId, state.step) {
        if (recoveryDoctorId != null && state.step is LoginStep.PickDoctor && !state.busy) {
            onRecoveryConsumed()
            viewModel.startPinRecovery(recoveryDoctorId)
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            when (val step = state.step) {
                is LoginStep.Bootstrapping -> com.hos.rushdpatients.ui.components.AppStateHost(
                    loading = true, loadingMessage = uiText(R.string.ui_login_screen_001))
                is LoginStep.BootstrapFailed -> com.hos.rushdpatients.ui.components.AppStateHost(
                    loading = false, loadingMessage = uiText(R.string.ui_login_screen_001),
                    error = uiText(R.string.ui_login_screen_002, step.message), retryLabel = uiText(R.string.ui_retry), onRetry = viewModel::boot)
                is LoginStep.PickDoctor -> PickDoctorList(
                    projectName = state.projectName,
                    doctors = step.doctors,
                    enrolledDoctorIds = state.enrolledDoctorIds,
                    error = state.error,
                    notice = state.notice, busy = state.busy, refreshing = state.refreshingDoctors,
                    onRefresh = viewModel::refreshDoctors,
                    onPick = viewModel::pickDoctor
                )
                is LoginStep.EnterPin -> LocalPinLoginScreen(
                    doctorId = step.doctor.id,
                    doctorName = step.doctor.fullName,
                    busy = state.busy,
                    error = state.error,
                    onLogin = viewModel::loginWithPin,
                    onTelegram = { viewModel.verifyViaTelegram(step.doctor) },
                    onForgotPin = { viewModel.startPinRecovery(step.doctor.id) },
                    onBack = viewModel::backToDoctors
                )
                is LoginStep.RecoverPin -> AppCenteredContent {
                    Text(step.doctor.fullName, style = MaterialTheme.typography.titleLarge)
                    Text(uiText(R.string.pin_recovery_instructions))
                    state.error?.let { AppNotice(it, kind = NoticeKind.ERROR) }
                    ProjectConnectionAction(recoveryDoctorId = step.doctor.id,
                        onRecoveryReady = viewModel::onRecoveryRejoined,
                        onRecoveryCancelled = viewModel::backToDoctors, enabled = !state.busy)
                }
                is LoginStep.Verifying -> VerifyingView(step, state.busy, state.error,
                    viewModel::verifyTelegramCode, viewModel::cancelVerification)
                is LoginStep.VerifyFailed -> VerifyFailedView(
                    step,
                    viewModel::retryVerify,
                    viewModel::backToDoctors
                )
                is LoginStep.SetPin -> SetPinScreen(
                    doctor = step.doctor,
                    error = state.error,
                    busy = state.busy,
                    onConfirm = { pin, confirm ->
                        viewModel.setPin(step.doctor, step.telegramId, pin, confirm)
                    },
                    onCancel = viewModel::backToDoctors
                )
            }
        }
        // Recovery must remain reachable when a revoked token prevents login/bootstrap.
        Column(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screen)) {
            if (state.step !is LoginStep.RecoverPin) {
                ProjectConnectionAction(onApplied = viewModel::boot, enabled = !state.busy)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
private fun PickDoctorList(
    projectName: String,
    doctors: List<Doctor>,
    enrolledDoctorIds: Set<String>,
    error: String?,
    notice: String?, busy: Boolean, refreshing: Boolean, onRefresh: () -> Unit,
    onPick: (Doctor) -> Unit
) {
    val uiText = rememberUiStrings()
    var query by rememberSaveable { mutableStateOf("") }
    val matchingDoctors = remember(doctors, query) {
        doctors.filter { ArabicSearchNormalizer.matches(query, it.fullName) }
    }
    val refreshState = rememberPullRefreshState(refreshing, onRefresh)
    val refreshLabel = uiText(R.string.login_refresh_action)
    Box(Modifier.fillMaxSize().pullRefresh(refreshState, enabled = !busy).semantics {
        customActions = listOf(CustomAccessibilityAction(refreshLabel) {
            if (busy) false else { onRefresh(); true }
        })
    }) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(UiSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    if (projectName.isNotBlank()) {
                        Text(projectName, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text(uiText(R.string.ui_login_screen_003), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        uiText(R.string.ui_login_screen_004),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(uiText(R.string.login_pull_refresh_hint), style = MaterialTheme.typography.bodySmall)
                    AppSearchField(query = query, onQueryChange = { query = it }, label = uiText(R.string.ui_shift_doctor_picker_003))
                    error?.let { AppNotice(it, kind = NoticeKind.ERROR) }
                    notice?.let { AppNotice(it, kind = NoticeKind.SUCCESS) }
                }
            }
            if (matchingDoctors.isEmpty()) {
                item {
                    Text(if (doctors.isEmpty()) uiText(R.string.ui_login_screen_005) else uiText(R.string.ui_shift_doctor_picker_006))
                }
            }
            items(matchingDoctors, key = { it.id }) { doctor ->
                val linked = doctor.telegramId != null
                val enabled = linked && !busy
                val hasPin = doctor.id in enrolledDoctorIds
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(UiOpacity.content(enabled))
                        .clickable(enabled = enabled, role = Role.Button) { onPick(doctor) }
                ) {
                    Column(modifier = Modifier.padding(UiSpacing.screen)) {
                        Text(doctor.fullName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = when {
                                !linked -> uiText(R.string.ui_login_screen_006)
                                hasPin -> uiText(R.string.ui_login_screen_007)
                                else -> uiText(R.string.ui_login_screen_008)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (linked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        PullRefreshIndicator(refreshing, refreshState, modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun VerifyingView(step: LoginStep.Verifying, busy: Boolean, error: String?,
    onVerify: (String) -> Unit, onCancel: () -> Unit) {
    val uiText = rememberUiStrings()
    // Never save a submitted login code in a Bundle or clipboard.
    var code by remember(step.doctor.id) { mutableStateOf("") }
    AppCenteredContent(modifier = Modifier.imePadding()) {
        Text(uiText(R.string.ui_login_screen_009), style = MaterialTheme.typography.headlineSmall)
        Text(uiText(R.string.login_private_code_instructions), textAlign = TextAlign.Center)
        com.hos.rushdpatients.ui.components.AppTextField(
            value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
            label = { Text(uiText(R.string.login_private_code_label)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        error?.let { AppNotice(it, kind = NoticeKind.ERROR) }
        Text(uiText(R.string.ui_login_screen_011, uiNumber(step.remainingSeconds)), style = MaterialTheme.typography.bodySmall)
        AppButton(enabled = !busy && code.length == 6 && step.remainingSeconds > 0,
            onClick = { val submitted = code; code = ""; onVerify(submitted) }) {
            Text(uiText(R.string.login_private_code_confirm))
        }
        AppTextButton(onClick = onCancel, enabled = !busy) { Text(uiText(R.string.ui_cancel)) }
    }
}

@Composable
private fun VerifyFailedView(
    step: LoginStep.VerifyFailed,
    onRetry: (Doctor) -> Unit,
    onBack: () -> Unit
) {
    val uiText = rememberUiStrings()
    AppCenteredContent() {
        Text(uiText(R.string.ui_login_screen_012), style = MaterialTheme.typography.headlineSmall)
        AppNotice(step.message, kind = NoticeKind.ERROR)
        AppButton(onClick = { onRetry(step.doctor) }) { Text(uiText(R.string.ui_retry)) }
        AppTextButton(onClick = onBack) { Text(uiText(R.string.ui_back)) }
    }
}
