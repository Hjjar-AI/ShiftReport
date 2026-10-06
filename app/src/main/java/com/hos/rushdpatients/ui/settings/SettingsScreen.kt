package com.hos.rushdpatients.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.time.LocalDate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.sync.CsvSchema

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onOpenVbaImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var backupDialogMode by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingExportPassword by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestorePassword by remember { mutableStateOf("") }
    var confirmBackupRestore by remember { mutableStateOf(false) }
    var confirmForceUpload by remember { mutableStateOf(false) }
    var forceConfirmationText by remember { mutableStateOf("") }
    var provisioningDialog by remember { mutableStateOf(false) }
    var provisioningPassphrase by remember { mutableStateOf("") }
    var pendingProvisioningPassphrase by remember { mutableStateOf("") }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingExportPassword.isNotEmpty()) {
            viewModel.exportEncryptedBackup(uri, pendingExportPassword)
        }
        pendingExportPassword = ""
    }
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            backupPassword = ""
            backupDialogMode = "restore"
        }
    }
    val createProvisioning = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingProvisioningPassphrase.isNotEmpty()) {
            viewModel.exportProjectProvisioning(uri, pendingProvisioningPassphrase)
        }
        pendingProvisioningPassphrase = ""
    }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            val result = snackbar.showSnackbar(
                message = it,
                actionLabel = if (state.retryAction != null) "إعادة المحاولة" else null
            )
            viewModel.dismissSnackbar()
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.retryLastAction()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = state.doctorName.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "الدور: ${state.role.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SettingsSection(title = "واجهة التطبيق") {
                SettingsSubheading("الألوان والخط")
                Text("ألوان التطبيق", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppThemePreset.entries.forEach { option ->
                        FilterChip(
                            selected = state.appTheme == option,
                            onClick = { viewModel.setAppTheme(option) },
                            label = { Text(option.arabicLabel) }
                        )
                    }
                }

                Text("حجم الخط", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppFontScale.entries.forEach { option ->
                        FilterChip(
                            selected = state.fontScale == option,
                            onClick = { viewModel.setFontScale(option) },
                            label = { Text(option.arabicLabel) }
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSubheading("بطاقات المرضى")
                Text(
                    "تستخدم شاشة المناوبة بطاقة سريرية موحّدة حتى تبقى التحذيرات والأولوية واضحة بنفس المعنى للجميع.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToggleRow(
                    title = "عرض التفاصيل افتراضياً",
                    subtitle = "يمكن تغييره من شريط شاشة المرضى",
                    checked = state.patientDetailsExpanded,
                    onCheckedChange = viewModel::setPatientDetailsExpanded
                )
                ToggleRow(
                    title = "بطاقة بعمودين",
                    subtitle = "تعمل عند توفر عرض كافٍ وتعود لعمود واحد على الشاشة الضيقة",
                    checked = state.patientTwoColumn,
                    onCheckedChange = viewModel::setPatientTwoColumn
                )
                ToggleRow(
                    title = "عرض مضغوط",
                    subtitle = "مسافات أصغر لعرض عدد أكبر من المرضى دون تصغير الخط",
                    checked = state.patientCompactDensity,
                    onCheckedChange = viewModel::setPatientCompactDensity
                )
                ToggleRow(
                    title = "الترحيل الموجّه للمناوبة",
                    subtitle = "عند بدء مناوبة فارغة، اختر المرضى المستمرين من المناوبة السابقة",
                    checked = state.guidedRollover,
                    onCheckedChange = viewModel::setGuidedRollover
                )
            }

            SettingsSection(title = "التقرير") {
                SettingsSubheading("الإرسال والتخطيط")
                ToggleRow(
                    title = "إرسال التقرير كملف PDF",
                    subtitle = "خيار افتراضي عند معاينة التقرير",
                    checked = state.reportAsPdf,
                    onCheckedChange = viewModel::setReportAsPdf
                )

                Text("نمط PDF", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfStyle.entries.forEach { option ->
                        FilterChip(
                            selected = state.pdfStyle == option,
                            onClick = { viewModel.setPdfStyle(option) },
                            label = { Text(option.arabicLabel) }
                        )
                    }
                }

                if (state.pdfStyle == PdfStyle.CARDS) {
                    Text("تصميم بطاقات PDF", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PatientCardStyle.entries.forEach { option ->
                            FilterChip(
                                selected = state.pdfPatientCardStyle == option,
                                onClick = { viewModel.setPdfPatientCardStyle(option) },
                                label = { Text("${option.shortLabel} · ${option.arabicLabel}") }
                            )
                        }
                    }
                }

                Text("اتجاه الصفحة", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfOrientation.entries.forEach { option ->
                        FilterChip(
                            selected = state.pdfOrientation == option,
                            onClick = { viewModel.setPdfOrientation(option) },
                            label = { Text(option.arabicLabel) }
                        )
                    }
                }

                Text("حجم الورق", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfPaperSize.entries.forEach { option ->
                        FilterChip(
                            selected = state.pdfPaperSize == option,
                            onClick = { viewModel.setPdfPaperSize(option) },
                            label = { Text(option.arabicLabel) }
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSubheading("ألوان وتوزيع الملف")
                Text("ألوان العنوان والجدول", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfColorPreset.entries.forEach { option ->
                        FilterChip(
                            selected = state.pdfColorPreset == option,
                            onClick = { viewModel.setPdfColorPreset(option) },
                            label = { Text(option.arabicLabel) }
                        )
                    }
                }

                ToggleRow(
                    title = "PDF داكن",
                    subtitle = "خلفية داكنة ونص فاتح",
                    checked = state.pdfDarkMode,
                    onCheckedChange = viewModel::setPdfDarkMode
                )
                ToggleRow(
                    title = "ملف PDF لكل مشرف",
                    subtitle = "عند الحفظ المحلي",
                    checked = state.pdfSeparateBySupervisor,
                    onCheckedChange = viewModel::setPdfSeparateBySupervisor
                )
            }

            SettingsSection(title = "البيانات والمزامنة") {
                ToggleRow(
                    title = "المزامنة التلقائية",
                    subtitle = "كل 15 دقيقة في الخلفية",
                    checked = state.autoSync,
                    onCheckedChange = viewModel::setAutoSync
                )
                ToggleRow(
                    title = "عبر Wi-Fi فقط",
                    subtitle = "تجنب بيانات الجوال",
                    checked = state.syncWifiOnly,
                    onCheckedChange = if (state.autoSync) viewModel::setSyncWifiOnly else null
                )

                state.lastCsvSync?.let {
                    Text(
                        "آخر مزامنة CSV: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.lastDoctorsSync?.let {
                    Text(
                        "آخر مزامنة أطباء: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.lastOperation?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LoadingButton(
                    text = "جلب أحدث البيانات من تلجرام",
                    loading = state.syncing,
                    onClick = viewModel::bringLatest,
                    modifier = Modifier.fillMaxWidth()
                )
                LoadingButton(
                    text = "رفع بيانات الوردية الحالية",
                    loading = state.syncing,
                    onClick = viewModel::uploadCurrent,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        forceConfirmationText = ""
                        confirmForceUpload = true
                    },
                    enabled = !state.syncing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("فرض النسخة الحالية") }
                Text(
                    "يتجاوز الدمج ويجعل بيانات هذا الجهاز هي النسخة المنشورة. استخدمه فقط للاستعادة المقصودة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                LoadingButton(
                    text = "حفظ بيانات المرضى CSV",
                    loading = state.exportingCsv,
                    onClick = viewModel::exportCsv,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.syncing
                )
            }

            SettingsSection(title = "الأمان والخصوصية") {
                ToggleRow(
                    title = "التحقق بالبصمة",
                    subtitle = if (state.biometricAvailable) "استخدم بصمتك لفتح التطبيق"
                    else "غير متوفرة على هذا الجهاز",
                    checked = state.biometricEnabled,
                    onCheckedChange = if (state.biometricAvailable) viewModel::setBiometric else null
                )

                Text("القفل التلقائي", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 5, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = state.autoLockMinutes == minutes,
                            onClick = { viewModel.setAutoLockMinutes(minutes) },
                            label = { Text("$minutes د") }
                        )
                    }
                }
            }

            SettingsSection(title = "النسخ الاحتياطي والاستعادة") {
                Text(
                    "ملف محمي بكلمة مرور ومستقل عن تلجرام. احتفظ بكلمة المرور في مكان آمن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LoadingButton(
                    text = "إنشاء نسخة مشفرة",
                    loading = state.backupBusy,
                    onClick = {
                        backupPassword = ""
                        backupDialogMode = "export"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { openBackup.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = !state.backupBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("استعادة نسخة مشفرة") }
            }

            SettingsSection(title = "حالة التخزين") {
                Text("حجم قاعدة البيانات: ${state.databaseSize}")
                Text("إصدار تنسيق CSV: v${CsvSchema.CURRENT_VERSION}")
                Text("المناوبات المحفوظة: ${state.storedShifts}")
                Text("سجلات المرضى: ${state.totalStoredPatients}")
                Text("في سلة المحذوفات: ${state.deletedPatients}")
                Text("معرّف الجهاز: ${state.deviceId}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "آخر نسخة مشفرة: ${state.lastEncryptedBackup ?: "لم تُنشأ نسخة بعد"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isAdmin) {
                SettingsSection(title = "إدارة التطبيق") {
                    Text("ضم أجهزة جديدة", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "أنشئ ملفاً مشفراً يعبئ إعدادات المستشفى والبوت على جهاز العضو. أرسل عبارة المرور إليه عبر قناة منفصلة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LoadingButton(
                        text = "تصدير ملف انضمام مشفر",
                        loading = state.provisioningBusy,
                        onClick = {
                            provisioningPassphrase = ""
                            provisioningDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("مجموعات المشرفين", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "معرّفات مجموعات تليجرام لاستقبال تقارير كل مشرف.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.supervisors.isEmpty()) {
                        Text(
                            "لا يوجد مشرفون",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        state.supervisors.forEach { supervisor ->
                            SupervisorGroupEditor(
                                supervisor = supervisor,
                                saving = state.savingSupervisorGroupId == supervisor.id,
                                enabled = state.savingSupervisorGroupId == null,
                                onSave = { value ->
                                    viewModel.setSupervisorGroupChatId(supervisor.id, value)
                                }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenVbaImport,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("استيراد بيانات من الإكسل") }
                }
            }

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth()
            ) { Text("تسجيل الخروج") }

            Spacer(Modifier.height(8.dp))
        }
    }


    backupDialogMode?.let { mode ->
        AlertDialog(
            onDismissRequest = {
                backupDialogMode = null
                backupPassword = ""
                if (mode == "restore") pendingImportUri = null
            },
            title = {
                Text(if (mode == "export") "حماية النسخة الاحتياطية" else "فتح النسخة الاحتياطية")
            },
            text = {
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text("كلمة المرور (8 محارف على الأقل)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = backupPassword.length >= 8,
                    onClick = {
                        if (mode == "export") {
                            pendingExportPassword = backupPassword
                            createBackup.launch("Rushd_Backup_${LocalDate.now()}.rpb")
                        } else {
                            pendingRestorePassword = backupPassword
                            confirmBackupRestore = true
                        }
                        backupPassword = ""
                        backupDialogMode = null
                    }
                ) { Text(if (mode == "export") "إنشاء" else "استعادة") }
            },
            dismissButton = {
                TextButton(onClick = {
                    backupDialogMode = null
                    backupPassword = ""
                    if (mode == "restore") pendingImportUri = null
                }) { Text("إلغاء") }
            }
        )
    }

    if (provisioningDialog) {
        AlertDialog(
            onDismissRequest = {
                provisioningDialog = false
                provisioningPassphrase = ""
            },
            title = { Text("حماية ملف الانضمام") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر عبارة قوية من 10 محارف على الأقل. لا تُحفظ العبارة داخل الملف.")
                    OutlinedTextField(
                        value = provisioningPassphrase,
                        onValueChange = { provisioningPassphrase = it },
                        label = { Text("عبارة المرور") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = provisioningPassphrase.length >= 10,
                    onClick = {
                        pendingProvisioningPassphrase = provisioningPassphrase
                        provisioningPassphrase = ""
                        provisioningDialog = false
                        createProvisioning.launch("ShiftReport_Join_${LocalDate.now()}.srjoin")
                    }
                ) { Text("إنشاء") }
            },
            dismissButton = {
                TextButton(onClick = {
                    provisioningDialog = false
                    provisioningPassphrase = ""
                }) { Text("إلغاء") }
            }
        )
    }

    if (confirmForceUpload) {
        AlertDialog(
            onDismissRequest = {
                confirmForceUpload = false
                forceConfirmationText = ""
            },
            title = { Text("فرض النسخة المحلية؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم حفظ مرجع النسخة المستبدلة ثم تجاوز الدمج. اكتب «فرض النسخة الحالية» للمتابعة.")
                    OutlinedTextField(
                        value = forceConfirmationText,
                        onValueChange = { forceConfirmationText = it },
                        label = { Text("عبارة التأكيد") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForceUpload = false
                        forceConfirmationText = ""
                        viewModel.forceUploadCurrent()
                    },
                    enabled = forceConfirmationText.trim() == "فرض النسخة الحالية"
                ) { Text("فرض النسخة") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmForceUpload = false
                    forceConfirmationText = ""
                }) { Text("إلغاء") }
            }
        )
    }

    if (confirmBackupRestore) {
        AlertDialog(
            onDismissRequest = {
                confirmBackupRestore = false
                pendingRestorePassword = ""
                pendingImportUri = null
            },
            title = { Text("تأكيد استعادة النسخة") },
            text = {
                Text("ستستبدل النسخة الاحتياطية البيانات المحلية الحالية. لا يمكن التراجع عن هذه العملية من داخل التطبيق.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri
                    val password = pendingRestorePassword
                    confirmBackupRestore = false
                    pendingImportUri = null
                    pendingRestorePassword = ""
                    if (uri != null) viewModel.restoreEncryptedBackup(uri, password)
                }) { Text("استعادة واستبدال") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmBackupRestore = false
                    pendingRestorePassword = ""
                    pendingImportUri = null
                }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun SettingsSubheading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SupervisorGroupEditor(
    supervisor: Doctor,
    saving: Boolean,
    enabled: Boolean,
    onSave: (String) -> Unit
) {
    var chatId by remember(supervisor.id, supervisor.supervisorGroupChatId) {
        mutableStateOf(supervisor.supervisorGroupChatId?.toString().orEmpty())
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(supervisor.fullName, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = chatId,
            onValueChange = { value ->
                chatId = value.filter { it.isDigit() || it == '-' }
            },
            label = { Text("معرف مجموعة تليجرام") },
            supportingText = { Text("-1001234567890") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        LoadingButton(
            text = if (chatId.isBlank()) "إزالة المعرف" else "حفظ",
            loading = saving,
            onClick = { onSave(chatId) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = onCheckedChange != null
        )
    }
}
