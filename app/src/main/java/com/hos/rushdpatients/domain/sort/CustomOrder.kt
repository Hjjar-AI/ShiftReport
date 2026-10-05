package com.hos.rushdpatients.domain.sort

/**
 * Configurable custom orderings for sort fields.
 *
 * Arabic strings mirror the VBA sheet's `ARABIC(DIAGNOSIS_CUSTOM_ORDER_LIST)`.
 * Modify at runtime if needed; defaults match the VBA behaviour.
 */
data class CustomOrder(
    val diagnosisOrder: List<String> = listOf("نفسي", "إدمان", "مزدوج"),
    val unknownDiagnosisRank: Int = Int.MAX_VALUE,
    val nameAlphabetical: Boolean = true
) {
    fun diagnosisRank(diagnosis: String): Int {
        val idx = diagnosisOrder.indexOfFirst { it == diagnosis }
        return if (idx >= 0) idx else unknownDiagnosisRank
    }

    companion object {
        val DEFAULT = CustomOrder()
    }
}
