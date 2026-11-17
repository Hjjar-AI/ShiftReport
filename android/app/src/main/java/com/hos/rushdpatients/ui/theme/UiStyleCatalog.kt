package com.hos.rushdpatients.ui.theme

import androidx.annotation.StringRes
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.UiStrings
import androidx.compose.ui.unit.Dp

/** Presentation profile values; selection is device-local and never synchronized. */
data class UiStyleProfile(
    val id: String, @StringRes val labelRes: Int, val description: String,
    val compactDensity: PatientCardDensity, val normalDensity: PatientCardDensity,
    val listGap: Dp, val normalBorder: Dp, val selectedBorder: Dp,
    val navigationElevation: Dp, val actionBarElevation: Dp,
    val expandMillis: Int, val collapseMillis: Int, val highlightMillis: Int,
    val indicatorMillis: Int = UiMotion.indicatorMillis,
    val placementMillis: Int = UiMotion.placementMillis
) {
    fun label(text: UiStrings): String = text(labelRes)
}

/** Current presentation values; clinical meaning and minimum targets stay fixed. */
object UiStyleCatalog {
    val current = UiStyleProfile(
        "current", R.string.ui_profile_current, "التصميم الحالي؛ القيم المستخدمة فعلياً",
        UiPatientCard.compact, UiPatientCard.normal, UiPatientCard.listGap,
        UiBorder.normalWidth, UiBorder.selectedWidth, UiElevation.navigation, UiElevation.actionBar,
        UiMotion.expandMillis, UiMotion.collapseMillis, UiMotion.highlightMillis)
}
