package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.data.model.Doctor

data class DoctorsUiState(
    val loading: Boolean = true,
    val doctors: List<Doctor> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
    val snackbar: String? = null
)
