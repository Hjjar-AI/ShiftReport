package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.ui.theme.patientBadgeColors
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.data.model.PatientBadgePriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@Composable
private fun autoDirTextStyle(): TextStyle =
    LocalTextStyle.current.copy(
        textDirection = TextDirection.ContentOrRtl
    )

@Composable
fun AddPatientDialog(
    doctors: List<Doctor>,
    draft: PatientDraft? = null,
    saving: Boolean = false,
    onDraftChanged: (PatientDraft) -> Unit = {},
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    PatientFormDialog(
        title = "إضافة مريض",
        initial = null,
        restoredDraft = draft,
        doctors = doctors,
        saving = saving,
        onDraftChanged = onDraftChanged,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun CopyPatientDialog(
    source: Patient,
    doctors: List<Doctor>,
    saving: Boolean = false,
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    val copy = remember(source) {
        source.copy(
            id = java.util.UUID.randomUUID().toString(),
            admittanceNumber = "",
            admittanceDate = null,
            isPriority = false,
            tasks = emptyList(),
            lastEditedByDoctorId = null,
            lastEditedByName = null,
            sortOrder = 0,
            updatedAt = Instant.now(),
            deletedAt = null
        )
    }
    PatientFormDialog(
        title = "نسخ بيانات مريض",
        initial = copy,
        restoredDraft = null,
        doctors = doctors,
        saving = saving,
        onDraftChanged = {},
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun PatientFormDialog(
    title: String,
    initial: Patient?,
    savedBaseline: Patient? = initial,
    restoredDraft: PatientDraft? = null,
    doctors: List<Doctor>,
    saving: Boolean,
    reviewAction: @Composable () -> Unit = {},
    onDraftChanged: (PatientDraft) -> Unit = {},
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    var formMode by remember(initial) { mutableStateOf(PatientFormMode.GENERAL) }
    var tasks by remember(initial, restoredDraft) {
        mutableStateOf(initial?.tasks ?: restoredDraft?.tasks.orEmpty())
    }
    var showBadgeEditor by remember(initial) { mutableStateOf(false) }
    val today = LocalDate.now()
    val currentYear = today.year
    var admittanceNumber by remember(initial, restoredDraft) {
        mutableStateOf(initial?.admittanceNumber ?: restoredDraft?.admittanceNumber.orEmpty())
    }
    var admittanceDate by remember(initial) {
        mutableStateOf<LocalDate?>(
            initial?.admittanceDate
                ?: restoredDraft?.admittanceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: today
        )
    }
    var gender by remember(initial, restoredDraft) {
        mutableStateOf(initial?.gender ?: Gender.fromCode(restoredDraft?.gender.orEmpty()))
    }
    var name by remember(initial, restoredDraft) {
        mutableStateOf(initial?.name ?: restoredDraft?.name.orEmpty())
    }
    var birthYearText by remember(initial) {
        mutableStateOf(initial?.birthDate?.year?.toString() ?: restoredDraft?.birthYear.orEmpty())
    }
    var ageText by remember(initial) {
        mutableStateOf(initial?.age?.toString() ?: restoredDraft?.age.orEmpty())
    }
    var hasCompanion by remember(initial, restoredDraft) {
        mutableStateOf(initial?.hasCompanion ?: restoredDraft?.hasCompanion ?: false)
    }
    var isPriority by remember(initial, restoredDraft) {
        mutableStateOf(initial?.isPriority ?: restoredDraft?.isPriority ?: false)
    }
    var badges by remember(initial, restoredDraft) {
        mutableStateOf(
            initial?.badges ?: restoredDraft?.badges.orEmpty().map { badge ->
                PatientBadge(badge.text, badge.priority?.let { PatientBadgePriority.fromCode(it) })
            }
        )
    }
    var badgeDraftText by remember(initial, restoredDraft) {
        mutableStateOf(restoredDraft?.badgeDraftText.orEmpty())
    }
    var badgeDraftPriority by remember(initial, restoredDraft) {
        mutableStateOf(
            restoredDraft?.badgeDraftPriority?.let { PatientBadgePriority.fromCode(it) }
        )
    }
    var diagnosisType by remember(initial) {
        mutableStateOf(
            initial?.diagnosisType ?: DiagnosisType.fromCode(restoredDraft?.diagnosisType.orEmpty())
        )
    }
    var initialDiagnosis by remember(initial, restoredDraft) {
        mutableStateOf(initial?.initialDiagnosis ?: restoredDraft?.initialDiagnosis.orEmpty())
    }
    var treatmentItems by remember(initial, restoredDraft) {
        mutableStateOf(initial?.let { parseFieldItems(it.treatmentPlan) } ?: restoredDraft?.treatmentItems.orEmpty())
    }
    var treatmentDraft by remember(initial, restoredDraft) { mutableStateOf(restoredDraft?.treatmentDraft.orEmpty()) }
    var followUpItems by remember(initial, restoredDraft) {
        mutableStateOf(initial?.let { parseFieldItems(it.followUp) } ?: restoredDraft?.followUpItems.orEmpty())
    }
    var followUpDraft by remember(initial, restoredDraft) { mutableStateOf(restoredDraft?.followUpDraft.orEmpty()) }
    var labItems by remember(initial, restoredDraft) {
        mutableStateOf(initial?.let { parseFieldItems(it.labs) } ?: restoredDraft?.labItems.orEmpty())
    }
    var labDraft by remember(initial, restoredDraft) { mutableStateOf(restoredDraft?.labDraft.orEmpty()) }
    var residentId by remember(initial, restoredDraft) {
        mutableStateOf(initial?.responsibleResidentId ?: restoredDraft?.residentId)
    }
    var specialistId by remember(initial, restoredDraft) {
        mutableStateOf(initial?.responsibleSpecialistId ?: restoredDraft?.specialistId)
    }
    var validationMessages by remember(initial) { mutableStateOf(emptyList<String>()) }

    val birthYear = birthYearText.trim().toIntOrNull()
    val age = birthYear?.let { (currentYear - it).takeIf { a -> a in 0..120 } }
    val admissionDays = admittanceDate?.takeUnless { it.isAfter(today) }
        ?.let { ChronoUnit.DAYS.between(it, today).toInt() }
    val activeDoctors = doctors.filterNot { it.isDeleted }
    val residentDoctors = activeDoctors.filter { it.clinicalRole.canBeResident() }
    val specialistDoctors = activeDoctors.filter { it.clinicalRole.canBeSupervisor() }
    val autoStyle = autoDirTextStyle()
    val formScrollState = rememberScrollState()
    val formScope = rememberCoroutineScope()
    val changedFieldCount = listOf(
        admittanceNumber != savedBaseline?.admittanceNumber.orEmpty(),
        name != savedBaseline?.name.orEmpty(),
        birthYearText != savedBaseline?.birthDate?.year?.toString().orEmpty(),
        initialDiagnosis != savedBaseline?.initialDiagnosis.orEmpty(),
        treatmentItems.joinToString("\n") != savedBaseline?.treatmentPlan.orEmpty(),
        followUpItems.joinToString("\n") != savedBaseline?.followUp.orEmpty(),
        labItems.joinToString("\n") != savedBaseline?.labs.orEmpty(),
        residentId != savedBaseline?.responsibleResidentId,
        specialistId != savedBaseline?.responsibleSpecialistId,
        badges != savedBaseline?.badges.orEmpty(),
        tasks != savedBaseline?.tasks.orEmpty(),
        badgeDraftText.isNotBlank(),
        isPriority != (savedBaseline?.isPriority ?: false)
    ).count { it }

    val admissionIdentityContent: @Composable () -> Unit = {
        SectionTitle(
            "بيانات الدخول",
            complete = admittanceNumber.isNotBlank() && admittanceDate != null
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = admittanceNumber,
                onValueChange = { admittanceNumber = it; validationMessages = emptyList() },
                label = { Text("رقم القبول الحالي *") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = autoStyle,
                isError = validationMessages.any { it.contains("رقم القبول") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
            DateField(
                value = admittanceDate,
                onValueChange = { admittanceDate = it; validationMessages = emptyList() },
                label = "تاريخ الدخول *",
                supportingText = admissionDays?.let { "مدة الإقامة: $it يوم" },
                modifier = Modifier.weight(1f)
            )
        }

        SectionTitle(
            "البيانات الشخصية",
            complete = name.isNotBlank() && birthYear != null && age != null
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; validationMessages = emptyList() },
            label = { Text("اسم المريض *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = autoStyle,
            isError = validationMessages.any { it.contains("اسم المريض") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = birthYearText,
                onValueChange = { value ->
                    val cleaned = value.filter(Char::isDigit).take(4)
                    birthYearText = cleaned
                    ageText = cleaned.toIntOrNull()
                        ?.let { currentYear - it }
                        ?.takeIf { it in 0..120 }
                        ?.toString()
                        .orEmpty()
                    validationMessages = emptyList()
                },
                label = { Text("سنة الميلاد *") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = autoStyle,
                isError = validationMessages.any { it.contains("سنة الميلاد") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
            Text("أو", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = ageText,
                onValueChange = { value ->
                    val cleaned = value.filter(Char::isDigit).take(3)
                    ageText = cleaned
                    birthYearText = cleaned.toIntOrNull()
                        ?.takeIf { it in 0..120 }
                        ?.let { currentYear - it }
                        ?.toString()
                        .orEmpty()
                    validationMessages = emptyList()
                },
                label = { Text("العمر *") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = autoStyle,
                isError = validationMessages.any { it.contains("العمر") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("الجنس", style = MaterialTheme.typography.labelMedium)
            FilterChip(
                selected = gender == Gender.MALE,
                onClick = { gender = Gender.MALE },
                label = { Text("ذكر") }
            )
            FilterChip(
                selected = gender == Gender.FEMALE,
                onClick = { gender = Gender.FEMALE },
                label = { Text("أنثى") }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("يوجد مرافق", style = MaterialTheme.typography.labelMedium)
            Switch(checked = hasCompanion, onCheckedChange = { hasCompanion = it })
        }
    }

    LaunchedEffect(
        admittanceNumber, admittanceDate, gender, name, birthYearText, ageText,
        hasCompanion, diagnosisType, initialDiagnosis, treatmentItems, treatmentDraft,
        followUpItems, followUpDraft, labItems, labDraft, residentId, specialistId,
        badges, badgeDraftText, badgeDraftPriority, isPriority, tasks
    ) {
        if (initial == null) {
            onDraftChanged(
                PatientDraft(
                    admittanceNumber = admittanceNumber,
                    admittanceDate = admittanceDate?.toString(),
                    gender = gender.code,
                    name = name,
                    birthYear = birthYearText,
                    age = ageText,
                    hasCompanion = hasCompanion,
                    diagnosisType = diagnosisType.code,
                    initialDiagnosis = initialDiagnosis,
                    treatmentItems = treatmentItems,
                    treatmentDraft = treatmentDraft,
                    followUpItems = followUpItems,
                    followUpDraft = followUpDraft,
                    labItems = labItems,
                    labDraft = labDraft,
                    residentId = residentId,
                    specialistId = specialistId,
                    tasks = tasks,
                    badges = badges.map { PatientBadgeDraft(it.text, it.priority?.code) },
                    badgeDraftText = badgeDraftText,
                    badgeDraftPriority = badgeDraftPriority?.code,
                    isPriority = isPriority
                )
            )
        }
    }

    WardFormDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                reviewAction()
                if (changedFieldCount > 0) {
                    Text(
                        "توجد تغييرات غير محفوظة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(formScrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (validationMessages.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("راجع الحقول التالية", style = MaterialTheme.typography.titleSmall)
                            validationMessages.forEach { Text("• $it") }
                        }
                    }
                }
                if (initial == null) admissionIdentityContent()

                SectionTitle(
                    "التشخيص والعلاج",
                    complete = initialDiagnosis.isNotBlank() &&
                        (treatmentItems + treatmentDraft).any(String::isNotBlank) &&
                        (followUpItems + followUpDraft).any(String::isNotBlank)
                )
                Text("نوع التشخيص", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiagnosisType.entries.forEach { type ->
                        FilterChip(
                            selected = diagnosisType == type,
                            onClick = { diagnosisType = type },
                            label = { Text(type.arabicLabel) }
                        )
                    }
                }
                MultilineField(
                    initialDiagnosis,
                    { initialDiagnosis = it; validationMessages = emptyList() },
                    "التشخيص الأولي",
                    autoStyle,
                    isError = validationMessages.any { it.contains("التشخيص الأولي") },
                    placeholder = when (diagnosisType) {
                        DiagnosisType.PSYCHIATRIC -> "مثال: Psychosis، Bipolar II disorder"
                        DiagnosisType.ADDICTION -> "مثال: Drug-induced psychosis، Stimulant use disorder"
                        DiagnosisType.DUAL -> "مثال: Schizophrenia + Alcohol use disorder"
                    }
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = formMode == PatientFormMode.GENERAL,
                        onClick = { formMode = PatientFormMode.GENERAL },
                        label = { Text("عام") }
                    )
                    FilterChip(
                        selected = formMode == PatientFormMode.ADVANCED,
                        onClick = { formMode = PatientFormMode.ADVANCED },
                        label = { Text("سريري متقدم") }
                    )
                }
                if (formMode == PatientFormMode.GENERAL) {
                    MultilineField(
                        value = (treatmentItems + treatmentDraft)
                            .filter(String::isNotBlank)
                            .joinToString("\n"),
                        onValueChange = {
                            treatmentItems = parseFieldItems(it)
                            treatmentDraft = ""
                            validationMessages = emptyList()
                        },
                        label = "الخطة العلاجية",
                        textStyle = autoStyle,
                        isError = validationMessages.any { it.contains("الخطة العلاجية") }
                    )
                    MultilineField(
                        value = (followUpItems + followUpDraft)
                            .filter(String::isNotBlank)
                            .joinToString("\n"),
                        onValueChange = {
                            followUpItems = parseFieldItems(it)
                            followUpDraft = ""
                            validationMessages = emptyList()
                        },
                        label = "المتابعة",
                        textStyle = autoStyle,
                        isError = validationMessages.any { it == "المتابعة مطلوبة" }
                    )
                    MultilineField(
                        value = (labItems + labDraft).filter(String::isNotBlank).joinToString("\n"),
                        onValueChange = {
                            labItems = parseFieldItems(it)
                            labDraft = ""
                            validationMessages = emptyList()
                        },
                        label = "التحاليل",
                        textStyle = autoStyle,
                        isError = false
                    )
                } else {
                MultiValueEditor(
                    label = "الخطة العلاجية",
                    items = treatmentItems,
                    draft = treatmentDraft,
                    onDraftChange = { treatmentDraft = it; validationMessages = emptyList() },
                    onEditItem = { index, value ->
                        treatmentItems = treatmentItems.toMutableList().also { it[index] = value }
                        validationMessages = emptyList()
                    },
                    onMove = { from, to ->
                        treatmentItems = reorder(treatmentItems, from, to)
                    },
                    onAdd = {
                        val value = treatmentDraft.trim()
                        if (value.isNotEmpty()) {
                            treatmentItems = treatmentItems + value
                            treatmentDraft = ""
                        }
                    },
                    onRemove = { index ->
                        treatmentItems = treatmentItems.filterIndexed { i, _ -> i != index }
                    },
                    onAddSeparator = { treatmentItems = treatmentItems + FIELD_SEPARATOR },
                    onAddDate = null,
                    textStyle = autoStyle,
                    isError = validationMessages.any { it.contains("الخطة العلاجية") }
                )
                MultiValueEditor(
                    label = "المتابعة",
                    items = followUpItems,
                    draft = followUpDraft,
                    onDraftChange = { followUpDraft = it; validationMessages = emptyList() },
                    onEditItem = { index, value ->
                        followUpItems = followUpItems.toMutableList().also { it[index] = value }
                        validationMessages = emptyList()
                    },
                    onMove = { from, to ->
                        followUpItems = reorder(followUpItems, from, to)
                    },
                    onAdd = {
                        followUpDraft.trim().takeIf { it.isNotEmpty() }?.let {
                            followUpItems = followUpItems + it
                            followUpDraft = ""
                        }
                    },
                    onRemove = { index ->
                        followUpItems = followUpItems.filterIndexed { i, _ -> i != index }
                    },
                    onAddSeparator = { followUpItems = followUpItems + FIELD_SEPARATOR },
                    onAddDate = null,
                    textStyle = autoStyle,
                    isError = validationMessages.any { it == "المتابعة مطلوبة" }
                )
                MultiValueEditor(
                    label = "التحاليل",
                    items = labItems,
                    draft = labDraft,
                    onDraftChange = { labDraft = it; validationMessages = emptyList() },
                    onEditItem = { index, value ->
                        labItems = labItems.toMutableList().also { it[index] = value }
                        validationMessages = emptyList()
                    },
                    onMove = { from, to ->
                        labItems = reorder(labItems, from, to)
                    },
                    onAdd = {
                        labDraft.trim().takeIf { it.isNotEmpty() }?.let {
                            labItems = labItems + it
                            labDraft = ""
                        }
                    },
                    onRemove = { index ->
                        labItems = labItems.filterIndexed { i, _ -> i != index }
                    },
                    onAddSeparator = { labItems = labItems + FIELD_SEPARATOR },
                    onAddDate = { date ->
                        labItems = labItems + dateMarkerFrom(date)
                    },
                    textStyle = autoStyle,
                    isError = false
                )
                }

                SectionTitle("مهام المريض")
                PatientTaskEditor(tasks, doctors, !saving) { tasks = it }

                SectionTitle(
                    "الفريق المسؤول",
                    complete = residentId != null && specialistId != null && residentId != specialistId
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    DoctorDropdown(
                        label = "المقيم",
                        undefinedLabel = "مقيم غير محدد",
                        selectedId = residentId,
                        doctors = residentDoctors,
                        onSelected = { residentId = it; validationMessages = emptyList() },
                        modifier = Modifier.weight(1f)
                    )
                    DoctorDropdown(
                        label = "الاختصاصي",
                        undefinedLabel = "اختصاصي غير محدد",
                        selectedId = specialistId,
                        doctors = specialistDoctors,
                        onSelected = { specialistId = it; validationMessages = emptyList() },
                        modifier = Modifier.weight(1f)
                    )
                }

                SectionTitle("الأولوية والشارات")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تثبيت كمريض ذي أولوية", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = isPriority, onCheckedChange = { isPriority = it })
                }
                TextButton(onClick = { showBadgeEditor = !showBadgeEditor },
                    modifier = Modifier.semantics { stateDescription = if (showBadgeEditor) "موسع" else "مطوي" }) {
                    Text(if (showBadgeEditor) "إغلاق تحرير الشارات" else "تحرير الشارات (${badges.size})")
                }
                if (!showBadgeEditor && (badges.isNotEmpty() || badgeDraftText.isNotBlank())) {
                    Text(
                        (badges.map { it.text } + listOf(badgeDraftText).filter(String::isNotBlank)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showBadgeEditor) {
                    badges.forEachIndexed { index, badge ->
                        val badgeColors = patientBadgeColors(badge.priority)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = badgeColors.container,
                            contentColor = badgeColors.content,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    buildString {
                                        append(badge.text)
                                        badge.priority?.let { append(" · ").append(it.arabicLabel) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { badges = badges.filterIndexed { i, _ -> i != index } }) {
                                    Icon(Icons.Filled.Close, contentDescription = "حذف الشارة")
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = badgeDraftText,
                        onValueChange = { badgeDraftText = it.take(80) },
                        label = { Text("نص الشارة (اختياري)") },
                        supportingText = { Text("مثال: تحسس دوائي، يحتاج مراجعة، خطر سقوط") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = autoStyle
                    )
                    Text("مستوى الشارة (اختياري)", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = badgeDraftPriority == null,
                            onClick = { badgeDraftPriority = null },
                            label = { Text("بدون مستوى") }
                        )
                        PatientBadgePriority.entries.forEach { level ->
                            FilterChip(
                                selected = badgeDraftPriority == level,
                                onClick = { badgeDraftPriority = level },
                                label = { Text(level.arabicLabel) }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val text = badgeDraftText.trim()
                            if (text.isNotEmpty()) {
                                badges = badges + PatientBadge(text, badgeDraftPriority)
                                badgeDraftText = ""
                                badgeDraftPriority = null
                            }
                        },
                        enabled = badgeDraftText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("إضافة الشارة") }

                }

                if (initial != null) admissionIdentityContent()
            }
        },
        confirmButton = {
            Button(modifier = Modifier.fillMaxWidth(), enabled = !saving, onClick = {
                val pendingTreatment = treatmentDraft.trim().takeIf { it.isNotEmpty() }
                val resolvedTreatments = treatmentItems + listOfNotNull(pendingTreatment)
                val treatmentPlan = resolvedTreatments
                    .filter { it == FIELD_SEPARATOR || it.isNotBlank() }
                    .joinToString("\n")

                val pendingFollowUp = followUpDraft.trim().takeIf { it.isNotEmpty() }
                val resolvedFollowUp = followUpItems + listOfNotNull(pendingFollowUp)
                val followUp = resolvedFollowUp
                    .filter { it == FIELD_SEPARATOR || it.isNotBlank() }
                    .joinToString("\n")

                val pendingLab = labDraft.trim().takeIf { it.isNotEmpty() }
                val resolvedLabs = labItems + listOfNotNull(pendingLab)
                val labs = resolvedLabs
                    .filter { it == FIELD_SEPARATOR || it.isNotBlank() }
                    .joinToString("\n")

                val errors = buildList {
                    if (admittanceNumber.isBlank()) add("رقم القبول الحالي مطلوب")
                    if (admittanceDate == null) add("اختر تاريخ الدخول")
                    if (admittanceDate?.isAfter(today) == true) {
                        add("تاريخ الدخول لا يمكن أن يكون في المستقبل")
                    }
                    if (name.isBlank()) add("اسم المريض مطلوب")
                    if (ageText.toIntOrNull()?.let { it !in 0..120 } == true) {
                        add("العمر يجب أن يكون بين 0 و120 سنة")
                    }
                    if (birthYear == null) add("أدخل سنة الميلاد أو العمر")
                    if (birthYear != null && birthYear > currentYear) {
                        add("سنة الميلاد لا يمكن أن تكون في المستقبل")
                    }
                    if (birthYear != null && age == null) add("سنة الميلاد تنتج عمراً غير صحيح")
                    if (initialDiagnosis.isBlank()) add("التشخيص الأولي مطلوب")
                    if (resolvedTreatments.none { it != FIELD_SEPARATOR && it.isNotBlank() }) {
                        add("الخطة العلاجية مطلوبة")
                    }
                    if (resolvedFollowUp.none { it != FIELD_SEPARATOR && it.isNotBlank() }) {
                        add("المتابعة مطلوبة")
                    }
                    if (residentId != null && residentId == specialistId) {
                        add("يجب اختيار طبيبين مختلفين للمقيم والاختصاصي")
                    }
                }
                if (errors.isNotEmpty()) {
                    validationMessages = errors
                    formScope.launch { formScrollState.animateScrollTo(0) }
                    return@Button
                }

                val resolvedBirthDate = birthYear?.let { LocalDate.of(it, 1, 1) }

                val resolvedBadges = badges + badgeDraftText.trim()
                    .takeIf(String::isNotBlank)
                    ?.let { listOf(PatientBadge(it, badgeDraftPriority)) }
                    .orEmpty()

                val patient = initial?.copy(
                    admittanceNumber = admittanceNumber.trim(),
                    admittanceDate = admittanceDate,
                    gender = gender,
                    name = name.trim(),
                    birthDate = resolvedBirthDate,
                    hasCompanion = hasCompanion,
                    diagnosisType = diagnosisType,
                    initialDiagnosis = initialDiagnosis.trim(),
                    treatmentPlan = treatmentPlan.trim(),
                    followUp = followUp.trim(),
                    labs = labs.trim(),
                    responsibleResidentId = residentId,
                    responsibleSpecialistId = specialistId,
                    badges = resolvedBadges,
                    tasks = tasks,
                    isPriority = isPriority
                ) ?: Patient(
                    admittanceNumber = admittanceNumber.trim(),
                    admittanceDate = admittanceDate,
                    gender = gender,
                    name = name.trim(),
                    birthDate = resolvedBirthDate,
                    hasCompanion = hasCompanion,
                    diagnosisType = diagnosisType,
                    initialDiagnosis = initialDiagnosis.trim(),
                    treatmentPlan = treatmentPlan.trim(),
                    followUp = followUp.trim(),
                    labs = labs.trim(),
                    responsibleResidentId = residentId,
                    responsibleSpecialistId = specialistId,
                    badges = resolvedBadges,
                    tasks = tasks,
                    isPriority = isPriority
                )
                onConfirm(patient)
            }) { Text(if (saving) "جار الحفظ…" else "حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
        }
    )
}

private enum class PatientFormMode { GENERAL, ADVANCED }

// ---------------- Section + field helpers ----------------

@Composable
private fun SectionTitle(text: String, complete: Boolean? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = buildString {
                append(text)
                when (complete) {
                    true -> append("  ✓")
                    false -> Unit
                    null -> Unit
                }
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = UiSpacing.small, vertical = UiSpacing.tiny).semantics { heading() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    supportingText: String?,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$label: ${value?.toString() ?: "اختر"}",
                style = MaterialTheme.typography.bodyMedium
            )
            supportingText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("إلغاء") }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun MultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    textStyle: TextStyle,
    isError: Boolean = false,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder != null) Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 1,
        maxLines = 4,
        textStyle = textStyle,
        isError = isError
    )
}

// ---------------- Multi-value editor ----------------

private const val FIELD_SEPARATOR = "---"
private const val DATE_PREFIX = "@date:"

private fun parseFieldItems(value: String?): List<String> = value.orEmpty()
    .split('\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

private fun isDateMarker(item: String): Boolean = item.startsWith(DATE_PREFIX)

private fun dateMarkerValue(item: String): LocalDate? =
    runCatching { LocalDate.parse(item.removePrefix(DATE_PREFIX)) }.getOrNull()

private fun dateMarkerFrom(date: LocalDate): String = "$DATE_PREFIX$date"

private fun <T> reorder(list: List<T>, from: Int, to: Int): List<T> {
    if (from == to) return list
    if (from !in list.indices || to !in list.indices) return list
    val mutable = list.toMutableList()
    val moved = mutable.removeAt(from)
    mutable.add(to, moved)
    return mutable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiValueEditor(
    label: String,
    items: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onEditItem: (Int, String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onAddSeparator: () -> Unit,
    onAddDate: ((LocalDate) -> Unit)?,
    textStyle: TextStyle,
    isError: Boolean = false
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$label${if (label == "التحاليل") "" else " *"}",
            style = MaterialTheme.typography.labelMedium
        )

        items.forEachIndexed { index, item ->
            when {
                item == FIELD_SEPARATOR -> SeparatorRow(
                    index = index,
                    itemCount = items.size,
                    onMove = onMove,
                    onRemove = onRemove
                )
                isDateMarker(item) -> DateMarkerRow(
                    index = index,
                    itemCount = items.size,
                    date = dateMarkerValue(item) ?: LocalDate.now(),
                    onMove = onMove,
                    onRemove = onRemove
                )
                else -> TextItemRow(
                    index = index,
                    itemCount = items.size,
                    item = item,
                    onEditItem = onEditItem,
                    onMove = onMove,
                    onRemove = onRemove,
                    textStyle = textStyle
                )
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("إضافة إلى $label") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 2,
            textStyle = textStyle,
            isError = isError
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onAdd,
                enabled = draft.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("إضافة") }
            OutlinedButton(
                onClick = onAddSeparator,
                enabled = items.isNotEmpty() && items.last() != FIELD_SEPARATOR,
                modifier = Modifier.weight(1f)
            ) { Text("فاصل") }
            if (onAddDate != null) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) { Text("تاريخ") }
            }
        }
    }

    if (showDatePicker && onAddDate != null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        onAddDate(date)
                    }
                    showDatePicker = false
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
            }
        ) { DatePicker(state = state) }
    }
}

// ---------------- Item rows ----------------

@Composable
private fun DragHandleButton(
    index: Int,
    itemCount: Int,
    onMove: (Int, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "إعادة ترتيب العنصر ${index + 1}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("نقل لأعلى") },
                enabled = index > 0,
                onClick = {
                    onMove(index, index - 1)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("نقل لأسفل") },
                enabled = index < itemCount - 1,
                onClick = {
                    onMove(index, index + 1)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("نقل للأعلى تماماً") },
                enabled = index > 0,
                onClick = {
                    onMove(index, 0)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun TextItemRow(
    index: Int,
    itemCount: Int,
    item: String,
    onEditItem: (Int, String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    textStyle: TextStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DragHandleButton(index = index, itemCount = itemCount, onMove = onMove)
        OutlinedTextField(
            value = item,
            onValueChange = { newValue -> onEditItem(index, newValue) },
            modifier = Modifier.weight(1f),
            minLines = 1,
            maxLines = 3,
            textStyle = textStyle
        )
        IconButton(onClick = { onRemove(index) }) {
            Icon(Icons.Default.Close, contentDescription = "حذف العنصر")
        }
    }
}

@Composable
private fun SeparatorRow(
    index: Int,
    itemCount: Int,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DragHandleButton(index = index, itemCount = itemCount, onMove = onMove)
        Divider(modifier = Modifier.weight(1f))
        IconButton(onClick = { onRemove(index) }) {
            Icon(Icons.Default.Close, contentDescription = "حذف الفاصل")
        }
    }
}

@Composable
private fun DateMarkerRow(
    index: Int,
    itemCount: Int,
    date: LocalDate,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        DragHandleButton(index = index, itemCount = itemCount, onMove = onMove)
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = date.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        IconButton(onClick = { onRemove(index) }) {
            Icon(Icons.Default.Close, contentDescription = "حذف التاريخ")
        }
    }
}

// ---------------- Doctor dropdown ----------------

@Composable
internal fun DoctorDropdown(
    label: String,
    undefinedLabel: String = "غير محدد",
    selectedId: String?,
    doctors: List<Doctor>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = doctors.firstOrNull { it.id == selectedId }?.fullName
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedName?.let { "$label: $it" } ?: undefinedLabel,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(undefinedLabel) },
                onClick = { onSelected(null); expanded = false }
            )
            doctors.forEach { doctor ->
                DropdownMenuItem(
                    text = { Text(doctor.fullName) },
                    onClick = { onSelected(doctor.id); expanded = false }
                )
            }
        }
    }
}
