package com.hos.rushdpatients.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Doctor
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
                            modifier = Modifier.padding(16.dp)
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ProjectConnectionAction(onApplied = viewModel::boot)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun PickDoctorList(
    projectName: String,
    doctors: List<Doctor>,
    error: String?,
    onPick: (Doctor) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        if (projectName.isNotBlank()) {
            Text(
                projectName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text("اختر اسمك", style = MaterialTheme.typography.headlineSmall)
        Text(
            "المشروع محفوظ. اختر اسمك للدخول مجدداً؛ ملف الانضمام مطلوب لإعداد جهاز جديد.",
            style = MaterialTheme.typography.bodySmall
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        // NOTE: Do NOT use `return` inside a composable lambda — early returns
        // corrupt Compose's group stack and crash with AIOOBE in Stack.pop on
        // recomposition. Use if/else instead.
        if (doctors.isEmpty()) {
            Text("لا يوجد أطباء في السجل", modifier = Modifier.padding(top = 24.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(doctors, key = { it.id }) { doctor ->
                    val enabled = doctor.telegramId != null
                    val hasPin = doctor.extraOptions.any { it.startsWith("pin:") }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onPick(doctor) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
    }
}

@Composable
private fun VerifyingView(step: LoginStep.Verifying, onCancel: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("التحقق عبر تليجرام", style = MaterialTheme.typography.headlineSmall)
        Text(
            "أرسل الرمز التالي إلى البوت في قسم General",
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center
        )
        Text(
            text = step.nonce,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(step.nonce))
            }) { Text("نسخ") }
        }
        Text(
            "الوقت المتبقي: ${step.remainingSeconds} ثانية",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodySmall
        )
        CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) { Text("إلغاء") }
    }
}

@Composable
private fun VerifyFailedView(
    step: LoginStep.VerifyFailed,
    onRetry: (Doctor) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("فشل التحقق", style = MaterialTheme.typography.headlineSmall)
        Text(step.message, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
        Button(onClick = { onRetry(step.doctor) }) { Text("إعادة المحاولة") }
        TextButton(onClick = onBack) { Text("رجوع") }
    }
}
