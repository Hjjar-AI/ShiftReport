package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift

data class ShiftCsvSnapshot(
    val shift: Shift,
    val patients: List<Patient>
)

/** Current publications contain exactly one ward in the bundle envelope. */
object CsvBundleCodec {
    private const val HEADER = "# rushdShiftBundle=1"
    private const val BOUNDARY = "#==RUSHD_SHIFT_BOUNDARY_7F3A9C=="

    fun encode(shifts: List<ShiftCsvSnapshot>): String {
        require(shifts.size == 1) { "حزمة النشر يجب أن تحتوي قسماً واحداً" }
        val distinct = shifts
        return buildString {
            append(HEADER).append('\n')
            distinct.forEachIndexed { index, snapshot ->
                if (index > 0) append(BOUNDARY).append('\n')
                val csv = CsvCodec.encode(snapshot.shift, snapshot.patients).trimEnd()
                require(!csv.contains("\n$BOUNDARY\n")) { "نص يتعارض مع فاصل حزمة المناوبات" }
                append(csv)
                append('\n')
            }
        }
    }

    fun decode(csv: String): List<ParsedShift> {
        val cleaned = csv.removePrefix("\uFEFF")
        require(cleaned.lineSequence().firstOrNull().orEmpty().trim() == HEADER) { "مطلوب تنسيق حزمة القسم الحالي" }
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

    const val MAX_SHIFTS = 1
}
