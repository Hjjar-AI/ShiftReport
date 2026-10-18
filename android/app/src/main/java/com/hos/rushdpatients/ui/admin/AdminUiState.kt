package com.hos.rushdpatients.ui.admin

import com.hos.rushdpatients.data.model.Doctor

data class AdminUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val admins: List<Doctor> = emptyList(),
    val nonAdmins: List<Doctor> = emptyList(),
    val currentActor: Doctor? = null,
    val snackbar: String? = null
)
