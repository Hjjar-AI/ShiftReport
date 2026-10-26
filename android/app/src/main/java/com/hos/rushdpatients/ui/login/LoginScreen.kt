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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiOpacity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.connection.ProjectConnectionAction

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                    error = state.error,
                    onPick = viewModel::pickDoctor
                )
                is LoginStep.EnterPin -> LocalPinLoginScreen(
                    doctorId = step.doctor.id,
                    doctorName = step.doctor.fullName,
                    busy = state.busy,
                    error = state.error,
                    onLogin = viewModel::loginWithPin,
                    onTelegram = { viewModel.verifyViaTelegram(step.doctor) },
                    onBack = viewModel::backToDoctors
                )
                is LoginStep.Verifying -> VerifyingView(step, viewModel::cancelVerification)
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
            ProjectConnectionAction(onApplied = viewModel::boot)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDoctorList(
    projectName: String,
    doctors: List<Doctor>,
    error: String?,
    onPick: (Doctor) -> Unit
) {
    val uiText = rememberUiStrings()
    var query by rememberSaveable { mutableStateOf("") }
    val matchingDoctors = remember(doctors, query) {
        doctors.filter { ArabicSearchNormalizer.matches(query, it.fullName) }
    }
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
                AppSearchField(query = query, onQueryChange = { query = it }, label = uiText(R.string.ui_shift_doctor_picker_003))
                error?.let { AppNotice(it, kind = NoticeKind.ERROR) }
            }
        }
        if (matchingDoctors.isEmpty()) {
            item {
                Text(if (doctors.isEmpty()) uiText(R.string.ui_login_screen_005) else uiText(R.string.ui_shift_doctor_picker_006))
            }
        }
        items(matchingDoctors, key = { it.id }) { doctor ->
            val enabled = doctor.telegramId != null
            val hasPin = doctor.options.pinHash != null
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
                            !enabled -> uiText(R.string.ui_login_screen_006)
                            hasPin -> uiText(R.string.ui_login_screen_007)
                            else -> uiText(R.string.ui_login_screen_008)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
internal fun VerifyingView(step: LoginStep.Verifying, onCancel: () -> Unit) {
    val uiText = rememberUiStrings()
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    fun clearNonce() {
        // Only remove this verification code; a later user copy belongs to the user.
        try {
            val clip = clipboard.primaryClip
            if (clip?.description?.label == "ShiftReport verification" && clip.itemCount == 1 &&
                clip.getItemAt(0).text?.toString() == step.nonce) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: SecurityException) {
            // Android may deny clipboard access when the app has lost focus.
        }
    }
    DisposableEffect(clipboard, step.nonce) { onDispose { clearNonce() } }
    LaunchedEffect(step.remainingSeconds) { if (step.remainingSeconds <= 0) clearNonce() }
    AppCenteredContent() {
        Text(uiText(R.string.ui_login_screen_009), style = MaterialTheme.typography.headlineSmall)
        Text(
            uiText(R.string.ui_login_screen_010),
            modifier = Modifier.padding(top = UiSpacing.screen),
            textAlign = TextAlign.Center
        )
        Text(
            text = step.nonce,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.padding(vertical = UiSpacing.screen)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppOutlinedButton(enabled = step.remainingSeconds > 0, onClick = {
                val clip = ClipData.newPlainText("ShiftReport verification", step.nonce)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                }
                clipboard.setPrimaryClip(clip)
            }) { Text(uiText(R.string.ui_copy)) }
        }
        Text(
            uiText(R.string.ui_login_screen_011, uiNumber(step.remainingSeconds)),
            modifier = Modifier.padding(top = UiSpacing.section),
            style = MaterialTheme.typography.bodySmall
        )
        AppBusyIndicator(modifier = Modifier.padding(top = UiSpacing.screen), size = UiSize.loadingIndicator, color = MaterialTheme.colorScheme.primary)
        AppTextButton(onClick = onCancel, modifier = Modifier.padding(top = UiSpacing.screen)) { Text(uiText(R.string.ui_cancel)) }
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
