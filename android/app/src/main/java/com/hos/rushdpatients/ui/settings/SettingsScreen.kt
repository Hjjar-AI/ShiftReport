package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.AppPdfStylePicker
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.AppSectionIndex
import com.hos.rushdpatients.ui.components.activeAppSection
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.PasswordField
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.hos.rushdpatients.ui.components.AppPdfAppearanceControls
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.connection.ProjectConnectionAction
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.sync.CsvSchema
import kotlinx.coroutines.CancellationException

enum class SettingsWorkspace(val title: String) {
    PREFERENCES("الإعدادات"),
    DATA("إدارة البيانات"),
    PROJECT("إدارة المشروع"),
    DIAGNOSTICS("التشخيص ومعلومات الجهاز")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onOpenVbaImport: () -> Unit,
    workspace: SettingsWorkspace = SettingsWorkspace.PREFERENCES,
    onOpenDataManagement: () -> Unit = {},
    onOpenProject: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canManageProject = isAdmin && state.isAdmin
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val settingsScroll = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<String, Int>() }
    val sections = remember(canManageProject, workspace) {
        SettingsSectionId.entries.filter { it.workspace == workspace && (canManageProject || !it.adminOnly) }
    }
    val sectionTitles = sections.map { it.title }
    val activeSection = activeAppSection(sectionTitles, sectionOffsets, settingsScroll)
    val settingsScope = rememberCoroutineScope()
    var openSection by rememberSaveable(workspace) {
        mutableStateOf<String?>(if (workspace == SettingsWorkspace.DATA) SettingsSectionId.SYNC.title else null)
    }
    var confirmUndoDownload by remember { mutableStateOf(false) }
    if (confirmUndoDownload) com.hos.rushdpatients.ui.components.ConfirmDialog(
        title = "التراجع عن آخر تنزيل",
        message = "استعادة البيانات المحلية قبل آخر تنزيل؟ لن يتغير تليجرام أو سجل التسليم. يُرفض التراجع إذا تغيرت البيانات بعد التنزيل؛ راجع المستعاد قبل النشر.",
        confirmText = "استعادة ما قبل التنزيل",
        onConfirm = { confirmUndoDownload = false; viewModel.undoLastDownload() },
        onDismiss = { confirmUndoDownload = false })

    var backupDialogMode by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestorePassword by remember { mutableStateOf("") }
    var confirmBackupRestore by remember { mutableStateOf(false) }
    var advancedPdf by rememberSaveable { mutableStateOf(false) }
    var provisioningDialog by remember(canManageProject) { mutableStateOf(false) }
    var provisioningPassphrase by remember(canManageProject) { mutableStateOf("") }

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

    LaunchedEffect(state.provisioningReady, canManageProject) {
        if (canManageProject && state.provisioningReady) {
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

    LaunchedEffect(state.provisioningShareUri, canManageProject) {
        if (!canManageProject) return@LaunchedEffect
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
            AppTopBar(title = workspace.title, onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (sections.size > 1) AppSectionIndex(sections = sectionTitles, selected = activeSection, label = { it },
                onSelect = { title ->
                    openSection = title
                    settingsScope.launch {
                        androidx.compose.runtime.withFrameNanos { }
                        androidx.compose.runtime.withFrameNanos { }
                        sectionOffsets[title]?.let { offset ->
                            settingsScroll.animateScrollTo(offset.coerceIn(0, settingsScroll.maxValue))
                        }
                    }
                }, modifier = Modifier.padding(horizontal = UiSpacing.medium))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small)
                    .verticalScroll(settingsScroll),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                if (workspace == SettingsWorkspace.PREFERENCES) AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(UiPadding.content),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.micro)
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
                    when (workspace) {
                        SettingsWorkspace.PREFERENCES -> "تفضيلات هذا الجهاز والعمل اليومي. افتح القسم المطلوب لتعديله."
                        SettingsWorkspace.DATA -> "المزامنة والملفات والنسخ الاحتياطي في مكان واحد. النشر والاستعادة يحتاجان إلى مراجعة وتأكيد."
                        SettingsWorkspace.PROJECT -> "اتصال المشروع وضم الأجهزة ووجهات تقارير المشرفين. أدوات الإدارة للمدير فقط."
                        SettingsWorkspace.DIAGNOSTICS -> "معلومات التخزين والجهاز للمساعدة في التشخيص. افتح القسم لعرض التفاصيل."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = UiSpacing.tiny)
                )

                sections.forEachIndexed { index, section ->
                    if (workspace == SettingsWorkspace.PREFERENCES && (index == 0 || sections[index - 1].group != section.group)) {
                        Text(
                            text = section.group.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = UiSpacing.small)
                                .semantics { heading() }
                        )
                    }
                    SettingsSection(
                        title = section.title,
                        summary = section.summary(state),
                        expanded = openSection == section.title,
                        onToggle = { openSection = if (openSection == section.title) null else section.title },
                        onPositioned = { sectionOffsets[section.title] = it }
                    ) {
                        when (section) {
                            SettingsSectionId.APPEARANCE -> SettingsAppearanceSection(
                                state = state,
                                onAppearance = viewModel::setAppearance,
                                onTheme = viewModel::setAppTheme,
                                onFont = viewModel::setFontScale
                            )
                            SettingsSectionId.PATIENT_CARDS -> {
                                SettingsSubheading("عرض قائمة المرضى")
                                Text(
                                    "تستخدم شاشة المناوبة بطاقة سريرية موحّدة حتى تبقى الشارات والأولوية واضحة بنفس المعنى للجميع.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AppToggleRow(
                                    title = "عرض التفاصيل افتراضياً",
                                    subtitle = "يمكن تغييره من شريط شاشة المرضى",
                                    checked = state.patientDetailsExpanded,
                                    onCheckedChange = viewModel::setPatientDetailsExpanded
                                )
                                AppToggleRow(
                                    title = "بطاقة بعمودين",
                                    subtitle = "تعمل عند توفر عرض كافٍ وتعود لعمود واحد على الشاشة الضيقة",
                                    checked = state.patientTwoColumn,
                                    onCheckedChange = viewModel::setPatientTwoColumn
                                )
                                AppToggleRow(
                                    title = "عرض مضغوط",
                                    subtitle = "مسافات أصغر لعرض عدد أكبر من المرضى دون تصغير الخط",
                                    checked = state.patientCompactDensity,
                                    onCheckedChange = viewModel::setPatientCompactDensity
                                )
                                SettingsPatientCardPreview(state)
                            }
                            SettingsSectionId.WORKFLOW -> {
                                SettingsSubheading("أثناء الجولة")
                                AppToggleRow(
                                    title = "حفظ والتالي أثناء الجولة",
                                    subtitle = "يُظهر زرّاً اختيارياً يحفظ المريض ويفتح التالي حسب التصفية والترتيب الحالي؛ متوقف افتراضياً",
                                    checked = state.saveAndNext,
                                    onCheckedChange = viewModel::setSaveAndNext
                                )
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingsSubheading("عند بدء مناوبة جديدة")
                                AppToggleRow(
                                    title = "اقتراح نسخ المناوبة السابقة كاملة",
                                    subtitle = "عند بدء مناوبة فارغة، اعرض تأكيد نسخ جميع المرضى والمهام غير المكتملة؛ مستقل عن جلب تليجرام",
                                    checked = state.guidedRollover,
                                    onCheckedChange = viewModel::setGuidedRollover
                                )
                            }
                            SettingsSectionId.REPORT -> {
                                SettingsSubheading("الإرسال والتخطيط")
                                AppToggleRow(
                                    title = "إرسال التقرير كملف PDF",
                                    subtitle = "خيار افتراضي عند معاينة التقرير",
                                    checked = state.reportAsPdf,
                                    onCheckedChange = viewModel::setReportAsPdf
                                )

                                AppPdfStylePicker(selected = state.pdfStyle, onSelected = viewModel::setPdfStyle)
                                AppTextButton(onClick = { advancedPdf = !advancedPdf },
                                    modifier = Modifier.heightIn(min = UiSpacing.touchTarget).semantics {
                                        stateDescription = if (advancedPdf) "موسع" else "مطوي"
                                    }) { Text(if (advancedPdf) "إخفاء خيارات PDF المتقدمة" else "خيارات PDF المتقدمة") }
                                if (advancedPdf) {
                                    if (state.pdfStyle != PdfStyle.CARDS) {
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
                                    }

                                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsSubheading("ألوان وتوزيع الملف")
                                    Text("ألوان ملف PDF", style = MaterialTheme.typography.labelMedium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                        PdfColorPreset.entries.forEach { option ->
                                            FilterChip(
                                                selected = state.pdfColorPreset == option,
                                                onClick = { viewModel.setPdfColorPreset(option) },
                                                label = { Text(option.arabicLabel) }
                                            )
                                        }
                                    }

                                    AppPdfAppearanceControls(
                                        weight = state.pdfRuleWeight, contrast = state.pdfRuleContrast,
                                        shading = state.pdfRowShading, dark = state.pdfDarkMode,
                                        onWeight = viewModel::setPdfRuleWeight,
                                        onContrast = viewModel::setPdfRuleContrast,
                                        onShading = viewModel::setPdfRowShading,
                                        onPreset = viewModel::setPdfPresentationPreset,
                                        showShading = state.pdfStyle != PdfStyle.CARDS
                                    )
                                    AppToggleRow(
                                        title = "PDF داكن",
                                        subtitle = "خلفية داكنة ونص فاتح",
                                        checked = state.pdfDarkMode,
                                        onCheckedChange = viewModel::setPdfDarkMode
                                    )
                                    AppToggleRow(
                                        title = "ملف PDF لكل مشرف",
                                        subtitle = "عند الحفظ المحلي",
                                        checked = state.pdfSeparateBySupervisor,
                                        onCheckedChange = viewModel::setPdfSeparateBySupervisor
                                    )
                                }
                            }
                            SettingsSectionId.SYNC -> {
                                SettingsSubheading("المزامنة مع تليجرام")
                                Text("المزامنة يدوية: اجلب النسخة الكاملة أو انشر تعديلاتك من الإجراءات أدناه.")

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
                                state.lastOperation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                                LoadingButton(
                                    text = "جلب أحدث البيانات من تليجرام",
                                    outlined = true,
                                    loading = state.syncing,
                                    enabled = !state.exportingCsv && !state.backupBusy,
                                    onClick = viewModel::bringLatest,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                LoadingButton(
                                    text = "مراجعة ونشر بيانات المناوبة",
                                    outlined = true,
                                    loading = state.syncing,
                                    enabled = !state.exportingCsv && !state.backupBusy,
                                    onClick = viewModel::uploadCurrent,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (state.canUndoDownload) AppOutlinedButton(
                                    onClick = { confirmUndoDownload = true }, enabled = !state.syncing && !state.backupBusy,
                                    modifier = Modifier.fillMaxWidth()) { Text("التراجع عن آخر تنزيل") }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingsSubheading("تصدير واستيراد الملفات")
                                LoadingButton(
                                    text = "حفظ بيانات المرضى CSV",
                                    outlined = true,
                                    loading = state.exportingCsv,
                                    onClick = viewModel::exportCsv,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.syncing
                                )
                                if (canManageProject) {
                                    AppOutlinedButton(
                                        onClick = onOpenVbaImport,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("استيراد بيانات من الإكسل") }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingsSubheading("النسخ الاحتياطي والاستعادة")
                                Text(
                                    "ملف محمي بكلمة مرور ومستقل عن تليجرام. احتفظ بكلمة المرور في مكان آمن.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LoadingButton(
                                    text = "إنشاء نسخة مشفرة",
                                    outlined = true,
                                    loading = state.backupBusy,
                                    enabled = !state.syncing,
                                    onClick = {
                                        backupPassword = ""
                                        backupDialogMode = "export"
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                AppOutlinedButton(
                                    onClick = { openBackup.launch(arrayOf("application/octet-stream", "*/*")) },
                                    enabled = !state.backupBusy && !state.syncing,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("استعادة نسخة مشفرة") }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                SettingsSubheading("النشر بعد الاستعادة")
                                Text(
                                    "بعد الاستعادة استخدم «مراجعة ونشر بيانات المناوبة» أعلاه لعرض النسخة المحلية الكاملة قبل نشرها. يشمل الاستبدال الحذف والترتيب وأطباء المناوبة، ويتوقف إذا تغيرت البيانات بعد المراجعة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            SettingsSectionId.CONNECTION -> {
                                Text(
                                    "إعادة الاتصال بالمشروع الحالي باستخدام ملف انضمام محمي، أو استبدال رمز البوت بصلاحية المدير.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ProjectConnectionAction(isAdmin = canManageProject)
                            }
                            SettingsSectionId.ADMIN -> {
                                Text("ضم أجهزة جديدة", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "أنشئ ملفاً مشفراً يعبئ إعدادات المستشفى والبوت على جهاز العضو. أرسل عبارة المرور إليه عبر قناة منفصلة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LoadingButton(
                                    text = "إنشاء ملف انضمام مشفر",
                                    outlined = true,
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
                            }
                            SettingsSectionId.SECURITY -> {
                                AppToggleRow(
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
                            SettingsSectionId.STORAGE -> {
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
                        }
                    }
                }

                if (workspace == SettingsWorkspace.PREFERENCES) {
                    SettingsSubheading("أدوات المشروع والصيانة")
                    AppOutlinedButton(onClick = onOpenDataManagement, modifier = Modifier.fillMaxWidth()) {
                        Text("إدارة البيانات")
                    }
                    AppOutlinedButton(onClick = onOpenProject, modifier = Modifier.fillMaxWidth()) {
                        Text("إدارة المشروع")
                    }
                    AppTextButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                        Text("التشخيص ومعلومات الجهاز")
                    }
                    AppOutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تسجيل الخروج") }
                }

                Spacer(Modifier.height(UiSpacing.small))
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
                PasswordField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = "كلمة المرور (8 محارف على الأقل)"
                )
            },
            confirmButton = {
                AppTextButton(
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
                AppTextButton(onClick = {
                    backupDialogMode = null
                    backupPassword = ""
                    if (mode == "restore") pendingImportUri = null
                }) { Text("إلغاء") }
            }
        )
    }

    if (canManageProject && provisioningDialog) {
        val directDownloads = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        AlertDialog(
            onDismissRequest = {
                provisioningDialog = false
                provisioningPassphrase = ""
            },
            title = { Text("حماية ملف الانضمام") },
            text = {
                Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text("ملف JSON محمي بكلمة مرور، بلا بيانات مرضى. اختر عبارة قوية من 10 محارف على الأقل، وأرسلها منفصلة عن الملف.")
                    PasswordField(
                        value = provisioningPassphrase,
                        onValueChange = { provisioningPassphrase = it },
                        label = "عبارة المرور"
                    )
                    Text(if (directDownloads) "يُحفظ الملف كاملاً في التنزيلات/ShiftReport، ويُتحقق من حجمه ومحتواه قبل إعلان النجاح."
                        else "يمكن مشاركة الملف الجاهز مباشرة أو اختيار مكان لحفظه.",
                        style = MaterialTheme.typography.bodySmall)
                    if (directDownloads) AppTextButton(
                        enabled = provisioningPassphrase.length >= 10 && !state.provisioningBusy,
                        onClick = {
                            viewModel.prepareProjectProvisioning(provisioningPassphrase, share = true)
                            provisioningPassphrase = ""
                            provisioningDialog = false
                        }, modifier = Modifier.fillMaxWidth()
                    ) { Text("مشاركة الملف مباشرة") }
                    AppTextButton(
                        enabled = provisioningPassphrase.length >= 10 && !state.provisioningBusy,
                        onClick = {
                            viewModel.prepareProjectProvisioning(provisioningPassphrase)
                            provisioningPassphrase = ""
                            provisioningDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
                    ) { Text("اختيار مكان آخر للحفظ") }
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = provisioningPassphrase.length >= 10 && !state.provisioningBusy,
                    onClick = {
                        viewModel.prepareProjectProvisioning(provisioningPassphrase,
                            share = !directDownloads, saveToDownloads = directDownloads)
                        provisioningPassphrase = ""
                        provisioningDialog = false
                    }
                ) { Text(if (directDownloads) "حفظ إلى التنزيلات" else "مشاركة الملف") }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    provisioningDialog = false
                    provisioningPassphrase = ""
                }) { Text("إلغاء") }
            }
        )
    }

    state.publicationReviewText?.let { reviewText ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPublicationReview,
            title = { Text("مراجعة نشر النسخة الكاملة") },
            text = {
                Column(Modifier.appDialogBody(UiDialog.reviewBodyMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text(reviewText)
                    Text("التأكيد ينشر البيانات فقط؛ إرسال التقرير إجراء منفصل يتطلب معاينته.")
                }
            },
            confirmButton = {
                AppTextButton(onClick = viewModel::confirmPublication, enabled = !state.syncing) {
                    Text("راجعت النسخة وأؤكد نشرها")
                }
            },
            dismissButton = {
                AppTextButton(onClick = viewModel::cancelPublicationReview, enabled = !state.syncing) { Text("إلغاء") }
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
                AppTextButton(onClick = {
                    val uri = pendingImportUri
                    val password = pendingRestorePassword
                    confirmBackupRestore = false
                    pendingImportUri = null
                    pendingRestorePassword = ""
                    if (uri != null) viewModel.restoreEncryptedBackup(uri, password)
                }) { Text("استعادة واستبدال") }
            },
            dismissButton = {
                AppTextButton(onClick = {
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
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPositioned: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth().onGloballyPositioned { onPositioned(it.positionInParent().y.roundToInt()) }) {
        Column(Modifier.fillMaxWidth().padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            com.hos.rushdpatients.ui.components.AppSectionHeader(title = title,
                expanded = expanded, onToggle = onToggle, summary = if (expanded) null else summary)
            if (expanded) { Divider(color = MaterialTheme.colorScheme.outlineVariant); content() }
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
            .padding(vertical = UiSpacing.micro),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)
    ) {
        Text(supervisor.fullName, style = MaterialTheme.typography.bodyMedium)
        AppTextField(
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
            AppNotice("تغيّر سجل المشرف؛ راجع النسخة المحفوظة قبل الحفظ", kind = com.hos.rushdpatients.ui.components.NoticeKind.WARNING)
            AppTextButton(onClick = {
                baseline = supervisor
                chatId = supervisor.supervisorGroupChatId?.toString().orEmpty()
            }, enabled = enabled) { Text("تحميل النسخة المحفوظة") }
        }
        LoadingButton(
            outlined = true,
            text = if (chatId.isBlank()) "إزالة المعرف" else "حفظ",
            loading = saving,
            onClick = { onSave(baseline, chatId) },
            enabled = enabled && supervisor == baseline && chatId.trim() != supervisor.supervisorGroupChatId?.toString().orEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private enum class SettingsGroup(val title: String) {
    DAILY("التخصيص والعمل اليومي"),
    PROJECT("البيانات والمشروع"),
    DEVICE("الأمان ومعلومات الجهاز")
}

private enum class SettingsSectionId(
    val title: String,
    val group: SettingsGroup,
    val workspace: SettingsWorkspace,
    val adminOnly: Boolean = false
) {
    APPEARANCE("المظهر والخط", SettingsGroup.DAILY, SettingsWorkspace.PREFERENCES),
    PATIENT_CARDS("بطاقات المرضى", SettingsGroup.DAILY, SettingsWorkspace.PREFERENCES),
    WORKFLOW("الجولات والمناوبات", SettingsGroup.DAILY, SettingsWorkspace.PREFERENCES),
    REPORT("التقارير وملفات PDF", SettingsGroup.DAILY, SettingsWorkspace.PREFERENCES),
    SYNC("المزامنة والملفات والنسخ الاحتياطي", SettingsGroup.PROJECT, SettingsWorkspace.DATA),
    CONNECTION("اتصال المشروع", SettingsGroup.PROJECT, SettingsWorkspace.PROJECT),
    ADMIN("الأجهزة ووجهات التقارير", SettingsGroup.PROJECT, SettingsWorkspace.PROJECT, true),
    SECURITY("قفل التطبيق", SettingsGroup.DEVICE, SettingsWorkspace.PREFERENCES),
    STORAGE("التخزين ومعلومات الجهاز", SettingsGroup.DEVICE, SettingsWorkspace.DIAGNOSTICS)
}

private fun SettingsSectionId.summary(state: SettingsUiState): String = when (this) {
    SettingsSectionId.APPEARANCE -> "${state.appearance.arabicLabel} · ${state.appTheme.arabicLabel} · خط ${state.fontScale.arabicLabel}"
    SettingsSectionId.PATIENT_CARDS -> listOf(
        if (state.patientDetailsExpanded) "التفاصيل موسعة" else "التفاصيل مطوية",
        if (state.patientTwoColumn) "عمودان عند توفر المساحة" else "عمود واحد",
        if (state.patientCompactDensity) "مسافات مضغوطة" else "مسافات عادية"
    ).joinToString(" · ")
    SettingsSectionId.WORKFLOW -> "حفظ والتالي: " + (if (state.saveAndNext) "مفعل" else "متوقف") +
        " · اقتراح نسخ المناوبة: " + if (state.guidedRollover) "مفعل" else "متوقف"
    SettingsSectionId.REPORT -> if (state.reportAsPdf) "PDF · ${state.pdfStyle.arabicLabel}" else "إرسال كنص"
    SettingsSectionId.SYNC -> "جلب ومراجعة النشر · ملفات CSV · نسخ مشفرة واستعادة"
    SettingsSectionId.CONNECTION -> "إعادة الاتصال بالمشروع الحالي"
    SettingsSectionId.ADMIN -> "ملفات الانضمام · مجموعات المشرفين"
    SettingsSectionId.SECURITY -> "قفل تلقائي: ${state.autoLockMinutes} د · " + if (state.biometricEnabled) "البصمة مفعلة" else "البصمة متوقفة"
    SettingsSectionId.STORAGE -> "${state.databaseSize} · ${state.storedShifts} مناوبة محفوظة"
}
