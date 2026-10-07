package com.hos.rushdpatients.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.ClipData
import android.content.Intent
import java.time.LocalDate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.config.ProjectProvisioningManager
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.connection.ProjectConnectionAction
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.sync.CsvSchema
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onOpenVbaImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val settingsScroll = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<String, Int>() }
    val sectionTitles = remember(isAdmin) {
        listOf("التقرير", "واجهة التطبيق", "البيانات والمزامنة", "الأمان والخصوصية", "النسخ الاحتياطي والاستعادة") +
            (if (isAdmin) listOf("إدارة التطبيق") else emptyList()) + listOf("حالة التخزين")
    }
    val sectionRequests = remember(sectionTitles) {
        sectionTitles.associateWith { BringIntoViewRequester() }
    }
    val sectionThreshold = with(LocalDensity.current) { UiSpacing.medium.roundToPx() }
    val activeSection by remember(sectionTitles, sectionThreshold) {
        derivedStateOf {
            if (settingsScroll.maxValue > 0 && settingsScroll.value >= settingsScroll.maxValue) {
                sectionTitles.last()
            } else {
                sectionTitles.lastOrNull { title ->
                    sectionOffsets[title]?.let { it <= settingsScroll.value + sectionThreshold } == true
                } ?: sectionTitles.first()
            }
        }
    }
    LaunchedEffect(activeSection) {
        sectionRequests.getValue(activeSection).bringIntoView()
    }
    val settingsScope = rememberCoroutineScope()
    var backupDialogMode by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestorePassword by remember { mutableStateOf("") }
    var confirmBackupRestore by remember { mutableStateOf(false) }
    var confirmForceUpload by remember { mutableStateOf(false) }
    var forceConfirmationText by remember { mutableStateOf("") }
    var advancedPdf by rememberSaveable { mutableStateOf(false) }
    var provisioningDialog by remember { mutableStateOf(false) }
    var provisioningPassphrase by remember { mutableStateOf("") }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> viewModel.exportEncryptedBackup(uri) }
    LaunchedEffect(state.backupReady) {
        if (state.backupReady) {
            viewModel.backupPickerLaunched()
            try {
                createBackup.launch("Rushd_Backup_${LocalDate.now()}.rpb")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.backupPickerFailed()
            }
        }
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
        ActivityResultContracts.CreateDocument(ProjectProvisioningManager.EXPORT_MIME_TYPE)
    ) { uri ->
        viewModel.exportProjectProvisioning(uri)
    }

    LaunchedEffect(state.provisioningReady) {
        if (state.provisioningReady) {
            viewModel.provisioningPickerLaunched()
            try {
                createProvisioning.launch("ShiftReport_Join_${LocalDate.now()}.${ProjectProvisioningManager.EXPORT_EXTENSION}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.provisioningPickerFailed()
            }
        }
    }

    LaunchedEffect(state.provisioningShareUri) {
        val uri = state.provisioningShareUri ?: return@LaunchedEffect
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = ProjectProvisioningManager.EXPORT_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "ملف الانضمام", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة ملف الانضمام المشفر"))
            viewModel.provisioningShareHandled(failed = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            viewModel.provisioningShareHandled(failed = true)
        }
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع",
                            modifier = Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 180f else 0f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sectionTitles.forEach { title ->
                    FilterChip(
                        selected = title == activeSection,
                        onClick = {
                            sectionOffsets[title]?.let { offset ->
                                settingsScope.launch { settingsScroll.animateScrollTo(offset) }
                            }
                        },
                        modifier = Modifier.heightIn(min = UiSpacing.touchTarget)
                            .bringIntoViewRequester(sectionRequests.getValue(title)),
                        label = { Text(title, maxLines = 1) }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small)
                    .verticalScroll(settingsScroll),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
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
                            .padding(UiSpacing.medium),
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

                Text(
                    "اختر قسماً من الشريط للوصول مباشرة إلى إعداداته. تظهر أدوات الإدارة للمدير فقط.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                SettingsSection(title = "التقرير", onPositioned = { sectionOffsets["التقرير"] = it }) {
                    SettingsSubheading("الإرسال والتخطيط")
                    ToggleRow(
                        title = "إرسال التقرير كملف PDF",
                        subtitle = "خيار افتراضي عند معاينة التقرير",
                        checked = state.reportAsPdf,
                        onCheckedChange = viewModel::setReportAsPdf
                    )

                    Text("نمط PDF", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                        PdfStyle.entries.forEach { option ->
                            FilterChip(
                                selected = state.pdfStyle == option,
                                onClick = { viewModel.setPdfStyle(option) },
                                label = { Text(option.arabicLabel) }
                            )
                        }
                    }

                    if (state.pdfStyle == PdfStyle.CARDS) {
                        Text(
                            "الصفوف الأنيقة تستخدم خلفية بيضاء وألواناً محدودة لتقليل حجم الملف وتحسين الطباعة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { advancedPdf = !advancedPdf },
                        modifier = Modifier.heightIn(min = UiSpacing.touchTarget).semantics {
                            stateDescription = if (advancedPdf) "موسع" else "مطوي"
                        }) { Text(if (advancedPdf) "إخفاء خيارات PDF المتقدمة" else "خيارات PDF المتقدمة") }
                    if (advancedPdf) {
                        Text("اتجاه الصفحة", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                            PdfOrientation.entries.forEach { option ->
                                FilterChip(
                                    selected = state.pdfOrientation == option,
                                    onClick = { viewModel.setPdfOrientation(option) },
                                    label = { Text(option.arabicLabel) }
                                )
                            }
                        }

                        Text("حجم الورق", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                            PdfColorPreset.entries.forEach { option ->
                                FilterChip(
                                    selected = state.pdfColorPreset == option,
                                    onClick = { viewModel.setPdfColorPreset(option) },
                                    label = { Text(option.arabicLabel) }
                                )
                            }
                        }

                        if (state.pdfStyle == PdfStyle.CLASSIC) {
                            ToggleRow(
                                title = "PDF داكن",
                                subtitle = "خلفية داكنة ونص فاتح",
                                checked = state.pdfDarkMode,
                                onCheckedChange = viewModel::setPdfDarkMode
                            )
                        }
                        ToggleRow(
                            title = "ملف PDF لكل مشرف",
                            subtitle = "عند الحفظ المحلي",
                            checked = state.pdfSeparateBySupervisor,
                            onCheckedChange = viewModel::setPdfSeparateBySupervisor
                        )
                    }
                }

                SettingsSection(title = "واجهة التطبيق", onPositioned = { sectionOffsets["واجهة التطبيق"] = it }) {
                    SettingsSubheading("الألوان والخط")
                    Text("المظهر", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                        AppAppearance.entries.forEach { option ->
                            FilterChip(selected = state.appearance == option,
                                onClick = { viewModel.setAppearance(option) },
                                label = { Text(option.arabicLabel) })
                        }
                    }
                    Text("اللون الأساسي", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                        AppThemePreset.entries.forEach { option ->
                            FilterChip(
                                selected = state.appTheme == option,
                                onClick = { viewModel.setAppTheme(option) },
                                label = { Text(option.arabicLabel) }
                            )
                        }
                    }
                    Text("حجم الخط", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
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
                        "تستخدم شاشة المناوبة بطاقة سريرية موحّدة حتى تبقى الشارات والأولوية واضحة بنفس المعنى للجميع.",
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

                SettingsSection(title = "البيانات والمزامنة", onPositioned = { sectionOffsets["البيانات والمزامنة"] = it }) {
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
                    LoadingButton(
                        text = "حفظ بيانات المرضى CSV",
                        loading = state.exportingCsv,
                        onClick = viewModel::exportCsv,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.syncing
                    )
                }

                SettingsSection(title = "الأمان والخصوصية", onPositioned = { sectionOffsets["الأمان والخصوصية"] = it }) {
                    ProjectConnectionAction(isAdmin = isAdmin)
                    ToggleRow(
                        title = "التحقق بالبصمة",
                        subtitle = if (state.biometricAvailable) "استخدم بصمتك لفتح التطبيق"
                        else "غير متوفرة على هذا الجهاز",
                        checked = state.biometricEnabled,
                        onCheckedChange = if (state.biometricAvailable) viewModel::setBiometric else null
                    )

                    Text("القفل التلقائي", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                        listOf(1, 5, 15, 30).forEach { minutes ->
                            FilterChip(
                                selected = state.autoLockMinutes == minutes,
                                onClick = { viewModel.setAutoLockMinutes(minutes) },
                                label = { Text("$minutes د") }
                            )
                        }
                    }
                }

                SettingsSection(title = "النسخ الاحتياطي والاستعادة", onPositioned = { sectionOffsets["النسخ الاحتياطي والاستعادة"] = it }) {
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
                    Divider()
                    SettingsSubheading("استعادة بيانات تليجرام")
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
                }

                SettingsSection(title = "حالة التخزين", onPositioned = { sectionOffsets["حالة التخزين"] = it }) {
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
                    SettingsSection(title = "إدارة التطبيق", onPositioned = { sectionOffsets["إدارة التطبيق"] = it }) {
                        Text("ضم أجهزة جديدة", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "أنشئ ملفاً مشفراً يعبئ إعدادات المستشفى والبوت على جهاز العضو. أرسل عبارة المرور إليه عبر قناة منفصلة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LoadingButton(
                            text = "إنشاء ملف انضمام مشفر",
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
                                androidx.compose.runtime.key(supervisor.id) {
                                    SupervisorGroupEditor(
                                        supervisor = supervisor,
                                        saving = state.savingSupervisorGroupId == supervisor.id,
                                        enabled = state.savingSupervisorGroupId == null,
                                        onSave = { expected, value ->
                                            viewModel.setSupervisorGroupChatId(expected, value)
                                        }
                                    )
                                }
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
                            viewModel.prepareEncryptedBackup(backupPassword)
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
                    Text("سيُحفظ ملف .srjoin.json مشفر. اختر عبارة قوية من 10 محارف على الأقل؛ لا تُحفظ العبارة داخل الملف.")
                    OutlinedTextField(
                        value = provisioningPassphrase,
                        onValueChange = { provisioningPassphrase = it },
                        label = { Text("عبارة المرور") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Text("يمكن مشاركة الملف الجاهز مباشرة أو اختيار مكان لحفظه.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        enabled = provisioningPassphrase.length >= 10 && !state.provisioningBusy,
                        onClick = {
                            viewModel.prepareProjectProvisioning(provisioningPassphrase)
                            provisioningPassphrase = ""
                            provisioningDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("اختيار مكان الحفظ") }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = provisioningPassphrase.length >= 10 && !state.provisioningBusy,
                    onClick = {
                        viewModel.prepareProjectProvisioning(provisioningPassphrase, share = true)
                        provisioningPassphrase = ""
                        provisioningDialog = false
                    }
                ) { Text("مشاركة الملف") }
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
    onPositioned: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { onPositioned(it.positionInParent().y.roundToInt()) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = UiSpacing.tiny).semantics { heading() }
            )
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
    onSave: (Doctor, String) -> Unit
) {
    var baseline by remember(supervisor.id) { mutableStateOf(supervisor) }
    var chatId by remember(supervisor.id) {
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
        if (supervisor != baseline) {
            Text("تغيّر سجل المشرف؛ راجع النسخة المحفوظة قبل الحفظ", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                baseline = supervisor
                chatId = supervisor.supervisorGroupChatId?.toString().orEmpty()
            }, enabled = enabled) { Text("تحميل النسخة المحفوظة") }
        }
        LoadingButton(
            text = if (chatId.isBlank()) "إزالة المعرف" else "حفظ",
            loading = saving,
            onClick = { onSave(baseline, chatId) },
            enabled = enabled && supervisor == baseline,
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
