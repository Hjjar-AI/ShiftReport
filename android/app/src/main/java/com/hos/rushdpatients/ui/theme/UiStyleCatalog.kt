package com.hos.rushdpatients.ui.theme

import androidx.annotation.StringRes
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.UiStrings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp

/** Presentation profile values; selection is device-local and never synchronized. */
data class UiStyleProfile(
    val id: String, @StringRes val labelRes: Int, val description: String,
    val compactDensity: PatientCardDensity, val normalDensity: PatientCardDensity,
    val listGap: Dp, val normalBorder: Dp, val selectedBorder: Dp,
    val navigationElevation: Dp, val actionBarElevation: Dp,
    val expandMillis: Int, val collapseMillis: Int, val highlightMillis: Int,
    val indicatorMillis: Int = UiMotion.indicatorMillis
) {
    fun label(text: UiStrings): String = text(labelRes)
}

/** Current and selectable presentation profiles; clinical meaning and minimum targets stay fixed. */
object UiStyleCatalog {
    val current = UiStyleProfile(
        "current", R.string.ui_profile_current, "التصميم الحالي؛ القيم المستخدمة فعلياً",
        UiPatientCard.compact, UiPatientCard.normal, UiPatientCard.listGap,
        UiBorder.normalWidth, UiBorder.selectedWidth, UiElevation.navigation, UiElevation.actionBar,
        UiMotion.expandMillis, UiMotion.collapseMillis, UiMotion.highlightMillis)
    val relaxed = current.copy(
        id = "relaxed", labelRes = R.string.ui_profile_relaxed, description = "مسافات أكبر بين المحتوى والبطاقات",
        compactDensity = PatientCardDensity(PaddingValues(horizontal = UiSpacing.medium, vertical = UiSpacing.small), UiSpacing.medium),
        normalDensity = PatientCardDensity(PaddingValues(horizontal = UiSpacing.largeSection, vertical = UiSpacing.section), UiSpacing.section),
        listGap = UiSpacing.section)
    val quiet = current.copy(
        id = "quiet", labelRes = R.string.ui_profile_quiet, description = "أسطح مسطحة وحركة أقصر",
        navigationElevation = UiElevation.flat, actionBarElevation = UiElevation.flat,
        expandMillis = 120, collapseMillis = 100, highlightMillis = 180, indicatorMillis = 100)
    val clear = current.copy(
        id = "clear", labelRes = R.string.ui_profile_clear, description = "حدود أكثر وضوحاً مع بقاء الألوان السريرية ثابتة",
        normalBorder = UiBorder.strong)
    val profiles = listOf(current, relaxed, quiet, clear)
}
