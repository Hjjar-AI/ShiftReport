package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.data.model.Patient
import java.time.LocalDate

/** Hidden assignment-PDF marking, independent of admission dates and ordinary clinical edits. */
object PatientAssignmentChange {
    fun onSave(patient: Patient, current: Patient?, day: LocalDate): Patient = patient.copy(
        assignmentChangedOn = if (current == null ||
            patient.responsibleSpecialistId != current.responsibleSpecialistId) day
        else current.assignmentChangedOn
    )

    fun highlighted(patient: Patient, reportDay: LocalDate): Boolean =
        patient.assignmentChangedOn == reportDay
}
