package com.hos.rushdpatients.ui.login

import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppCenteredContent
import com.hos.rushdpatients.ui.components.AppTextField
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.connection.ProjectConnectionAction
import com.hos.rushdpatients.ui.components.LongPressTriggerButton

/** Seconds the retry button must be held to fire the hidden seed. */
private const val HIDDEN_SEED_HOLD_MS = 15_000L

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            when (val step = state.step) {
                is LoginStep.Bootstrapping -> Centered { CircularProgressIndicator() }
                is LoginStep.BootstrapFailed -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("تعذر تحميل سجل الأطباء", style = MaterialTheme.typography.titleLarge)
                        Text(
                            step.message,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(UiSpacing.screen)
                        )
                        if (state.seeding) {
                            CircularProgressIndicator()
                        } else {
                            LongPressTriggerButton(
                                text = "إعادة المحاولة",
                                holdDurationMs = HIDDEN_SEED_HOLD_MS,
                                onClick = viewModel::boot,
                                onHoldTriggered = viewModel::seedBootstrap
                            )
                        }
                    }
                }
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
    var query by rememberSaveable { mutableStateOf("") }
    val matchingDoctors = doctors.filter { ArabicSearchNormalizer.matches(query, it.fullName) }
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
                Text("اختر اسمك", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "المشروع محفوظ. اختر اسمك للدخول مجدداً؛ ملف الانضمام مطلوب لإعداد جهاز جديد.",
                    style = MaterialTheme.typography.bodySmall
                )
                AppTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("بحث باسم الطبيب") },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { AppNotice(it, kind = NoticeKind.ERROR) }
            }
        }
        if (matchingDoctors.isEmpty()) {
            item {
                Text(if (doctors.isEmpty()) "لا يوجد أطباء في السجل" else "لا توجد أسماء مطابقة")
            }
        }
        items(matchingDoctors, key = { it.id }) { doctor ->
            val enabled = doctor.telegramId != null
            val hasPin = doctor.extraOptions.any { it.startsWith("pin:") }
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onPick(doctor) }
            ) {
                Column(modifier = Modifier.padding(UiSpacing.screen)) {
                    Text(doctor.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            !enabled -> "لم يتم ربط حساب تليجرام — تواصل مع المدير"
                            hasPin -> "اضغط للدخول برقمك السري على هذا الجهاز"
                            else -> "اضغط للتحقق عبر تليجرام"
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
private fun VerifyingView(step: LoginStep.Verifying, onCancel: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AppCenteredContent() {
        Text("التحقق عبر تليجرام", style = MaterialTheme.typography.headlineSmall)
        Text(
            "أرسل الرمز التالي إلى البوت في قسم General",
            modifier = Modifier.padding(top = UiSpacing.screen),
            textAlign = TextAlign.Center
        )
        Text(
            text = step.nonce,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = UiSpacing.screen)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppOutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(step.nonce))
            }) { Text("نسخ") }
        }
        Text(
            "الوقت المتبقي: ${step.remainingSeconds} ثانية",
            modifier = Modifier.padding(top = UiSpacing.section),
            style = MaterialTheme.typography.bodySmall
        )
        CircularProgressIndicator(modifier = Modifier.padding(top = UiSpacing.screen))
        AppTextButton(onClick = onCancel, modifier = Modifier.padding(top = UiSpacing.screen)) { Text("إلغاء") }
    }
}

@Composable
private fun VerifyFailedView(
    step: LoginStep.VerifyFailed,
    onRetry: (Doctor) -> Unit,
    onBack: () -> Unit
) {
    AppCenteredContent() {
        Text("فشل التحقق", style = MaterialTheme.typography.headlineSmall)
        AppNotice(step.message, kind = NoticeKind.ERROR)
        AppButton(onClick = { onRetry(step.doctor) }) { Text("إعادة المحاولة") }
        AppTextButton(onClick = onBack) { Text("رجوع") }
    }
}
