package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.domain.doctor.DoctorRegistryConflict
import com.hos.rushdpatients.data.model.Doctor

data class DoctorImportPreview(
    val activeCount: Int,
    val adminCount: Int,
    val deletedCount: Int,
    val newWithoutPinCount: Int
)

data class DoctorsUiState(
    val loading: Boolean = true,
    val doctors: List<Doctor> = emptyList(),
    val saving: Boolean = false,
    val exporting: Boolean = false,
    val importing: Boolean = false,
    val importPreview: DoctorImportPreview? = null,
    val mergeConflicts: List<DoctorRegistryConflict> = emptyList(),
    val error: String? = null,
    val snackbar: String? = null
)
