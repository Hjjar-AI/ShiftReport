package com.hos.rushdpatients.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Design samples only. No profile is applied, persisted or synchronized by this catalog. */
data class UiStyleProfile(
    val id: String, val label: String, val description: String,
    val compactDensity: PatientCardDensity, val normalDensity: PatientCardDensity,
    val listGap: Dp, val normalBorder: Dp, val selectedBorder: Dp,
    val navigationElevation: Dp, val actionBarElevation: Dp,
    val expandMillis: Int, val collapseMillis: Int, val highlightMillis: Int
)

/** Spare values for a possible future local appearance picker; current tokens remain authoritative. */
object UiStyleCatalog {
    val current = UiStyleProfile(
        "current", "الحالي", "التصميم الحالي؛ القيم المستخدمة فعلياً",
        UiPatientCard.compact, UiPatientCard.normal, UiPatientCard.listGap,
        UiBorder.normalWidth, UiBorder.selectedWidth, UiElevation.navigation, UiElevation.actionBar,
        UiMotion.expandMillis, UiMotion.collapseMillis, UiMotion.highlightMillis)
    val relaxed = current.copy(
        id = "relaxed", label = "أوسع", description = "مسافات أكبر بين المحتوى والبطاقات",
        compactDensity = PatientCardDensity(PaddingValues(horizontal = UiSpacing.medium, vertical = UiSpacing.small), UiSpacing.medium),
        normalDensity = PatientCardDensity(PaddingValues(horizontal = UiSpacing.largeSection, vertical = UiSpacing.section), UiSpacing.section),
        listGap = UiSpacing.section)
    val quiet = current.copy(
        id = "quiet", label = "هادئ", description = "أسطح مسطحة وحركة أقصر",
        navigationElevation = UiElevation.flat, actionBarElevation = UiElevation.flat,
        expandMillis = 120, collapseMillis = 100, highlightMillis = 180)
    val clear = current.copy(
        id = "clear", label = "أوضح", description = "حدود أكثر وضوحاً مع بقاء الألوان السريرية ثابتة",
        normalBorder = 1.5.dp)
    val profiles = listOf(current, relaxed, quiet, clear)
}
