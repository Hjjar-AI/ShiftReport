package com.hos.rushdpatients.migration

import com.hos.rushdpatients.data.model.Patient

data class VbaImportPreview(
    val fileName: String,
    val shiftDate: java.time.LocalDate,
    val doctors: List<String>,
    val newPatients: List<Patient>,
    val updatedPatients: List<Patient>,
    val unchangedPatients: List<Patient>,
    val warnings: List<String>
) {
    val totalPatients: Int
        get() = newPatients.size + updatedPatients.size + unchangedPatients.size
}