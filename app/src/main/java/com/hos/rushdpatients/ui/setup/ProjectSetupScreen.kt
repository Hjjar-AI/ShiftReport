package com.hos.rushdpatients.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.ui.components.PasswordField

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProjectSetupScreen(
    viewModel: ProjectSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "تهيئة ShiftReport",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "اربط هذا الجهاز بمشروع المستشفى. يبقى شعار التطبيق وصفحة الاعتمادات كما هما، بينما تصبح بيانات المستشفى والبوت خاصة بكل مشروع.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == ProjectSetupMode.CREATE,
                onClick = { viewModel.setMode(ProjectSetupMode.CREATE) },
                label = { Text("إنشاء مشروع") },
                enabled = !state.busy
            )
            FilterChip(
                selected = state.mode == ProjectSetupMode.JOIN,
                onClick = { viewModel.setMode(ProjectSetupMode.JOIN) },
                label = { Text("الانضمام لمشروع") },
                enabled = !state.busy
            )
        }

        InstructionCard(state.mode)

        OutlinedTextField(
            value = state.hospitalName,
            onValueChange = viewModel::setHospitalName,
            label = { Text("اسم المستشفى أو المشروع") },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(
            value = state.botToken,
            onValueChange = viewModel::setBotToken,
            label = "رمز البوت من BotFather",
            enabled = !state.busy
        )
        NumberField(
            value = state.chatId,
            onValueChange = viewModel::setChatId,
            label = "معرف المجموعة الرئيسي (رقم سالب)",
            signed = true,
            enabled = !state.busy
        )

        Text("معرفات المواضيع", style = MaterialTheme.typography.titleMedium)
        Text(
            "اختيارية. اترك الحقل فارغاً للنشر في General.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NumberField(state.reportsTopicId, viewModel::setReportsTopicId, "موضوع التقارير", enabled = !state.busy)
        NumberField(state.announcementsTopicId, viewModel::setAnnouncementsTopicId, "موضوع الإعلانات", enabled = !state.busy)
        NumberField(state.csvTopicId, viewModel::setCsvTopicId, "موضوع بيانات المناوبات", enabled = !state.busy)
        NumberField(state.doctorsTopicId, viewModel::setDoctorsTopicId, "موضوع سجل الأطباء", enabled = !state.busy)

        if (state.mode == ProjectSetupMode.CREATE) {
            Text("المدير الأول", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.adminName,
                onValueChange = viewModel::setAdminName,
                label = { Text("الاسم الكامل") },
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            NumberField(
                state.adminTelegramId,
                viewModel::setAdminTelegramId,
                "معرف مستخدم تليجرام",
                enabled = !state.busy
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.adminGenderCode == "M",
                    onClick = { viewModel.setAdminGender("M") },
                    label = { Text("ذكر") },
                    enabled = !state.busy
                )
                FilterChip(
                    selected = state.adminGenderCode == "F",
                    onClick = { viewModel.setAdminGender("F") },
                    label = { Text("أنثى") },
                    enabled = !state.busy
                )
                FilterChip(
                    selected = state.adminClinicalRoleCode == "RESIDENT",
                    onClick = { viewModel.setAdminClinicalRole("RESIDENT") },
                    label = { Text("مقيم") },
                    enabled = !state.busy
                )
                FilterChip(
                    selected = state.adminClinicalRoleCode == "SUPERVISOR",
                    onClick = { viewModel.setAdminClinicalRole("SUPERVISOR") },
                    label = { Text("مشرف") },
                    enabled = !state.busy
                )
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
        state.status?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        Button(
            onClick = viewModel::initialize,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(if (state.mode == ProjectSetupMode.CREATE) "إنشاء وتهيئة المشروع" else "التحقق والانضمام")
        }
    }
}

@Composable
private fun InstructionCard(mode: ProjectSetupMode) {
    val text = if (mode == ProjectSetupMode.CREATE) {
        "١. أنشئ بوتاً عبر BotFather.\n٢. أنشئ مجموعة Supergroup وأضف البوت مديراً بصلاحيات إرسال الرسائل والملفات وتثبيت الرسائل.\n٣. أنشئ المواضيع المطلوبة إن رغبت، ثم أدخل المعرفات أدناه.\n٤. استخدم مجموعة جديدة لا تحتوي رسالة مشروع مثبّتة."
    } else {
        "اطلب من مدير المشروع اسم المستشفى ورمز البوت ومعرف المجموعة ومعرفات المواضيع نفسها. يجب أن تكون بيانات المشروع مثبّتة في المجموعة."
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, Modifier.padding(14.dp), textAlign = TextAlign.Start)
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    signed: Boolean = false,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (signed) KeyboardType.Text else KeyboardType.Number
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
