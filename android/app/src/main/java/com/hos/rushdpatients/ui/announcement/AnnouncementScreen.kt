package com.hos.rushdpatients.ui.announcement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreen(onBack: () -> Unit, viewModel: AnnouncementViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val busy = state.sending || state.reviewing || state.resolving || state.loading
    var resolution by remember { mutableStateOf<Pair<ReportDeliveryRecord, Boolean>?>(null) }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbar.showSnackbar(it); viewModel.dismissSnackbar() }
    }
    Scaffold(topBar = { AppTopBar(title = "إعلان المجموعة", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(UiSpacing.screen).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)) {
            Text("ينشر في وجهة إعلانات المشروع؛ يبقى فهرس المزامنة مثبتاً. يُرسل النص كما تراجعه، مع تشفير المشروع عند تفعيله.",style = MaterialTheme.typography.bodyMedium)
            if (state.loading) Text("جارٍ قراءة حالة الإعلان…")
            state.lastMessageId?.let { Text("الإعلان الحالي: $it",style = MaterialTheme.typography.bodySmall) }
            AppTextField(value = state.template,onValueChange = viewModel::onTemplateChange,label = { Text("نص الإعلان") },
                enabled = !busy,placeholder = { Text("اكتب إعلاناً عاماً للمجموعة…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),maxLines = 20)
            LoadingButton(text = "مراجعة الإعلان",loading = state.reviewing,onClick = viewModel::review,
                enabled = !busy && !state.loading && !state.ledgerUnavailable && state.deliveryRecords.isEmpty(),modifier = Modifier.fillMaxWidth())
            AppTextButton(onClick = viewModel::refresh,enabled = !busy) { Text("تحديث حالة الإعلان") }
            if (state.ledgerUnavailable) Text("سجل التسليم غير متاح؛ الإرسال محظور حتى إصلاحه.")
            state.deliveryRecords.forEach { row ->
                Text("نشر إعلان غير مؤكد؛ افحص الرسالة والفهرس في تليجرام قبل أي إعادة.")
                AppTextButton(onClick = { resolution = row to true },enabled = !busy) { Text("تحققت من وجود الإعلان") }
                AppTextButton(onClick = { resolution = row to false },enabled = !busy) { Text("تحققت أنه لم يُنشر") }
            }
        }
    }
    state.reviewText?.let { text ->
        AlertDialog(onDismissRequest = viewModel::dismissReview,title = { Text(if (state.reviewExisting) "مراجعة تحديث الإعلان" else "مراجعة إعلان جديد") },
            text = { Text(text,Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { AppTextButton(onClick = viewModel::confirmSend,enabled = !busy) { Text("تأكيد النشر") } },
            dismissButton = { AppTextButton(onClick = viewModel::dismissReview,enabled = !busy) { Text("إلغاء") } })
    }
    resolution?.let { (row,received) ->
        var messageId by remember(row.id,received) { mutableStateOf("") }
        val parsed = messageId.map { if (Character.isDigit(it)) Character.digit(it,10).toString() else it.toString() }.joinToString("").trim().toLongOrNull()
        AlertDialog(onDismissRequest = { if (!busy) resolution = null },title = { Text("تأكيد حسم التسليم") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text(if (received) "أدخل معرّف رسالة الإعلان الذي تحققت من نصه في تليجرام. تُصلح إحالة مفقودة دون استبدال إعلان أحدث."
                         else "تأكد أن الإعلان لم يُنشر قبل فتح محاولة جديدة؛ لا تكفي رسالة خطأ أو انقطاع اتصال لإثبات ذلك.")
                    if (received) AppTextField(messageId,{ messageId = it },label = { Text("معرّف رسالة الإعلان") },singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { AppTextButton(onClick = { viewModel.resolve(row,if (received) parsed else null); resolution = null },
                enabled = !busy && (!received || (parsed != null && parsed > 0))) { Text("تأكيد ما تحققت منه") } },
            dismissButton = { AppTextButton(onClick = { resolution = null },enabled = !busy) { Text("إلغاء") } })
    }
}
