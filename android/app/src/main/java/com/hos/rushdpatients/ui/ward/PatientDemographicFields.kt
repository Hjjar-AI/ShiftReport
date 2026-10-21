package com.hos.rushdpatients.ui.ward

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
import androidx.compose.material3.FilterChip
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
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PatientDemographicFields(
    admittanceNumber: String, admittanceDate: LocalDate?, admissionDays: Int?, name: String,
    ageText: String, birthYearText: String, birthYear: Int?, age: Int?, currentYear: Int,
    gender: Gender, hasCompanion: Boolean, saving: Boolean, autoStyle: TextStyle,
    validationMessages: List<PatientFormError>, fieldRequests: Map<PatientFormField, BringIntoViewRequester>,
    personalAnchor: Modifier, onAdmissionNumberChanged: (String) -> Unit,
    onAdmissionDateChanged: (LocalDate?) -> Unit, onNameChanged: (String) -> Unit,
    onAgeAndYearChanged: (String, String) -> Unit, onGenderChanged: (Gender) -> Unit,
    onCompanionChanged: (Boolean) -> Unit
) {
    SectionTitle(
        "بيانات الدخول",
        complete = admittanceNumber.isNotBlank() && admittanceDate != null,
        modifier = personalAnchor
    )
    AppFieldPair(
        stackWhenNarrow = true,
        first = { fieldModifier ->
            AppTextField(
                value = admittanceNumber,
                onValueChange = onAdmissionNumberChanged,
                label = { Text("رقم القبول الحالي *") },
                modifier = fieldModifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.ADMISSION_NUMBER)),
                singleLine = true,
                textStyle = autoStyle,
                isError = validationMessages.any { it.field == PatientFormField.ADMISSION_NUMBER },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
        },
        second = { fieldModifier ->
            DateField(
                value = admittanceDate,
                onValueChange = onAdmissionDateChanged,
                label = "تاريخ الدخول *",
                isError = validationMessages.any { it.field == PatientFormField.ADMISSION_DATE },
                supportingText = admissionDays?.let { "مدة الإقامة: ${uiNumber(it)} يوم" },
                modifier = fieldModifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.ADMISSION_DATE))
            )
        }
    )

    SectionTitle(
        "البيانات الشخصية",
        complete = name.isNotBlank() && birthYear != null && age != null
    )
    AppTextField(
        value = name,
        onValueChange = onNameChanged,
        label = { Text("اسم المريض *") },
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(fieldRequests.getValue(PatientFormField.NAME)),
        singleLine = true,
        textStyle = autoStyle,
        isError = validationMessages.any { it.field == PatientFormField.NAME },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
    )
    AppFieldPair(
        stackWhenNarrow = true,
        separator = "أو",
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
                label = { Text("العمر *") },
                modifier = fieldModifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.AGE)),
                singleLine = true,
                textStyle = autoStyle,
                isError = validationMessages.any { it.field == PatientFormField.AGE },
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
                label = { Text("سنة الميلاد *") },
                modifier = fieldModifier.bringIntoViewRequester(fieldRequests.getValue(PatientFormField.BIRTH_YEAR)),
                singleLine = true,
                textStyle = autoStyle,
                isError = validationMessages.any { it.field == PatientFormField.BIRTH_YEAR },
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
            Text("الجنس", style = MaterialTheme.typography.labelMedium)
            FilterChip(
                enabled = !saving,
                selected = gender == Gender.MALE,
                onClick = { onGenderChanged(Gender.MALE) },
                label = { Text("ذكر") }
            )
            FilterChip(
                enabled = !saving,
                selected = gender == Gender.FEMALE,
                onClick = { onGenderChanged(Gender.FEMALE) },
                label = { Text("أنثى") }
            )
        }
        AppToggleRow(title = "يوجد مرافق", checked = hasCompanion,
            onCheckedChange = onCompanionChanged, enabled = !saving, modifier = Modifier.weight(.85f))
    }
}
