package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppFieldPair
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.hos.rushdpatients.ui.components.LocalFieldEnabled
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.ui.theme.UiSpacing
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
    draftWarning: String? = null,
    reviewAction: @Composable () -> Unit = {},
    entryOptions: @Composable () -> Unit = {},
    entryOptionsSummary: String? = null,
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
    var treatmentText by formState::treatmentText
    var followUpText by formState::followUpText
    var labText by formState::labText
    var residentId by formState::residentId
    var specialistId by formState::specialistId
    val today = LocalDate.now()
    val currentYear = today.year
    var validationMessages by remember(initial) { mutableStateOf(emptyList<PatientFormError>()) }
    fun clearFieldError(vararg fields: PatientFormField) {
        validationMessages = validationMessages.filterNot { it.field in fields }
    }
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
    val scrollFormTo = com.hos.rushdpatients.ui.components.rememberUiScrollTo(formScrollState)
    val formScope = rememberCoroutineScope()
    val taskEntry = formState.taskEntry
    val resolvedTasks = tasks + listOfNotNull(taskEntry?.takeIf { it.description.isNotBlank() })
    // Compare the complete editable form, including the inline new task.
    val formValues = listOf(
        admittanceNumber, admittanceDate, gender, name, birthYearText, ageText,
        hasCompanion, diagnosisType, initialDiagnosis, treatmentText,
        followUpText, labText, residentId, specialistId,
        badges, resolvedTasks, badgeDraftText, badgeDraftPriority, isPriority
    )
    val savedValues = remember(savedBaseline) {
        listOf(
            savedBaseline?.admittanceNumber.orEmpty(), savedBaseline?.admittanceDate ?: today,
            savedBaseline?.gender ?: Gender.fromCode(""), savedBaseline?.name.orEmpty(),
            savedBaseline?.birthDate?.year?.toString().orEmpty(), savedBaseline?.age?.toString().orEmpty(),
            savedBaseline?.hasCompanion ?: false, savedBaseline?.diagnosisType ?: DiagnosisType.fromCode(""),
            savedBaseline?.initialDiagnosis.orEmpty(), savedBaseline?.treatmentPlan.orEmpty(),
            savedBaseline?.followUp.orEmpty(), savedBaseline?.labs.orEmpty(),
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
            ageText, birthYearText, currentYear, gender, hasCompanion, saving,
            autoStyle, validationMessages, fieldRequests,
            onAdmissionNumberChanged = { admittanceNumber = it; clearFieldError(PatientFormField.ADMISSION_NUMBER) },
            onAdmissionDateChanged = { admittanceDate = it; clearFieldError(PatientFormField.ADMISSION_DATE) },
            onNameChanged = { name = it; clearFieldError(PatientFormField.NAME) },
            onAgeAndYearChanged = { nextAge, nextYear ->
                ageText = nextAge; birthYearText = nextYear; clearFieldError(PatientFormField.AGE, PatientFormField.BIRTH_YEAR)
            }, onGenderChanged = { gender = it }, onCompanionChanged = { hasCompanion = it })
    }

    val teamFieldsContent: @Composable () -> Unit = {
        PatientFormSection {
            AppSectionHeader(
                uiText(R.string.ui_patient_form_dialog_014)
            )
            Column(
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(fieldRequests.getValue(PatientFormField.TEAM)),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                DoctorDropdown(
                    label = uiText(R.string.ui_patient_form_dialog_015),
                    isError = validationMessages.any { it.field == PatientFormField.TEAM },
                    errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.TEAM }?.message,
                    undefinedLabel = uiText(R.string.ui_patient_form_dialog_016),
                    selectedId = residentId,
                    doctors = residentDoctors, registry = doctors,
                    onSelected = { residentId = it; clearFieldError(PatientFormField.TEAM) },
                    modifier = Modifier.fillMaxWidth()
                )
                DoctorDropdown(
                    label = uiText(R.string.ui_patient_form_dialog_017),
                    isError = validationMessages.any { it.field == PatientFormField.TEAM },
                    errorMessage = null,
                    undefinedLabel = uiText(R.string.ui_patient_form_dialog_018),
                    selectedId = specialistId,
                    doctors = specialistDoctors, registry = doctors,
                    onSelected = { specialistId = it; clearFieldError(PatientFormField.TEAM) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(uiText(R.string.ui_patient_form_dialog_019),
                style = MaterialTheme.typography.bodySmall)
            if (com.hos.rushdpatients.domain.doctor.DoctorTeams.isCrossTeamCover(
                    activeDoctors.firstOrNull { it.id == residentId }, specialistId)) {
                Text(uiText(R.string.ui_patient_form_dialog_020), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }

    val optionalFieldsContent: @Composable () -> Unit = {
        PatientOptionalFields(formState, saving, autoStyle,
            entryOptionsSummary = entryOptionsSummary, entryOptions = entryOptions)
    }

    LaunchedEffect(
        admittanceNumber, admittanceDate, gender, name, birthYearText, ageText,
        hasCompanion, diagnosisType, initialDiagnosis, treatmentText,
        followUpText, labText, residentId, specialistId,
        badges, badgeDraftText, badgeDraftPriority, isPriority, tasks, taskEntry
    ) {
        run {
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
                    treatmentItems = emptyList(),
                    treatmentDraft = treatmentText,
                    followUpItems = emptyList(),
                    followUpDraft = followUpText,
                    labItems = emptyList(),
                    labDraft = labText,
                    residentId = residentId,
                    specialistId = specialistId,
                    tasks = resolvedTasks,
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
                            fontWeight = com.hos.rushdpatients.ui.theme.UiTypography.emphasizedWeight)
                    }
                    reviewAction()
                    if (hasUnsavedChanges) {
                        Text(
                            uiText(R.string.ui_patient_form_dialog_002),
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
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                ) {
                    draftWarning?.let { warning ->
                        com.hos.rushdpatients.ui.components.AppNotice(warning,
                            kind = com.hos.rushdpatients.ui.components.NoticeKind.WARNING)
                    }
                    if (validationMessages.isNotEmpty()) {
                        com.hos.rushdpatients.ui.components.AppNotice(
                            message = uiText(R.string.ui_patient_form_dialog_003),
                            kind = com.hos.rushdpatients.ui.components.NoticeKind.ERROR,
                            content = {
                                validationMessages.forEach { error ->
                                    AppTextButton(onClick = {
                                        formScope.launch { fieldRequests.getValue(error.field).bringIntoView() }
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text(error.message, modifier = Modifier.fillMaxWidth(),
                                            color = com.hos.rushdpatients.ui.theme.LocalClinicalColors.current.onUrgentContainer)
                                    }
                                }
                            })
                    }
                    if (initial == null) {
                        admissionIdentityContent()
                        teamFieldsContent()
                    }

                    PatientFormSection {
                        AppSectionHeader(
                            uiText(R.string.ui_patient_form_dialog_004)
                        )
                        Text(uiText(R.string.ui_patient_form_dialog_005), style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                            DiagnosisType.entries.forEach { type ->
                                AppFilterChip(
                                    enabled = !saving,
                                    selected = diagnosisType == type,
                                    onClick = { diagnosisType = type },
                                    label = { Text(type.uiLabel(uiText)) }
                                )
                            }
                        }
                        MultilineField(
                            initialDiagnosis,
                            { initialDiagnosis = it },
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
                    }
                    PatientFormSection {
                        AppSectionHeader(uiText(R.string.ui_patient_form_dialog_010))
                        MultilineField(
                            value = treatmentText, onValueChange = { treatmentText = it },
                            label = uiText(R.string.ui_patient_form_dialog_011), textStyle = autoStyle,
                            isError = validationMessages.any { it.field == PatientFormField.TREATMENT },
                            errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.TREATMENT }?.message,
                            modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.TREATMENT))
                        )
                    }
                    PatientFormSection {
                        MultilineField(
                            value = followUpText, onValueChange = { followUpText = it },
                            label = uiText(R.string.ui_patient_form_dialog_012), textStyle = autoStyle,
                            isError = validationMessages.any { it.field == PatientFormField.FOLLOW_UP },
                            errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.FOLLOW_UP }?.message,
                            modifier = Modifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.FOLLOW_UP))
                        )
                    }
                    PatientFormSection {
                        MultilineField(
                            value = labText, onValueChange = { labText = it },
                            label = uiText(R.string.ui_labs), textStyle = autoStyle
                        )
                    }
                    PatientFormSection {
                        optionalFieldsContent()
                    }
                    if (initial != null) {
                        teamFieldsContent()
                        admissionIdentityContent()
                    }
                }
            },
            confirmButton = {
                val submit: (Boolean) -> Unit = submit@{ advance ->
                    val treatmentPlan = treatmentText
                    val followUp = followUpText
                    val labs = labText

                    val errors = validatePatientForm(uiText, admittanceDate, today,
                        ageText, birthYear, currentYear, age, residentId, specialistId)
                    if (errors.isNotEmpty()) {
                        validationMessages = errors
                        formScope.launch { scrollFormTo(0) }
                        return@submit
                    }

                    val resolvedBirthDate = birthYear?.let { LocalDate.of(it, 1, 1) }

                    val resolvedBadges = badges + badgeDraftText.trim()
                        .takeIf(String::isNotBlank)
                        ?.let { listOf(PatientBadge(it, badgeDraftPriority)) }
                        .orEmpty()

                    val patient = patientForSubmission(initial, admittanceNumber, admittanceDate,
                        gender, name, resolvedBirthDate, hasCompanion, diagnosisType, initialDiagnosis,
                        treatmentPlan, followUp, labs, residentId, specialistId, resolvedBadges, resolvedTasks, isPriority)
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
