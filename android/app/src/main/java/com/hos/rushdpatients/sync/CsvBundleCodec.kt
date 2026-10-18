package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift

data class ShiftCsvSnapshot(
    val shift: Shift,
    val patients: List<Patient>
)

/**
 * A human-readable container of up to three ordinary shift CSV documents. A legacy single-shift
 * CSV is treated as a one-entry bundle, so existing Telegram data remains importable.
 */
object CsvBundleCodec {
    private const val HEADER = "# rushdShiftBundle=1"
    private const val BOUNDARY = "#==RUSHD_SHIFT_BOUNDARY_7F3A9C=="

    fun encode(shifts: List<ShiftCsvSnapshot>): String {
        require(shifts.isNotEmpty()) { "لا توجد مناوبات لإضافتها إلى ملف CSV" }
        val distinct = shifts
            .distinctBy { it.shift.id }
            .sortedByDescending { it.shift.date }
            .take(MAX_SHIFTS)
        return buildString {
            append(HEADER).append('\n')
            distinct.forEachIndexed { index, snapshot ->
                if (index > 0) append(BOUNDARY).append('\n')
                append(CsvCodec.encode(snapshot.shift, snapshot.patients).trimEnd())
                append('\n')
            }
        }
    }

    fun decode(csv: String): List<ParsedShift> {
        val cleaned = csv.removePrefix("\uFEFF")
        if (!cleaned.lineSequence().firstOrNull().orEmpty().trim().startsWith(HEADER)) {
            return listOf(CsvCodec.decode(cleaned))
        }
        val body = cleaned.substringAfter('\n', "")
        val parts = body.split("\n$BOUNDARY\n")
            .map(String::trim)
            .filter(String::isNotEmpty)
        require(parts.isNotEmpty() && parts.size <= MAX_SHIFTS) {
            "حزمة المناوبات فارغة أو تتجاوز الحد المسموح"
        }
        val parsed = parts.map(CsvCodec::decode)
        require(parsed.mapNotNull { it.shiftId }.distinct().size == parsed.mapNotNull { it.shiftId }.size) {
            "تحتوي حزمة المناوبات على معرّفات مكررة"
        }
        return parsed.sortedByDescending { it.shiftDate }
    }

    const val MAX_SHIFTS = 3
}
