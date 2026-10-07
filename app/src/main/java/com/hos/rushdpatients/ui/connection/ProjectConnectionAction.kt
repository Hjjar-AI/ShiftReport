package com.hos.rushdpatients.ui.connection

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    var secret by remember { mutableStateOf("") }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFile = uri
            secret = ""
        }
    }
    LaunchedEffect(state.applied) { if (state.applied) onApplied() }

    OutlinedButton(onClick = {
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                TextButton(onClick = {
                                    try {
                                        uriHandler.openUri("https://t.me/BotFather")
                                    } catch (_: Exception) {
                                        pickerError = "افتح @BotFather في تليجرام"
                                    }
                                }, enabled = !state.busy) { Text("فتح BotFather") }
                            } else {
                                OutlinedButton(onClick = {
                                    pickerError = null
                                    try {
                                        picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                                    } catch (_: Exception) {
                                        pickerError = "تعذر فتح منتقي الملفات؛ أعد المحاولة"
                                    }
                                }, enabled = !state.busy) {
                                    Text(if (selectedFile == null) "اختيار ملف .srjoin.json" else "تغيير الملف المختار")
                                }
                                if (selectedFile != null) Text("تم اختيار الملف")
                            }
                            OutlinedTextField(
                                value = secret,
                                onValueChange = { secret = it },
                                label = { Text(if (manual) "الرمز الجديد" else "عبارة مرور الملف") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isAdmin) TextButton(onClick = {
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
                        Text(it, color = if (state.applied) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
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
                if (!state.applied) TextButton(onClick = dismiss, enabled = !state.busy) { Text("إلغاء") }
            }
        )
    }
}
