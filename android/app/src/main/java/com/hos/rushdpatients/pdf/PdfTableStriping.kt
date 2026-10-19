package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupingDetector

internal object PdfTableStriping {
    fun colors(patients: List<Patient>, palette: PdfPalette.Resolved): List<Int> {
        if (!GroupingDetector.isGroupedBySupervisor(patients))
            return patients.indices.map { if (it % 2 == 0) palette.pageBackground else palette.zebra }
        var previous: String? = null
        var group = -1
        var position = 0
        return patients.map { patient ->
            val key = patient.responsibleSpecialistId.orEmpty()
            if (key != previous) { group++; position = 0; previous = key }
            (if (position++ % 2 == 0) palette.group(group) else palette.cream)
        }
    }
}
