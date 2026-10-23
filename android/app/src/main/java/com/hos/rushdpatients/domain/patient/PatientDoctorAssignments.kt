package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient

/** Live registry checks at transactional clinical write boundaries. Ownership stays optional. */
object PatientDoctorAssignments {
    fun requireValid(patient: Patient, doctors: Map<String, Doctor>) {
        patient.responsibleResidentId?.let { id ->
            require(doctors[id]?.let { !it.isDeleted && it.clinicalRole.canBeResident() } == true) {
                "المقيم المسؤول غير موجود أو تصنيفه غير صالح"
            }
        }
        patient.responsibleSpecialistId?.let { id ->
            require(doctors[id]?.let { !it.isDeleted && it.clinicalRole.canBeSupervisor() } == true) {
                "الاختصاصي المسؤول غير موجود أو تصنيفه غير صالح"
            }
        }
        patient.tasks.filterNot { it.done }.forEach { task ->
            task.ownerDoctorId?.let { id ->
                require(doctors[id]?.isDeleted == false) { "مسؤول المهمة غير موجود أو غير فعال" }
            }
        }
    }
}
