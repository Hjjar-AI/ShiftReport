package com.hos.rushdpatients.domain.sort

import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient

object PatientComparators {

    fun forSpec(
        spec: SortSpec,
        doctorNames: Map<String, String>,
        customOrder: CustomOrder = CustomOrder.DEFAULT
    ): Comparator<Patient> {
        if (spec.isEmpty) {
            return compareBy<Patient> { it.sortOrder }
                .thenBy { it.name.lowercase() }
        }
        return Comparator { a, b ->
            for (level in spec.levels) {
                val cmp = compareLevel(a, b, level, doctorNames, customOrder)
                if (cmp != 0) return@Comparator cmp
            }
            a.name.lowercase().compareTo(b.name.lowercase())
                .takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
    }

    /** Shared ward/report/export order, with consecutive patient numbers. */
    fun ordered(patients: List<Patient>, spec: SortSpec, doctorNames: Map<String, String>): List<Patient> =
        patients.sortedWith(compareByDescending<Patient> { it.isPriority }.then(forSpec(spec, doctorNames)))
            .mapIndexed { index, patient -> patient.copy(sortOrder = index + 1) }

    private fun compareLevel(
        a: Patient,
        b: Patient,
        level: SortLevel,
        doctorNames: Map<String, String>,
        customOrder: CustomOrder
    ): Int {
        val base = when (level.field) {
            SortField.NAME -> compareName(a, b)
            SortField.GENDER -> compareGender(a, b)
            SortField.DIAGNOSIS -> compareDiagnosis(a, b, customOrder)
            SortField.DAYS_OF_ADMITTANCE -> compareDays(a, b)
            SortField.SUPERVISOR -> compareSupervisor(a, b, doctorNames)
        }
        return if (level.direction == SortDirection.DESC) -base else base
    }

    private fun compareName(a: Patient, b: Patient): Int =
        a.name.lowercase().compareTo(b.name.lowercase())

    private fun compareGender(a: Patient, b: Patient): Int =
        when {
            a.gender == b.gender -> 0
            a.gender == Gender.MALE -> -1
            else -> 1
        }

    private fun compareDiagnosis(
        a: Patient,
        b: Patient,
        customOrder: CustomOrder
    ): Int {
        val ra = customOrder.diagnosisRank(a.diagnosisType.arabicLabel)
        val rb = customOrder.diagnosisRank(b.diagnosisType.arabicLabel)
        val byRank = ra.compareTo(rb)
        if (byRank != 0) return byRank
        return a.initialDiagnosis.lowercase().compareTo(b.initialDiagnosis.lowercase())
    }

    private fun compareDays(a: Patient, b: Patient): Int {
        val da = a.admittanceDays ?: 0
        val db = b.admittanceDays ?: 0
        return da.compareTo(db)
    }

    private fun compareSupervisor(
        a: Patient,
        b: Patient,
        doctorNames: Map<String, String>
    ): Int {
        val sa = a.responsibleSpecialistId?.let { doctorNames[it] }.orEmpty()
        val sb = b.responsibleSpecialistId?.let { doctorNames[it] }.orEmpty()
        return sa.lowercase().compareTo(sb.lowercase())
    }
}