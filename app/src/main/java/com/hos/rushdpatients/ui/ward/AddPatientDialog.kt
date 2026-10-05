package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientWarningFlag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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
    restoredDraft: PatientDraft? = null,
    doctors: List<Doctor>,
    saving: Boolean,
    onDraftChanged: (PatientDraft) -> Unit = {},
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    var formMode by remember(initial) { mutableStateOf(PatientFormMode.GENERAL) }
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
    var warningFlags by remember(initial) {
        mutableStateOf(
            initial?.warningFlags ?: restoredDraft?.warningFlags
                ?.mapNotNull(PatientWarningFlag::fromCode)
                ?.toSet().orEmpty()
        )
    }
    var warningDetails by remember(initial, restoredDraft) {
        mutableStateOf(
            initial?.warningDetails ?: restoredDraft?.warningDetails.orEmpty()
                .mapNotNull { (code, detail) -> PatientWarningFlag.fromCode(code)?.let { it to detail } }
                .toMap()
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
    var validationMessage by remember(initial) { mutableStateOf<String?>(null) }

    val birthYear = birthYearText.trim().toIntOrNull()
    val age = birthYear?.let { (currentYear - it).takeIf { a -> a in 0..120 } }
    val admissionDays = admittanceDate?.takeUnless { it.isAfter(today) }
        ?.let { ChronoUnit.DAYS.between(it, today).toInt() }
    val activeDoctors = doctors.filterNot { it.isDeleted }
    val residentDoctors = activeDoctors.filter { it.clinicalRole.canBeResident() }
    val specialistDoctors = activeDoctors.filter { it.clinicalRole.canBeSupervisor() }
    val autoStyle = autoDirTextStyle()
    val compactWindow = LocalConfiguration.current.screenWidthDp < 600

    LaunchedEffect(
        admittanceNumber, admittanceDate, gender, name, birthYearText, ageText,
        hasCompanion, diagnosisType, initialDiagnosis, treatmentItems, treatmentDraft,
        followUpItems, followUpDraft, labItems, labDraft, residentId, specialistId,
        warningFlags, warningDetails, isPriority
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
                    warningFlags = warningFlags.mapTo(mutableSetOf()) { it.code },
                    warningDetails = warningDetails.mapKeys { it.key.code },
                    isPriority = isPriority
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = if (compactWindow) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxHeight()
                .width(600.dp)
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (compactWindow) 760.dp else 900.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                Text(
                    if (formMode == PatientFormMode.GENERAL) {
                        "الحقول الأساسية لتسليم المناوبة بسرعة. البيانات المتقدمة محفوظة ويمكن فتحها عند الحاجة."
                    } else {
                        "تفاصيل التحذيرات والعناصر القابلة للترتيب والتحاليل."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SectionTitle("بيانات الدخول")
                OutlinedTextField(
                    value = admittanceNumber,
                    onValueChange = { admittanceNumber = it; validationMessage = null },
                    label = { Text("رقم الدخول *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = autoStyle
                )
                DateField(
                    value = admittanceDate,
                    onValueChange = { admittanceDate = it; validationMessage = null },
                    label = "تاريخ الدخول *",
                    supportingText = admissionDays?.let { "مدة الإقامة: $it يوم" }
                )

                SectionTitle("البيانات الشخصية")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationMessage = null },
                    label = { Text("اسم المريض *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = autoStyle
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
                            validationMessage = null
                        },
                        label = { Text("سنة الميلاد *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = autoStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
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
                            validationMessage = null
                        },
                        label = { Text("العمر *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = autoStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                    Switch(
                        checked = hasCompanion,
                        onCheckedChange = { hasCompanion = it }
                    )
                }

                SectionTitle("تنبيهات سريعة")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تثبيت كمريض ذي أولوية", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = isPriority, onCheckedChange = { isPriority = it })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PatientWarningFlag.entries.forEach { flag ->
                        FilterChip(
                            selected = flag in warningFlags,
                            onClick = {
                                warningFlags = if (flag in warningFlags) {
                                    warningFlags - flag
                                } else {
                                    warningFlags + flag
                                }
                            },
                            label = { Text(flag.arabicLabel) }
                        )
                    }
                }
                if (formMode == PatientFormMode.ADVANCED) {
                    warningFlags.sortedBy { it.ordinal }.forEach { flag ->
                        OutlinedTextField(
                            value = warningDetails[flag].orEmpty(),
                            onValueChange = { value ->
                                warningDetails = warningDetails + (flag to value)
                            },
                            label = { Text("تفاصيل ${flag.arabicLabel} (اختياري)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                }

                SectionTitle("التشخيص والعلاج")
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
                MultilineField(initialDiagnosis, { initialDiagnosis = it }, "التشخيص الأولي", autoStyle)

                if (formMode == PatientFormMode.GENERAL) {
                    MultilineField(
                        value = (treatmentItems + treatmentDraft)
                            .filter(String::isNotBlank)
                            .joinToString("\n"),
                        onValueChange = {
                            treatmentItems = parseFieldItems(it)
                            treatmentDraft = ""
                            validationMessage = null
                        },
                        label = "الخطة العلاجية",
                        textStyle = autoStyle
                    )
                    MultilineField(
                        value = (followUpItems + followUpDraft)
                            .filter(String::isNotBlank)
                            .joinToString("\n"),
                        onValueChange = {
                            followUpItems = parseFieldItems(it)
                            followUpDraft = ""
                            validationMessage = null
                        },
                        label = "المتابعة وتسليم المهام",
                        textStyle = autoStyle
                    )
                } else {
                MultiValueEditor(
                    label = "الخطة العلاجية",
                    items = treatmentItems,
                    draft = treatmentDraft,
                    onDraftChange = { treatmentDraft = it; validationMessage = null },
                    onEditItem = { index, value ->
                        treatmentItems = treatmentItems.toMutableList().also { it[index] = value }
                        validationMessage = null
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
                    textStyle = autoStyle
                )
                MultiValueEditor(
                    label = "المتابعة",
                    items = followUpItems,
                    draft = followUpDraft,
                    onDraftChange = { followUpDraft = it; validationMessage = null },
                    onEditItem = { index, value ->
                        followUpItems = followUpItems.toMutableList().also { it[index] = value }
                        validationMessage = null
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
                    textStyle = autoStyle
                )
                OutlinedButton(
                    onClick = {
                        val task = followUpDraft.trim().removePrefix("☐").trim()
                        if (task.isNotEmpty()) {
                            followUpItems = followUpItems + "☐ $task"
                            followUpDraft = ""
                        } else {
                            followUpDraft = "☐ "
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إضافة كمهمة اختيارية") }
                MultiValueEditor(
                    label = "التحاليل",
                    items = labItems,
                    draft = labDraft,
                    onDraftChange = { labDraft = it; validationMessage = null },
                    onEditItem = { index, value ->
                        labItems = labItems.toMutableList().also { it[index] = value }
                        validationMessage = null
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
                    textStyle = autoStyle
                )
                }

                SectionTitle("الفريق المسؤول")
                DoctorDropdown(
                    label = "المقيم المسؤول (اختياري)",
                    undefinedLabel = "مقيم غير محدد",
                    selectedId = residentId,
                    doctors = residentDoctors,
                    onSelected = { residentId = it; validationMessage = null }
                )
                DoctorDropdown(
                    label = "الاختصاصي المسؤول (اختياري)",
                    undefinedLabel = "اختصاصي غير محدد",
                    selectedId = specialistId,
                    doctors = specialistDoctors,
                    onSelected = { specialistId = it; validationMessage = null }
                )

                validationMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
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

                val message = when {
                    admittanceNumber.isBlank() -> "رقم الدخول مطلوب"
                    admittanceDate == null -> "اختر تاريخ الدخول"
                    admittanceDate?.isAfter(today) == true ->
                        "تاريخ الدخول لا يمكن أن يكون في المستقبل"
                    name.isBlank() -> "اسم المريض مطلوب"
                    ageText.toIntOrNull()?.let { it !in 0..120 } == true ->
                        "العمر يجب أن يكون بين 0 و120 سنة"
                    birthYear == null -> "أدخل سنة الميلاد أو العمر"
                    birthYear > currentYear ->
                        "سنة الميلاد لا يمكن أن تكون في المستقبل"
                    age == null -> "سنة الميلاد تنتج عمراً غير صحيح"
                    initialDiagnosis.isBlank() -> "التشخيص الأولي مطلوب"
                    resolvedTreatments.none { it != FIELD_SEPARATOR && it.isNotBlank() } ->
                        "الخطة العلاجية مطلوبة"
                    resolvedFollowUp.none { it != FIELD_SEPARATOR && it.isNotBlank() } ->
                        "المتابعة مطلوبة"
                    residentId != null && residentId == specialistId ->
                        "يجب اختيار طبيبين مختلفين للمقيم والاختصاصي"
                    else -> null
                }
                if (message != null) {
                    validationMessage = message
                    return@TextButton
                }

                val resolvedBirthDate = birthYear?.let { LocalDate.of(it, 1, 1) }

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
                    warningFlags = warningFlags,
                    warningDetails = warningDetails.filterKeys { it in warningFlags },
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
                    warningFlags = warningFlags,
                    warningDetails = warningDetails.filterKeys { it in warningFlags },
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 0.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    supportingText: String?
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth()
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
    textStyle: TextStyle
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 3,
        textStyle = textStyle
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
    textStyle: TextStyle
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
            textStyle = textStyle
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DragHandleButton(
    index: Int,
    itemCount: Int,
    onMove: (Int, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { expanded = true }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "إعادة الترتيب (اضغط مطولاً)",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun DoctorDropdown(
    label: String,
    undefinedLabel: String = "غير محدد",
    selectedId: String?,
    doctors: List<Doctor>,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = doctors.firstOrNull { it.id == selectedId }?.fullName
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedName?.let { "$label: $it" } ?: undefinedLabel,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
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
