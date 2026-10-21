package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R

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

internal fun groupPatients(uiText: UiStrings,
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
                        name = id?.let(names::get) ?: uiText(R.string.ui_ward_patient_ordering_001),
                        patients = list
                    )
                }
                .sortedWith(compareBy({ it.key == null }, { it.name }))

            GroupByMode.RESIDENT -> patients
                .groupBy { it.responsibleResidentId }
                .map { (id, list) ->
                    PatientGroup(
                        key = id,
                        name = id?.let(names::get) ?: uiText(R.string.ui_ward_patient_ordering_002),
                        patients = list
                    )
                }
                .sortedWith(compareBy({ it.key == null }, { it.name }))

            GroupByMode.DIAGNOSIS -> patients
                .groupBy { it.diagnosisType }
                .map { (type, list) ->
                    PatientGroup(
                        key = type.code,
                        name = type.uiLabel(uiText),
                        patients = list
                    )
                }
                .sortedBy { it.name }

            GroupByMode.GENDER -> patients
                .groupBy { it.gender }
                .map { (gender, list) ->
                    PatientGroup(
                        key = gender.code,
                        name = if (gender == Gender.MALE) uiText(R.string.ui_male) else uiText(R.string.ui_female),
                        patients = list
                    )
                }
                .sortedBy { it.name }

            GroupByMode.ADMISSION_DATE -> patients
                .groupBy { it.admittanceDate }
                .map { (date, list) ->
                    PatientGroup(
                        key = date?.toString(),
                        name = date?.toString() ?: uiText(R.string.ui_ward_patient_ordering_003),
                        patients = list
                    )
                }
                .sortedWith(
                    compareBy<PatientGroup> { it.key == null }
                        .thenByDescending { it.key ?: "" }
                )
        }
    }
