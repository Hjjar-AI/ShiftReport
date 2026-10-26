package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.PatientBadgePriority
import com.hos.rushdpatients.data.model.TaskPriority
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfRuleWeight
import com.hos.rushdpatients.pdf.PdfRuleContrast
import com.hos.rushdpatients.pdf.PdfRowShading
import com.hos.rushdpatients.pdf.PdfPresentationPreset

/** UI labels use resources; portable model codes and clinical/export wording stay unchanged. */

fun ClinicalRole.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    ClinicalRole.RESIDENT -> R.string.ui_project_setup_screen_034
    ClinicalRole.SUPERVISOR -> R.string.ui_project_setup_screen_035
})

fun DiagnosisType.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    DiagnosisType.PSYCHIATRIC -> R.string.ui_label_diagnosis_type_001
    DiagnosisType.ADDICTION -> R.string.ui_label_diagnosis_type_002
    DiagnosisType.DUAL -> R.string.ui_label_diagnosis_type_003
})

fun PatientBadgePriority.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    PatientBadgePriority.LOW -> R.string.ui_label_patient_badge_priority_001
    PatientBadgePriority.MEDIUM -> R.string.ui_label_patient_badge_priority_002
    PatientBadgePriority.HIGH -> R.string.ui_label_patient_badge_priority_003
})

fun TaskPriority.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    TaskPriority.LOW -> R.string.ui_label_patient_badge_priority_001
    TaskPriority.NORMAL -> R.string.ui_label_task_priority_001
    TaskPriority.HIGH -> R.string.ui_label_patient_badge_priority_003
})

fun GroupByMode.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    GroupByMode.NONE -> R.string.ui_label_group_by_mode_001
    GroupByMode.SUPERVISOR -> R.string.ui_label_group_by_mode_002
    GroupByMode.RESIDENT -> R.string.ui_label_group_by_mode_003
    GroupByMode.DIAGNOSIS -> R.string.ui_label_group_by_mode_004
    GroupByMode.GENDER -> R.string.ui_label_group_by_mode_005
    GroupByMode.ADMISSION_DATE -> R.string.ui_label_group_by_mode_006
})

fun PdfStyle.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    PdfStyle.CLASSIC -> R.string.ui_label_pdf_style_001
    PdfStyle.CARDS -> R.string.ui_label_pdf_style_002
})

fun PdfOrientation.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    PdfOrientation.PORTRAIT -> R.string.ui_label_pdf_orientation_001
    PdfOrientation.LANDSCAPE -> R.string.ui_label_pdf_orientation_002
})

fun PdfPaperSize.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    PdfPaperSize.A4 -> R.string.ui_label_pdf_paper_size_001
    PdfPaperSize.A5 -> R.string.ui_label_pdf_paper_size_003
})

fun PdfColorPreset.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    PdfColorPreset.TEAL -> R.string.ui_label_pdf_color_preset_001
    PdfColorPreset.BLUE -> R.string.ui_label_pdf_color_preset_002
    PdfColorPreset.GREEN -> R.string.ui_label_pdf_color_preset_003
    PdfColorPreset.PURPLE -> R.string.ui_label_pdf_color_preset_004
    PdfColorPreset.PASTEL_RAINBOW -> R.string.ui_label_pdf_color_preset_005
})

fun PdfRuleWeight.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    PdfRuleWeight.THIN -> R.string.ui_label_pdf_rule_weight_001
    PdfRuleWeight.MEDIUM -> R.string.ui_label_patient_badge_priority_002
    PdfRuleWeight.THICK -> R.string.ui_label_pdf_rule_weight_002
})

fun PdfRuleContrast.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    PdfRuleContrast.LIGHT -> R.string.ui_label_pdf_rule_contrast_001
    PdfRuleContrast.MEDIUM -> R.string.ui_label_pdf_rule_contrast_002
    PdfRuleContrast.STRONG -> R.string.ui_label_pdf_rule_contrast_003
})

fun PdfRowShading.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    PdfRowShading.AUTO -> R.string.ui_label_pdf_row_shading_001
    PdfRowShading.ZEBRA -> R.string.ui_label_pdf_row_shading_002
    PdfRowShading.NONE -> R.string.ui_label_pdf_row_shading_003
})

fun PdfPresentationPreset.uiLabel(uiText: UiStrings): String = uiText(when (this) {
    PdfPresentationPreset.BALANCED -> R.string.ui_label_pdf_presentation_preset_001
    PdfPresentationPreset.CLEAR -> R.string.ui_label_pdf_presentation_preset_002
    PdfPresentationPreset.PRINT -> R.string.ui_label_pdf_presentation_preset_003
})
