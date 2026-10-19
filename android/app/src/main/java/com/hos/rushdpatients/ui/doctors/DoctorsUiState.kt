package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.data.model.Doctor

data class DoctorImportPreview(
    val activeCount: Int,
    val adminCount: Int,
    val deletedCount: Int,
    val newWithoutPinCount: Int,
    val added: List<String> = emptyList(),
    val changed: List<String> = emptyList(),
    val unchanged: List<String> = emptyList(),
    val retained: List<String> = emptyList()
)

data class DoctorsUiState(
    val loading: Boolean = true,
    val doctors: List<Doctor> = emptyList(),
    val saving: Boolean = false,
    val exporting: Boolean = false,
    val exportReady: Boolean = false,
    val importing: Boolean = false,
    val importPreview: DoctorImportPreview? = null,
    val error: String? = null,
    val snackbar: String? = null
)
