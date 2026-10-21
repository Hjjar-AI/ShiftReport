package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpec
import kotlinx.coroutines.flow.map

internal fun sortPatients(
        patients: List<Patient>,
        doctors: List<Doctor>,
        spec: SortSpec
    ): List<Patient> {
        return PatientComparators.ordered(patients, spec, doctors.associate { it.id to it.fullName })
    }

internal fun groupPatients(
        patients: List<Patient>,
        doctors: List<Doctor>,
        mode: GroupByMode
    ): List<PatientGroup> {
        if (mode == GroupByMode.NONE || patients.isEmpty()) return emptyList()
        val names = doctors.associate { it.id to it.fullName }

        return when (mode) {
            GroupByMode.NONE -> emptyList()

            GroupByMode.SUPERVISOR -> patients
                .groupBy { it.responsibleSpecialistId }
                .map { (id, list) ->
                    PatientGroup(
                        key = id,
                        name = id?.let(names::get) ?: "بدون اختصاصي",
                        patients = list
                    )
                }
                .sortedWith(compareBy({ it.key == null }, { it.name }))

            GroupByMode.RESIDENT -> patients
                .groupBy { it.responsibleResidentId }
                .map { (id, list) ->
                    PatientGroup(
                        key = id,
                        name = id?.let(names::get) ?: "بدون مقيم",
                        patients = list
                    )
                }
                .sortedWith(compareBy({ it.key == null }, { it.name }))

            GroupByMode.DIAGNOSIS -> patients
                .groupBy { it.diagnosisType }
                .map { (type, list) ->
                    PatientGroup(
                        key = type.code,
                        name = type.arabicLabel,
                        patients = list
                    )
                }
                .sortedBy { it.name }

            GroupByMode.GENDER -> patients
                .groupBy { it.gender }
                .map { (gender, list) ->
                    PatientGroup(
                        key = gender.code,
                        name = if (gender == Gender.MALE) "ذكر" else "أنثى",
                        patients = list
                    )
                }
                .sortedBy { it.name }

            GroupByMode.ADMISSION_DATE -> patients
                .groupBy { it.admittanceDate }
                .map { (date, list) ->
                    PatientGroup(
                        key = date?.toString(),
                        name = date?.toString() ?: "بدون تاريخ دخول",
                        patients = list
                    )
                }
                .sortedWith(
                    compareBy<PatientGroup> { it.key == null }
                        .thenByDescending { it.key ?: "" }
                )
        }
    }
