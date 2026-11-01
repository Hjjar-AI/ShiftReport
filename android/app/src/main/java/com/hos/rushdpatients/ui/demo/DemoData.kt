package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.doctor.DoctorTeams

internal object DemoData {
    fun doctors(): List<Doctor> = listOf("أرز", "بلوط", "صنوبر", "زيتون", "صفصاف", "نخيل").mapIndexed { index, name ->
        val doctor = Doctor(id = "demo-doctor-$index", fullName = "د. $name", firstName = name, lastName = "تجريبي",
            gender = if (index % 2 == 0) Gender.MALE else Gender.FEMALE,
            clinicalRole = if (index < 3) ClinicalRole.RESIDENT else ClinicalRole.SUPERVISOR)
        if (index < 3) DoctorTeams.assign(doctor, "demo-doctor-${index + 3}") else doctor
    }

    fun patients(doctors: List<Doctor>): List<Patient> =
        DemoSamplePatients.patients.mapIndexed { index, sample ->
            // Keep the sample's content; only demo identity and tree-doctor assignments are local.
            sample.copy(
                id = "demo-patient-$index",
                responsibleResidentId = doctors[index % 3].id,
                responsibleSpecialistId = doctors[3 + index % 3].id
            )
        }
}
