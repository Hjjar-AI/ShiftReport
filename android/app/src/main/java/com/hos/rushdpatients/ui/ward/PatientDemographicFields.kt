package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppFieldPair
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.AppTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.UiLayout
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PatientDemographicFields(
    admittanceNumber: String, admittanceDate: LocalDate?, admissionDays: Int?, name: String,
    ageText: String, birthYearText: String, currentYear: Int,
    gender: Gender, hasCompanion: Boolean, saving: Boolean, autoStyle: TextStyle,
    validationMessages: List<PatientFormError>, fieldRequests: Map<PatientFormField, BringIntoViewRequester>,
    onAdmissionNumberChanged: (String) -> Unit,
    onAdmissionDateChanged: (LocalDate?) -> Unit, onNameChanged: (String) -> Unit,
    onAgeAndYearChanged: (String, String) -> Unit, onGenderChanged: (Gender) -> Unit,
    onCompanionChanged: (Boolean) -> Unit
) {
    val uiText = rememberUiStrings()
    PatientFormSection {
        AppSectionHeader(
            uiText(R.string.ui_patient_demographic_fields_005)
        )
        AppTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text(uiText(R.string.ui_patient_demographic_fields_006)) },
            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(fieldRequests.getValue(PatientFormField.NAME)),
            singleLine = true,
            textStyle = autoStyle,
            isError = validationMessages.any { it.field == PatientFormField.NAME },
            errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.NAME }?.message,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        AppFieldPair(
            minimumRowWidth = UiLayout.patientFieldPairMinWidth, matchHeights = true,
            separator = uiText(R.string.ui_or),
            first = { fieldModifier ->
                AppTextField(
                    value = ageText,
                    onValueChange = { value ->
                        val cleaned = value.filter(Char::isDigit).take(3)
                        val nextAge = cleaned
                        val nextYear = cleaned.toIntOrNull()
                            ?.takeIf { it in 0..120 }
                            ?.let { currentYear - it }
                            ?.toString()
                            .orEmpty()
                        onAgeAndYearChanged(nextAge, nextYear)
                    },
                    label = { Text(uiText(R.string.ui_patient_demographic_fields_008)) },
                    modifier = fieldModifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.AGE)),
                    singleLine = true,
                    textStyle = autoStyle,
                    isError = validationMessages.any { it.field == PatientFormField.AGE },
                    errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.AGE }?.message,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )
            },
            second = { fieldModifier ->
                AppTextField(
                    value = birthYearText,
                    onValueChange = { value ->
                        val cleaned = value.filter(Char::isDigit).take(4)
                        val nextYear = cleaned
                        val nextAge = cleaned.toIntOrNull()
                            ?.let { currentYear - it }
                            ?.takeIf { it in 0..120 }
                            ?.toString()
                            .orEmpty()
                        onAgeAndYearChanged(nextAge, nextYear)
                    },
                    label = { Text(uiText(R.string.ui_patient_demographic_fields_009)) },
                    modifier = fieldModifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.BIRTH_YEAR)),
                    singleLine = true,
                    textStyle = autoStyle,
                    isError = validationMessages.any { it.field == PatientFormField.BIRTH_YEAR },
                    errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.BIRTH_YEAR }?.message,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )
            }
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(uiText(R.string.ui_patient_demographic_fields_010),
                    modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium)
                AppFilterChip(
                    enabled = !saving,
                    selected = gender == Gender.MALE,
                    onClick = { onGenderChanged(Gender.MALE) },
                    label = { Text(uiText(R.string.ui_male)) }
                )
                AppFilterChip(
                    enabled = !saving,
                    selected = gender == Gender.FEMALE,
                    onClick = { onGenderChanged(Gender.FEMALE) },
                    label = { Text(uiText(R.string.ui_female)) }
                )
            }
            AppToggleRow(title = uiText(R.string.ui_patient_demographic_fields_013), checked = hasCompanion,
                onCheckedChange = onCompanionChanged, enabled = !saving, modifier = Modifier.weight(.85f))
        }
    }

    PatientFormSection {
        AppSectionHeader(
            uiText(R.string.ui_patient_demographic_fields_001)
        )
        AppFieldPair(
            minimumRowWidth = UiLayout.patientFieldPairMinWidth, matchHeights = true,
            first = { fieldModifier ->
                AppTextField(
                    value = admittanceNumber,
                    onValueChange = onAdmissionNumberChanged,
                    label = { Text(uiText(R.string.ui_patient_demographic_fields_002)) },
                    modifier = fieldModifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.ADMISSION_NUMBER)),
                    singleLine = true,
                    textStyle = autoStyle,
                    isError = validationMessages.any { it.field == PatientFormField.ADMISSION_NUMBER },
                    errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.ADMISSION_NUMBER }?.message,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
            },
            second = { fieldModifier ->
                DateField(
                    value = admittanceDate,
                    onValueChange = onAdmissionDateChanged,
                    onClear = { onAdmissionDateChanged(null) },
                    label = uiText(R.string.ui_patient_demographic_fields_003),
                    isError = validationMessages.any { it.field == PatientFormField.ADMISSION_DATE },
                    errorMessage = validationMessages.firstOrNull { it.field == PatientFormField.ADMISSION_DATE }?.message,
                    supportingText = null,
                    modifier = fieldModifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.ADMISSION_DATE))
                )
            }
        )
        admissionDays?.let {
            Text(uiText(R.string.ui_patient_demographic_fields_004, uiNumber(it)),
                modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
