package com.hos.rushdpatients.ui.setup

import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.ui.components.PasswordField

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProjectSetupScreen(
    viewModel: ProjectSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTopics by rememberSaveable(state.mode) { mutableStateOf(false) }
    var showInstructions by rememberSaveable(state.mode) { mutableStateOf(state.mode != ProjectSetupMode.JOIN) }
    val openProvisioning = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importProvisioning) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiSpacing.screen, vertical = UiSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
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

            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
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

            AppTextButton(onClick = { showInstructions = !showInstructions }) {
                Text(if (showInstructions) "إخفاء خطوات الإعداد" else "خطوات الإعداد والمساعدة")
            }
            if (showInstructions) InstructionCard(state.mode)

            if (state.mode != ProjectSetupMode.DEMO) {
                if (state.mode == ProjectSetupMode.JOIN) {
                    SetupSection("١. ملف الانضمام من المدير") {
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
                        AppButton(
                            onClick = { openProvisioning.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                            enabled = !state.busy && state.provisioningPassphrase.length >= 10,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.importedProvisioning) "اختيار ملف انضمام آخر" else "اختيار ملف الانضمام") }
                    }
                    if (state.importedProvisioning) {
                        SetupSection("٢. مراجعة المشروع") {
                            Text(state.hospitalName, fontWeight = FontWeight.Bold)
                            Text("المجموعة: ${state.chatId}")
                            Text(if (state.encryptTelegram) "تشفير بيانات تليجرام: مفعّل" else "تشفير بيانات تليجرام: غير مفعّل")
                            Text("تم استيراد إعدادات الاتصال والمواضيع تلقائياً؛ لا يلزم إدخال معرّفات المواضيع.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (state.mode == ProjectSetupMode.CREATE) {
                    SetupSection("بيانات الاتصال بالمشروع") {
                        AppTextField(
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

                        AppTextButton(onClick = { showTopics = !showTopics }) {
                            Text(if (showTopics) "إخفاء إعدادات المواضيع" else "إعدادات المواضيع المتقدمة (اختياري)")
                        }
                        if (showTopics) {
                            Text(
                                "اختيارية. اترك الحقول فارغة للنشر في General.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            NumberField(state.reportsTopicId, viewModel::setReportsTopicId, "موضوع التقارير", enabled = !state.busy)
                            NumberField(state.announcementsTopicId, viewModel::setAnnouncementsTopicId, "موضوع الإعلانات", enabled = !state.busy)
                            NumberField(state.csvTopicId, viewModel::setCsvTopicId, "موضوع بيانات المناوبات", enabled = !state.busy)
                            NumberField(state.doctorsTopicId, viewModel::setDoctorsTopicId, "موضوع سجل الأطباء", enabled = !state.busy)
                        }
                    }
                }
            }

            if (state.mode == ProjectSetupMode.CREATE) {
                SetupSection("حماية بيانات تليجرام") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("تشفير البيانات بمفتاح عشوائي", modifier = Modifier.weight(1f))
                        Switch(checked = state.encryptTelegram, onCheckedChange = viewModel::setEncryptTelegram,
                            enabled = !state.busy)
                    }
                    Text("اختياري عند إنشاء المشروع. يُنشأ مفتاح عشوائي ويُنقل للأعضاء داخل ملف الانضمام المحمي. يفك التطبيق البيانات محلياً؛ التقارير النصية تُرسل كملفات مشفرة. استخدم PDF للقراءة في تليجرام.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("احتفظ بملف الانضمام وعبارة مروره؛ استعادة البيانات المشفرة تحتاج مفتاح المشروع.",
                        style = MaterialTheme.typography.bodySmall)
                }
                SetupSection("المدير الأول") {
                    AppTextField(
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
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

            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            state.error?.let {
                AppNotice(it, kind = NoticeKind.ERROR)
            }
            state.status?.let {
                AppNotice(it)
            }

            AppButton(
                onClick = viewModel::initialize,
                enabled = !state.busy && (state.mode != ProjectSetupMode.JOIN || state.importedProvisioning),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = UiSpacing.medium)
                            .size(UiSize.iconMedium),
                        strokeWidth = UiSize.progressStroke
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
}

@Composable
private fun InstructionCard(mode: ProjectSetupMode) {
    val text = when (mode) {
        ProjectSetupMode.DEMO ->
            "شاهد التطبيق فوراً بأسماء زهور وفواكه ومرضى وهميين. لا يحتاج رمز بوت، ولا يحفظ أو يزامن أي بيانات. يمكنك الخروج والعودة للتهيئة الحقيقية في أي وقت."
        ProjectSetupMode.JOIN ->
            "اطلب ملف الانضمام من المدير وعبارة مروره عبر قناة منفصلة. يستورد الملف بيانات الاتصال والمواضيع ومفتاح المشروع إن كان التشفير مفعلاً. راجع اسم المشروع ثم اضغط التحقق والانضمام."
        ProjectSetupMode.CREATE ->
            "١. أنشئ بوتاً عبر BotFather.\n٢. أنشئ مجموعة Supergroup وأضف البوت مديراً بصلاحيات إرسال الرسائل والملفات وتثبيت الرسائل.\n٣. أنشئ المواضيع المطلوبة إن رغبت، ثم أدخل المعرفات أدناه.\n٤. استخدم مجموعة جديدة لا تحتوي رسالة مشروع مثبّتة.\n٥. بعد التهيئة، صدّر ملف انضمام مشفراً للأعضاء من إعدادات المدير."
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, Modifier.padding(UiPadding.content), textAlign = TextAlign.Start)
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
    AppTextField(
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

@Composable
private fun SetupSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppSection(title = title, content = content)
}
