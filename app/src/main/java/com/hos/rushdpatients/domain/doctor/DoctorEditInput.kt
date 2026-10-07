package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Gender

/** Transient editor input; the entered PIN is never persisted as a draft or included in audit output. */
data class DoctorEditInput(
    val firstName: String,
    val lastName: String,
    val gender: Gender,
    val clinicalRole: ClinicalRole,
    val pin: String?,
    val customTitle: String?,
    val telegramId: Long?
)
