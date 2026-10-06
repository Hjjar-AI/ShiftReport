package com.hos.rushdpatients.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val openProvisioning = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importProvisioning) }

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
            "اختر طريقة البدء. الانضمام هو الخيار المعتاد للأجهزة الجديدة، ولا يتطلب إنشاء بوت جديد.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "١. تجربة: بيانات وهمية بلا إعدادات أو حفظ.\n٢. انضمام: لمستشفى مهيأ مسبقاً (الخيار الافتراضي).\n٣. إنشاء: للمسؤول الذي يجهز مشروع مستشفى جديداً فقط.",
                Modifier.padding(14.dp)
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == ProjectSetupMode.DEMO,
                onClick = { viewModel.setMode(ProjectSetupMode.DEMO) },
                label = { Text("تجربة") },
                enabled = !state.busy
            )
            FilterChip(
                selected = state.mode == ProjectSetupMode.JOIN,
                onClick = { viewModel.setMode(ProjectSetupMode.JOIN) },
                label = { Text("انضمام") },
                enabled = !state.busy
            )
            FilterChip(
                selected = state.mode == ProjectSetupMode.CREATE,
                onClick = { viewModel.setMode(ProjectSetupMode.CREATE) },
                label = { Text("إنشاء") },
                enabled = !state.busy
            )
        }

        InstructionCard(state.mode)

        if (state.mode != ProjectSetupMode.DEMO) {
            if (state.mode == ProjectSetupMode.JOIN) {
                Text("ملف الانضمام المشفر", style = MaterialTheme.typography.titleMedium)
                Text(
                    "اطلب الملف من المدير وعبارة مروره عبر قناة منفصلة. لا تُرسل العبارة مع الملف.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PasswordField(
                    value = state.provisioningPassphrase,
                    onValueChange = viewModel::setProvisioningPassphrase,
                    label = "عبارة مرور الملف (10 محارف على الأقل)",
                    enabled = !state.busy
                )
                Button(
                    onClick = { openProvisioning.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = !state.busy && state.provisioningPassphrase.length >= 10,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.importedProvisioning) "اختيار ملف انضمام آخر" else "اختيار ملف الانضمام") }
                Text(
                    "أو أدخل البيانات يدوياً:",
                    style = MaterialTheme.typography.labelLarge
                )
            }

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
        }

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
            Text(
                when (state.mode) {
                    ProjectSetupMode.DEMO -> "بدء التجربة"
                    ProjectSetupMode.JOIN -> "التحقق والانضمام"
                    ProjectSetupMode.CREATE -> "إنشاء وتهيئة المشروع"
                }
            )
        }
    }
}

@Composable
private fun InstructionCard(mode: ProjectSetupMode) {
    val text = when (mode) {
        ProjectSetupMode.DEMO ->
            "شاهد التطبيق فوراً بأسماء زهور وفواكه ومرضى وهميين. لا يحتاج رمز بوت، ولا يحفظ أو يزامن أي بيانات. يمكنك الخروج والعودة للتهيئة الحقيقية في أي وقت."
        ProjectSetupMode.JOIN ->
            "انضم بعد دخولك مجموعة تليجرام الخاصة بالمستشفى. استخدم ملف الانضمام المشفر من المدير لتعبئة الإعدادات، أو أدخلها يدوياً. يتحقق التطبيق من البوت والمجموعة ثم ينزل سجل المشروع المثبّت."
        ProjectSetupMode.CREATE ->
            "١. أنشئ بوتاً عبر BotFather.\n٢. أنشئ مجموعة Supergroup وأضف البوت مديراً بصلاحيات إرسال الرسائل والملفات وتثبيت الرسائل.\n٣. أنشئ المواضيع المطلوبة إن رغبت، ثم أدخل المعرفات أدناه.\n٤. استخدم مجموعة جديدة لا تحتوي رسالة مشروع مثبّتة.\n٥. بعد التهيئة، صدّر ملف انضمام مشفراً للأعضاء من إعدادات المدير."
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
