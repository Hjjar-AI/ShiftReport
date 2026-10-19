package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import java.time.Instant

enum class KnownShiftState { UNKNOWN, MATCHED, CHANGED }

/** Compare report content and order with a locally recorded snapshot, without a network claim. */
object KnownShiftSnapshot {
    fun compare(shift: Shift?, patients: List<Patient>, csv: String?): KnownShiftState {
        if (shift == null || csv.isNullOrBlank()) return KnownShiftState.UNKNOWN
        val base = runCatching { CsvCodec.decode(csv) }.getOrNull() ?: return KnownShiftState.UNKNOWN
        if (base.shiftId != shift.id || base.shiftDate != shift.date) return KnownShiftState.UNKNOWN
        fun content(values: List<Patient>) = values.filterNot { it.isDeleted }.sortedBy { it.sortOrder }.map {
            it.copy(updatedAt = Instant.EPOCH, revision = 0, sortOrder = 0)
        }
        val same = shift.doctorIds == base.doctorIds &&
            SortSpecCodec.decode(shift.sortSpecJson) == SortSpecCodec.decode(base.sortSpecJson) &&
            content(patients) == content(base.patients)
        return if (same) KnownShiftState.MATCHED else KnownShiftState.CHANGED
    }
}
