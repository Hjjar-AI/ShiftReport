package com.hos.rushdpatients.ui.connection

import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** File recovery is available before login; manual token replacement still requires live admin authority. */
@Composable
fun ProjectConnectionAction(
    isAdmin: Boolean = false,
    onApplied: () -> Unit = {},
    viewModel: ProjectConnectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var open by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember(selectedFile) { mutableStateOf<String?>(null) }
    var secret by remember { mutableStateOf("") }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFile = uri
            secret = ""
        }
    }
    LaunchedEffect(state.applied) { if (state.applied) onApplied() }
    LaunchedEffect(selectedFile) {
        selectedFileName = selectedFile?.let { viewModel.readDisplayName(it) }
    }

    AppOutlinedButton(onClick = {
        viewModel.reset()
        manual = false
        selectedFile = null
        secret = ""
        pickerError = null
        open = true
    }, modifier = Modifier.fillMaxWidth()) { Text("إعادة الاتصال بالمشروع") }

    if (open) {
        val dismiss = {
            if (!state.busy) {
                open = false
                selectedFile = null
                secret = ""
                viewModel.reset()
            }
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(if (manual) "استبدال رمز البوت" else "إعادة الاتصال بالمشروع") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                ) {
                    when {
                        state.reviewProject != null -> {
                            Text("تم التحقق من اتصال ${state.reviewProject}.")
                            Text("سيُستبدل رمز الاتصال فقط. سيبقى مفتاح التشفير والمرضى والمناوبات والتعديلات غير المرسلة كما هي.")
                        }
                        state.applied -> Unit
                        else -> {
                            Text("استخدم ملف الانضمام المحدّث من المدير. يُرفض ملف مشروع آخر أو مفتاح مختلف لحماية البيانات المحلية.")
                            if (manual) {
                                Text("ألغِ الرمز القديم وأنشئ بديلاً للبوت نفسه عبر BotFather، ثم أدخله هنا. بعد الحفظ صدّر ملف انضمام جديداً ووزّعه على الأجهزة. إلغاء الرمز يوقف اتصال الأجهزة التي تستخدمه.")
                                AppTextButton(onClick = {
                                    try {
                                        uriHandler.openUri("https://t.me/BotFather")
                                    } catch (_: Exception) {
                                        pickerError = "افتح @BotFather في تليجرام"
                                    }
                                }, enabled = !state.busy) { Text("فتح BotFather") }
                            } else {
                                AppOutlinedButton(onClick = {
                                    pickerError = null
                                    try {
                                        picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                                    } catch (_: Exception) {
                                        pickerError = "تعذر فتح منتقي الملفات؛ أعد المحاولة"
                                    }
                                }, enabled = !state.busy) {
                                    Text(if (selectedFile == null) "اختيار ملف .srjoin.json" else "تغيير الملف المختار")
                                }
                                if (selectedFile != null) Text(
                                    "الملف المختار: ${selectedFileName ?: "اسم الملف غير متاح"}",
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            AppTextField(
                                value = secret,
                                onValueChange = { secret = it },
                                label = { Text(if (manual) "الرمز الجديد" else "عبارة مرور الملف") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isAdmin) AppTextButton(onClick = {
                                manual = !manual
                                selectedFile = null
                                secret = ""
                                pickerError = null
                                viewModel.reset()
                            }, enabled = !state.busy) {
                                Text(if (manual) "استخدام ملف الانضمام" else "استبدال الرمز كمدير")
                            }
                        }
                    }
                    if (state.busy) CircularProgressIndicator()
                    (state.message ?: pickerError)?.let {
                        AppNotice(it, kind = if (state.applied) NoticeKind.SUCCESS else NoticeKind.ERROR)
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = !state.busy && (state.applied || state.reviewProject != null ||
                        (manual && secret.isNotBlank()) ||
                        (!manual && selectedFile != null && secret.length >= 10)),
                    onClick = {
                        when {
                            state.applied -> dismiss()
                            state.reviewProject != null -> viewModel.confirm()
                            manual -> { viewModel.replaceToken(secret); secret = "" }
                            else -> selectedFile?.let { viewModel.importFile(it, secret); secret = "" }
                        }
                    }
                ) {
                    Text(when {
                        state.applied -> "تم"
                        state.reviewProject != null -> "حفظ الاتصال"
                        else -> "التحقق"
                    })
                }
            },
            dismissButton = {
                if (!state.applied) AppTextButton(onClick = dismiss, enabled = !state.busy) { Text("إلغاء") }
            }
        )
    }
}
