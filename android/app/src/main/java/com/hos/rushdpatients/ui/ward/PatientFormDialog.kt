package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.AppIconButton
import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppFieldPair
import com.hos.rushdpatients.ui.components.AppToggleRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.hos.rushdpatients.ui.components.LocalFieldEnabled
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
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
    stateStore: PatientFormStateStore? = null,
    doctors: List<Doctor>,
    saving: Boolean,
    reviewAction: @Composable () -> Unit = {},
    entryOptions: @Composable () -> Unit = {},
    onDraftChanged: (PatientDraft) -> Unit = {},
    onConfirm: (Patient) -> Unit,
    onConfirmAndNext: ((Patient) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    val formState = remember(initial) { stateStore?.obtain(initial, restoredDraft) ?: PatientFormState(initial, restoredDraft) }
    var tasks by formState::tasks
    var admittanceNumber by formState::admittanceNumber
    var admittanceDate by formState::admittanceDate
    var gender by formState::gender
    var name by formState::name
    var birthYearText by formState::birthYearText
    var ageText by formState::ageText
    var hasCompanion by formState::hasCompanion
    var isPriority by formState::isPriority
    var badges by formState::badges
    var badgeDraftText by formState::badgeDraftText
    var badgeDraftPriority by formState::badgeDraftPriority
    var diagnosisType by formState::diagnosisType
    var initialDiagnosis by formState::initialDiagnosis
    var treatmentItems by formState::treatmentItems
    var treatmentDraft by formState::treatmentDraft
    var followUpItems by formState::followUpItems
    var followUpDraft by formState::followUpDraft
    var labItems by formState::labItems
    var labDraft by formState::labDraft
    var residentId by formState::residentId
    var specialistId by formState::specialistId
    var showBadgeEditor by remember(initial) { mutableStateOf(false) }
    val today = LocalDate.now()
    val currentYear = today.year
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
    val activeSection = com.hos.rushdpatients.ui.components.activeAppSection(sectionOrder, sectionOffsets, formScrollState)
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
        PatientDemographicFields(admittanceNumber, admittanceDate, admissionDays, name,
            ageText, birthYearText, birthYear, age, currentYear, gender, hasCompanion, saving,
            autoStyle, validationMessages, fieldRequests, sectionAnchor(PatientFormSection.PERSONAL),
            onAdmissionNumberChanged = { admittanceNumber = it; validationMessages = emptyList() },
            onAdmissionDateChanged = { admittanceDate = it; validationMessages = emptyList() },
            onNameChanged = { name = it; validationMessages = emptyList() },
            onAgeAndYearChanged = { nextAge, nextYear ->
                ageText = nextAge; birthYearText = nextYear; validationMessages = emptyList()
            }, onGenderChanged = { gender = it }, onCompanionChanged = { hasCompanion = it })
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
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    val patientHeading = savedBaseline?.name?.takeIf(String::isNotBlank)
                        ?: name.takeIf(String::isNotBlank)
                    patientHeading?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Text(uiText(R.string.ui_patient_form_dialog_001, activeSection.label(uiText)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    reviewAction()
                    if (hasUnsavedChanges) {
                        Text(
                            uiText(R.string.ui_patient_form_dialog_002),
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
                                Text(uiText(R.string.ui_patient_form_dialog_003), style = MaterialTheme.typography.titleSmall)
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
                    entryOptions()

                    AppSectionHeader(
                        uiText(R.string.ui_patient_form_dialog_004),
                        complete = initialDiagnosis.isNotBlank() &&
                            (treatmentItems + treatmentDraft).any(String::isNotBlank) &&
                            (followUpItems + followUpDraft).any(String::isNotBlank),
                        modifier = sectionAnchor(PatientFormSection.CLINICAL)
                    )
                    Text(uiText(R.string.ui_patient_form_dialog_005), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                        DiagnosisType.entries.forEach { type ->
                            FilterChip(
                                enabled = !saving,
                                selected = diagnosisType == type,
                                onClick = { diagnosisType = type },
                                label = { Text(type.uiLabel(uiText)) }
                            )
                        }
                    }
                    MultilineField(
                        initialDiagnosis,
                        { initialDiagnosis = it; validationMessages = emptyList() },
                        uiText(R.string.ui_patient_form_dialog_006),
                        autoStyle,
                        isError = validationMessages.any { it.field == PatientFormField.DIAGNOSIS },
                        errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.DIAGNOSIS }?.message,
                        modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.DIAGNOSIS)),
                        placeholder = when (diagnosisType) {
                            DiagnosisType.PSYCHIATRIC -> uiText(R.string.ui_patient_form_dialog_007)
                            DiagnosisType.ADDICTION -> uiText(R.string.ui_patient_form_dialog_008)
                            DiagnosisType.DUAL -> uiText(R.string.ui_patient_form_dialog_009)
                        }
                    )

                    AppSectionHeader(uiText(R.string.ui_patient_form_dialog_010))
                    MultiValueEditor(
                        label = uiText(R.string.ui_patient_form_dialog_011),
                        items = treatmentItems,
                        draft = treatmentDraft,
                        onDraftChange = { treatmentDraft = it; validationMessages = emptyList() },
                        onReplaceAll = { treatmentItems = parseFieldItems(it); treatmentDraft = ""; validationMessages = emptyList() },
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
                        errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.TREATMENT }?.message,
                        modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.TREATMENT))
                    )
                    MultiValueEditor(
                        label = uiText(R.string.ui_patient_form_dialog_012),
                        items = followUpItems,
                        draft = followUpDraft,
                        onDraftChange = { followUpDraft = it; validationMessages = emptyList() },
                        onReplaceAll = { followUpItems = parseFieldItems(it); followUpDraft = ""; validationMessages = emptyList() },
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
                        errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.FOLLOW_UP }?.message,
                        modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.FOLLOW_UP))
                    )
                    MultiValueEditor(
                        label = uiText(R.string.ui_labs),
                        items = labItems,
                        draft = labDraft,
                        onDraftChange = { labDraft = it; validationMessages = emptyList() },
                        onReplaceAll = { labItems = parseFieldItems(it); labDraft = ""; validationMessages = emptyList() },
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

                    PatientTaskEditor(tasks, doctors, !saving, formState = formState, modifier = sectionAnchor(PatientFormSection.TASKS)) { tasks = it }

                    AppSectionHeader(
                        uiText(R.string.ui_patient_form_dialog_014),
                        complete = residentId != null && specialistId != null && residentId != specialistId
                    )
                    AppFieldPair(
                        stackWhenNarrow = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(fieldRequests.getValue(PatientFormField.TEAM)),
                        first = { fieldModifier ->
                            DoctorDropdown(
                                label = uiText(R.string.ui_patient_form_dialog_015),
                                isError = validationMessages.any { it.field == PatientFormField.TEAM },
                                errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.TEAM }?.message,
                                undefinedLabel = uiText(R.string.ui_patient_form_dialog_016),
                                selectedId = residentId,
                                doctors = residentDoctors, registry = doctors,
                                onSelected = { residentId = it; validationMessages = emptyList() },
                                modifier = fieldModifier
                            )
                        },
                        second = { fieldModifier ->
                            DoctorDropdown(
                                label = uiText(R.string.ui_patient_form_dialog_017),
                                isError = validationMessages.any { it.field == PatientFormField.TEAM },
                                errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.TEAM }?.message,
                                undefinedLabel = uiText(R.string.ui_patient_form_dialog_018),
                                selectedId = specialistId,
                                doctors = specialistDoctors, registry = doctors,
                                onSelected = { specialistId = it; validationMessages = emptyList() },
                                modifier = fieldModifier
                            )
                        }
                    )

                    Text(uiText(R.string.ui_patient_form_dialog_019),
                        style = MaterialTheme.typography.bodySmall)
                    if (com.hos.rushdpatients.domain.doctor.DoctorTeams.isCrossTeamCover(
                            activeDoctors.firstOrNull { it.id == residentId }, specialistId)) {
                        Text(uiText(R.string.ui_patient_form_dialog_020), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary)
                    }

                    AppSectionHeader(uiText(R.string.ui_patient_form_dialog_021))
                    AppToggleRow(title = uiText(R.string.ui_patient_form_dialog_022), checked = isPriority,
                        onCheckedChange = { isPriority = it }, enabled = !saving)
                    AppTextButton(onClick = { showBadgeEditor = !showBadgeEditor },
                        modifier = Modifier.semantics { stateDescription = if (showBadgeEditor) uiText(R.string.ui_patient_form_dialog_023) else uiText(R.string.ui_patient_form_dialog_024) }) {
                        Text(if (showBadgeEditor) uiText(R.string.ui_patient_form_dialog_025) else uiText(R.string.ui_patient_form_dialog_026, uiNumber(badges.size)))
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
                                            badge.priority?.let { append(" · ").append(it.uiLabel(uiText)) }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppIconButton(label = uiText(R.string.ui_patient_form_dialog_027),
            icon = Icons.Filled.Close,
            onClick = { badges = badges.filterIndexed { i, _ -> i != index } },
            enabled = !saving)
                                }
                            }
                        }
                        AppTextField(
                            value = badgeDraftText,
                            onValueChange = { badgeDraftText = it.take(80) },
                            label = { Text(uiText(R.string.ui_patient_form_dialog_028)) },
                            supportingText = { Text(uiText(R.string.ui_patient_form_dialog_029)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = autoStyle
                        )
                        Text(uiText(R.string.ui_patient_form_dialog_030), style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                            FilterChip(
                                enabled = !saving,
                                selected = badgeDraftPriority == null,
                                onClick = { badgeDraftPriority = null },
                                label = { Text(uiText(R.string.ui_patient_form_dialog_031)) }
                            )
                            PatientBadgePriority.entries.forEach { level ->
                                FilterChip(
                                    enabled = !saving,
                                    selected = badgeDraftPriority == level,
                                    onClick = { badgeDraftPriority = level },
                                    label = { Text(level.uiLabel(uiText)) }
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
                            enabled = !saving && badgeDraftText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(uiText(R.string.ui_patient_form_dialog_032)) }

                    }

                    if (initial != null) admissionIdentityContent()
                }
            },
            confirmButton = {
                val submit: (Boolean) -> Unit = submit@{ advance ->
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

                    val errors = validatePatientForm(uiText, admittanceNumber, admittanceDate, today, name,
                        ageText, birthYear, currentYear, age, initialDiagnosis, resolvedTreatments,
                        resolvedFollowUp, residentId, specialistId)
                    if (errors.isNotEmpty()) {
                        validationMessages = errors
                        formScope.launch { formScrollState.animateScrollTo(0) }
                        return@submit
                    }

                    val resolvedBirthDate = birthYear?.let { LocalDate.of(it, 1, 1) }

                    val resolvedBadges = badges + badgeDraftText.trim()
                        .takeIf(String::isNotBlank)
                        ?.let { listOf(PatientBadge(it, badgeDraftPriority)) }
                        .orEmpty()

                    val patient = patientForSubmission(initial, admittanceNumber, admittanceDate,
                        gender, name, resolvedBirthDate, hasCompanion, diagnosisType, initialDiagnosis,
                        treatmentPlan, followUp, labs, residentId, specialistId, resolvedBadges, tasks, isPriority)
                    if (advance) onConfirmAndNext?.invoke(patient) else onConfirm(patient)
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                    Text(if (saving) uiText(R.string.ui_patient_form_dialog_033) else if (hasUnsavedChanges)
                        uiText(R.string.ui_patient_form_dialog_034) else uiText(R.string.ui_patient_form_dialog_035),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnsavedChanges) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (onConfirmAndNext != null) {
                        AppFieldPair(first = { buttonModifier ->
                            AppButton(modifier = buttonModifier, enabled = !saving,
                                onClick = { submit(false) }) { Text(if (saving) uiText(R.string.ui_patient_form_dialog_036) else uiText(R.string.ui_save)) }
                        }, second = { buttonModifier ->
                            AppOutlinedButton(modifier = buttonModifier, enabled = !saving,
                                onClick = { submit(true) }) { Text(uiText(R.string.ui_patient_form_dialog_038)) }
                        })
                    } else AppButton(modifier = Modifier.fillMaxWidth(), enabled = !saving,
                        onClick = { submit(false) }) { Text(if (saving) uiText(R.string.ui_patient_form_dialog_036) else uiText(R.string.ui_save)) }
                }
            },
            dismissButton = {
                AppTextButton(onClick = requestDismiss, enabled = !saving) { Text(uiText(R.string.ui_cancel)) }
            }
        )
    }
    if (confirmDiscard && !saving) {
        ConfirmDialog(
            title = uiText(R.string.ui_patient_form_dialog_039),
            message = uiText(R.string.ui_patient_form_dialog_040),
            confirmText = uiText(R.string.ui_patient_form_dialog_041),
            destructive = true,
            dismissText = uiText(R.string.ui_patient_form_dialog_042),
            onConfirm = {
                confirmDiscard = false
                if (initial == null) onDraftChanged(PatientDraft())
                onDismiss()
            },
            onDismiss = { confirmDiscard = false }
        )
    }
}
