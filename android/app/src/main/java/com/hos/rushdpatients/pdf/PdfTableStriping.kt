package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.data.model.Patient

/** Alternate patient rows in their displayed order, independent of team assignments. */
internal object PdfTableStriping {
    fun colors(patients: List<Patient>, palette: PdfPalette.Resolved): List<Int> =
        patients.indices.map { index ->
            if (index % 2 != 0) palette.zebra
            else palette.pageBackground
        }
}
