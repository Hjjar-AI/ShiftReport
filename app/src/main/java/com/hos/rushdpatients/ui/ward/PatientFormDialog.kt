package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.hos.rushdpatients.ui.components.LocalFieldEnabled
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.ui.theme.patientBadgeColors
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.data.model.PatientBadgePriority
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import com.hos.rushdpatients.ui.components.ConfirmDialog

private enum class PatientFormField {
    ADMISSION_NUMBER, ADMISSION_DATE, NAME, BIRTH_YEAR, AGE, DIAGNOSIS, TREATMENT, FOLLOW_UP, TEAM
}

private data class PatientFormError(val field: PatientFormField, val message: String)

@Composable
private fun autoDirTextStyle(): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(
        textDirection = TextDirection.ContentOrRtl
    )

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    var validationMessages by remember(initial) { mutableStateOf(emptyList<PatientFormError>()) }
    val fieldRequests = remember(initial) {
        PatientFormField.entries.associateWith { BringIntoViewRequester() }
    }

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
    val sectionOffsets = remember(initial) { mutableStateMapOf<PatientFormSection, Int>() }
    val sectionOrder = remember(initial == null) {
        if (initial == null) listOf(PatientFormSection.PERSONAL, PatientFormSection.CLINICAL,
            PatientFormSection.LABS, PatientFormSection.TASKS)
        else PatientFormSection.entries.toList()
    }
    val sectionThreshold = with(LocalDensity.current) { UiSpacing.medium.roundToPx() }
    val activeSection by remember(sectionOrder, sectionOffsets, sectionThreshold) {
        derivedStateOf {
            if (formScrollState.maxValue > 0 && formScrollState.value >= formScrollState.maxValue) {
                sectionOrder.last()
            } else sectionOrder.lastOrNull {
                (sectionOffsets[it] ?: Int.MAX_VALUE) <= formScrollState.value + sectionThreshold
            } ?: sectionOrder.first()
        }
    }
    fun sectionAnchor(section: PatientFormSection) = Modifier.onGloballyPositioned {
        sectionOffsets[section] = it.positionInParent().y.roundToInt()
    }
    // Compare the complete editable form, including text not yet added to clinical lists.
    val formValues = listOf(
        admittanceNumber, admittanceDate, gender, name, birthYearText, ageText,
        hasCompanion, diagnosisType, initialDiagnosis, treatmentItems, treatmentDraft,
        followUpItems, followUpDraft, labItems, labDraft, residentId, specialistId,
        badges, tasks, badgeDraftText, badgeDraftPriority, isPriority
    )
    val savedValues = remember(savedBaseline) {
        listOf(
            savedBaseline?.admittanceNumber.orEmpty(), savedBaseline?.admittanceDate ?: today,
            savedBaseline?.gender ?: Gender.fromCode(""), savedBaseline?.name.orEmpty(),
            savedBaseline?.birthDate?.year?.toString().orEmpty(), savedBaseline?.age?.toString().orEmpty(),
            savedBaseline?.hasCompanion ?: false, savedBaseline?.diagnosisType ?: DiagnosisType.fromCode(""),
            savedBaseline?.initialDiagnosis.orEmpty(), parseFieldItems(savedBaseline?.treatmentPlan), "",
            parseFieldItems(savedBaseline?.followUp), "", parseFieldItems(savedBaseline?.labs), "",
            savedBaseline?.responsibleResidentId, savedBaseline?.responsibleSpecialistId,
            savedBaseline?.badges.orEmpty(), savedBaseline?.tasks.orEmpty(), "", null,
            savedBaseline?.isPriority ?: false
        )
    }
    val hasUnsavedChanges = formValues != savedValues
    var confirmDiscard by remember(initial) { mutableStateOf(false) }
    val requestDismiss = {
        if (!saving) {
            if (hasUnsavedChanges) confirmDiscard = true else onDismiss()
        }
    }

    val admissionIdentityContent: @Composable () -> Unit = {
        SectionTitle(
            "بيانات الدخول",
            complete = admittanceNumber.isNotBlank() && admittanceDate != null,
            modifier = sectionAnchor(PatientFormSection.PERSONAL)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
            verticalAlignment = Alignment.Top
        ) {
            AppTextField(
                value = admittanceNumber,
                onValueChange = { admittanceNumber = it; validationMessages = emptyList() },
                label = { Text("رقم القبول الحالي *") },
                modifier = Modifier.weight(1f).bringIntoViewRequester(fieldRequests.getValue(PatientFormField.ADMISSION_NUMBER)),
                singleLine = true,
                textStyle = autoStyle,
                isError = validationMessages.any { it.field == PatientFormField.ADMISSION_NUMBER },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
            DateField(
                value = admittanceDate,
                onValueChange = { admittanceDate = it; validationMessages = emptyList() },
                label = "تاريخ الدخول *",
                isError = validationMessages.any { it.field == PatientFormField.ADMISSION_DATE },
                supportingText = admissionDays?.let { "مدة الإقامة: $it يوم" },
                modifier = Modifier.weight(1f).bringIntoViewRequester(fieldRequests.getValue(PatientFormField.ADMISSION_DATE))
            )
        }

        SectionTitle(
            "البيانات الشخصية",
            complete = name.isNotBlank() && birthYear != null && age != null
        )
        AppTextField(
            value = name,
            onValueChange = { name = it; validationMessages = emptyList() },
            label = { Text("اسم المريض *") },
            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(fieldRequests.getValue(PatientFormField.NAME)),
            singleLine = true,
            textStyle = autoStyle,
            isError = validationMessages.any { it.field == PatientFormField.NAME },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
        ) {
            AppTextField(
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
                modifier = Modifier.weight(1f).bringIntoViewRequester(fieldRequests.getValue(PatientFormField.BIRTH_YEAR)),
                singleLine = true,
                textStyle = autoStyle,
                isError = validationMessages.any { it.field == PatientFormField.BIRTH_YEAR },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
            Text("أو", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium)
            AppTextField(
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
                modifier = Modifier.weight(1f).bringIntoViewRequester(fieldRequests.getValue(PatientFormField.AGE)),
                singleLine = true,
                textStyle = autoStyle,
                isError = validationMessages.any { it.field == PatientFormField.AGE },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
        ) {
            Text("الجنس", style = MaterialTheme.typography.labelMedium)
            FilterChip(
                enabled = !saving,
                selected = gender == Gender.MALE,
                onClick = { gender = Gender.MALE },
                label = { Text("ذكر") }
            )
            FilterChip(
                enabled = !saving,
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
            Switch(enabled = !saving, checked = hasCompanion, onCheckedChange = { hasCompanion = it })
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

    CompositionLocalProvider(LocalFieldEnabled provides !saving) {
        WardFormDialog(
            onDismissRequest = requestDismiss,
            title = {
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    savedBaseline?.name?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                    reviewAction()
                    if (hasUnsavedChanges) {
                        Text(
                            "توجد تغييرات غير محفوظة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    PatientFormSectionNavigation(sectionOrder, activeSection, enabled = !saving) { section ->
                        sectionOffsets[section]?.let { offset ->
                            formScope.launch { formScrollState.animateScrollTo(offset.coerceIn(0, formScrollState.maxValue)) }
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(formScrollState),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                ) {
                    if (validationMessages.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(Modifier.padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                                Text("راجع الحقول التالية", style = MaterialTheme.typography.titleSmall)
                                validationMessages.forEach { error ->
                                    AppTextButton(
                                        onClick = {
                                            formScope.launch { fieldRequests.getValue(error.field).bringIntoView() }
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
                                    ) {
                                        Text(error.message, modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }
                    }
                    if (initial == null) admissionIdentityContent()

                    SectionTitle(
                        "التشخيص والعلاج",
                        complete = initialDiagnosis.isNotBlank() &&
                            (treatmentItems + treatmentDraft).any(String::isNotBlank) &&
                            (followUpItems + followUpDraft).any(String::isNotBlank),
                        modifier = sectionAnchor(PatientFormSection.CLINICAL)
                    )
                    Text("نوع التشخيص", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                        DiagnosisType.entries.forEach { type ->
                            FilterChip(
                                enabled = !saving,
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
                        isError = validationMessages.any { it.field == PatientFormField.DIAGNOSIS },
                        modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.DIAGNOSIS)),
                        placeholder = when (diagnosisType) {
                            DiagnosisType.PSYCHIATRIC -> "مثال: Psychosis، Bipolar II disorder"
                            DiagnosisType.ADDICTION -> "مثال: Drug-induced psychosis، Stimulant use disorder"
                            DiagnosisType.DUAL -> "مثال: Schizophrenia + Alcohol use disorder"
                        }
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        FilterChip(
                            enabled = !saving,
                            selected = formMode == PatientFormMode.GENERAL,
                            onClick = { formMode = PatientFormMode.GENERAL },
                            label = { Text("عام") }
                        )
                        FilterChip(
                            enabled = !saving,
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
                            isError = validationMessages.any { it.field == PatientFormField.TREATMENT },
                            modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.TREATMENT))
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
                            isError = validationMessages.any { it.field == PatientFormField.FOLLOW_UP },
                            modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.FOLLOW_UP))
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
                            isError = false,
                            modifier = sectionAnchor(PatientFormSection.LABS)
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
                        isError = validationMessages.any { it.field == PatientFormField.TREATMENT },
                        modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.TREATMENT))
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
                        isError = validationMessages.any { it.field == PatientFormField.FOLLOW_UP },
                        modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.FOLLOW_UP))
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
                        isError = false,
                        modifier = sectionAnchor(PatientFormSection.LABS)
                    )
                    }

                    PatientTaskEditor(tasks, doctors, !saving, modifier = sectionAnchor(PatientFormSection.TASKS)) { tasks = it }

                    SectionTitle(
                        "الفريق المسؤول",
                        complete = residentId != null && specialistId != null && residentId != specialistId
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .bringIntoViewRequester(fieldRequests.getValue(PatientFormField.TEAM)),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
                        verticalAlignment = Alignment.Top
                    ) {
                        DoctorDropdown(
                            label = "المقيم",
                            isError = validationMessages.any { it.field == PatientFormField.TEAM },
                            undefinedLabel = "مقيم غير محدد",
                            selectedId = residentId,
                            doctors = residentDoctors,
                            onSelected = { residentId = it; validationMessages = emptyList() },
                            modifier = Modifier.weight(1f)
                        )
                        DoctorDropdown(
                            label = "الاختصاصي",
                            isError = validationMessages.any { it.field == PatientFormField.TEAM },
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
                        Switch(enabled = !saving, checked = isPriority, onCheckedChange = { isPriority = it })
                    }
                    AppTextButton(onClick = { showBadgeEditor = !showBadgeEditor },
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
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = UiSpacing.small, top = UiSpacing.micro, bottom = UiSpacing.micro),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        buildString {
                                            append(badge.text)
                                            badge.priority?.let { append(" · ").append(it.arabicLabel) }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { badges = badges.filterIndexed { i, _ -> i != index } }, enabled = !saving) {
                                        Icon(Icons.Filled.Close, contentDescription = "حذف الشارة")
                                    }
                                }
                            }
                        }
                        AppTextField(
                            value = badgeDraftText,
                            onValueChange = { badgeDraftText = it.take(80) },
                            label = { Text("نص الشارة (اختياري)") },
                            supportingText = { Text("مثال: تحسس دوائي، يحتاج مراجعة، خطر سقوط") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = autoStyle
                        )
                        Text("مستوى الشارة (اختياري)", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                            FilterChip(
                                enabled = !saving,
                                selected = badgeDraftPriority == null,
                                onClick = { badgeDraftPriority = null },
                                label = { Text("بدون مستوى") }
                            )
                            PatientBadgePriority.entries.forEach { level ->
                                FilterChip(
                                    enabled = !saving,
                                    selected = badgeDraftPriority == level,
                                    onClick = { badgeDraftPriority = level },
                                    label = { Text(level.arabicLabel) }
                                )
                            }
                        }
                        AppOutlinedButton(
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
                AppButton(modifier = Modifier.fillMaxWidth(), enabled = !saving, onClick = {
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
                        if (admittanceNumber.isBlank()) add(PatientFormError(PatientFormField.ADMISSION_NUMBER, "رقم القبول الحالي مطلوب"))
                        if (admittanceDate == null) add(PatientFormError(PatientFormField.ADMISSION_DATE, "اختر تاريخ الدخول"))
                        if (admittanceDate?.isAfter(today) == true) {
                            add(PatientFormError(PatientFormField.ADMISSION_DATE, "تاريخ الدخول لا يمكن أن يكون في المستقبل"))
                        }
                        if (name.isBlank()) add(PatientFormError(PatientFormField.NAME, "اسم المريض مطلوب"))
                        if (ageText.toIntOrNull()?.let { it !in 0..120 } == true) {
                            add(PatientFormError(PatientFormField.AGE, "العمر يجب أن يكون بين 0 و120 سنة"))
                        }
                        if (birthYear == null) add(PatientFormError(PatientFormField.BIRTH_YEAR, "أدخل سنة الميلاد أو العمر"))
                        if (birthYear != null && birthYear > currentYear) {
                            add(PatientFormError(PatientFormField.BIRTH_YEAR, "سنة الميلاد لا يمكن أن تكون في المستقبل"))
                        }
                        if (birthYear != null && age == null) add(PatientFormError(PatientFormField.BIRTH_YEAR, "سنة الميلاد تنتج عمراً غير صحيح"))
                        if (initialDiagnosis.isBlank()) add(PatientFormError(PatientFormField.DIAGNOSIS, "التشخيص الأولي مطلوب"))
                        if (resolvedTreatments.none { it != FIELD_SEPARATOR && it.isNotBlank() }) {
                            add(PatientFormError(PatientFormField.TREATMENT, "الخطة العلاجية مطلوبة"))
                        }
                        if (resolvedFollowUp.none { it != FIELD_SEPARATOR && it.isNotBlank() }) {
                            add(PatientFormError(PatientFormField.FOLLOW_UP, "المتابعة مطلوبة"))
                        }
                        if (residentId != null && residentId == specialistId) {
                            add(PatientFormError(PatientFormField.TEAM, "يجب اختيار طبيبين مختلفين للمقيم والاختصاصي"))
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
                AppTextButton(onClick = requestDismiss, enabled = !saving) { Text("إلغاء") }
            }
        )
    }
    if (confirmDiscard && !saving) {
        ConfirmDialog(
            title = "تجاهل التغييرات؟",
            message = "توجد تغييرات غير محفوظة. هل تريد تجاهلها وإغلاق النموذج؟",
            confirmText = "تجاهل التغييرات",
            dismissText = "متابعة التعديل",
            onConfirm = {
                confirmDiscard = false
                if (initial == null) onDraftChanged(PatientDraft())
                onDismiss()
            },
            onDismiss = { confirmDiscard = false }
        )
    }
}

private enum class PatientFormMode { GENERAL, ADVANCED }
