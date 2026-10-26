package com.hos.rushdpatients.domain.doctor

/** Content can be reused in publication review without parsing a translated instruction. */
data class RegistryChangeSummary(
    val summary: String,
    val instruction: String = "افتح سجل الأطباء، راجع التعديلات ثم اختر «نشر سجل الأطباء»."
) {
    val message: String get() = "$summary\n$instruction"
}
