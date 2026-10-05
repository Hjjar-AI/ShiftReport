package com.hos.rushdpatients.ui.login

import com.hos.rushdpatients.data.model.Doctor

sealed interface LoginStep {
    data object Bootstrapping : LoginStep
    data class BootstrapFailed(val message: String) : LoginStep
    data class PickDoctor(val doctors: List<Doctor>) : LoginStep
    data class Verifying(
        val doctor: Doctor,
        val nonce: String,
        val remainingSeconds: Int
    ) : LoginStep
    data class VerifyFailed(val doctor: Doctor, val message: String) : LoginStep
    data class SetPin(val doctor: Doctor, val telegramId: Long) : LoginStep
}

data class LoginUiState(
    val projectName: String = "",
    val step: LoginStep = LoginStep.Bootstrapping,
    val busy: Boolean = false,
    val seeding: Boolean = false,
    val error: String? = null
)
