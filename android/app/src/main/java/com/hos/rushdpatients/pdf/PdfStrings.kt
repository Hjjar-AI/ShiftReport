package com.hos.rushdpatients.pdf

/**
 * Arabic literal strings used by the PDF renderer.
 *
 * Kept beside the canvas renderers because Android resources are not available
 * to these small, context-free drawing helpers.
 */
object PdfStrings {
    const val REPORT_TITLE_PREFIX = "تقرير مناوبة"
    const val HEADER_ID = "#"
    const val HEADER_PATIENT = "المريض"
    const val HEADER_CONDITION = "التشخيص / التنبيهات"
    const val HEADER_ADMIT_NUM = "رقم القبول الحالي"
    const val HEADER_ADMIT_DAYS = "التاريخ / الأيام"
    const val HEADER_SUPERVISOR = "الاختصاصي"
    const val HEADER_RESIDENT = "المقيم"
    const val HEADER_TREATMENT = "الخطة العلاجية"
    const val HEADER_NOTES = "المتابعة / التحاليل"

    const val SEPARATOR = "---"
    const val ESCORT_LABEL = "مرافق"
    const val ESCORTS_COUNT_LABEL = "عدد المرافقين"
    const val PATIENTS_COUNT_LABEL = "عدد المرضى"
    const val PSYCHO_SHORT = "ن"
    const val YEAR_SUFFIX = "سنة"
    const val DATE_LABEL = "تاريخ المناوبة"
}
