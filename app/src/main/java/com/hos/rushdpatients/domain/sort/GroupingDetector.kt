package com.hos.rushdpatients.domain.sort

import com.hos.rushdpatients.data.model.Patient

object GroupingDetector {

    fun isGroupedBySupervisor(
        patients: List<Patient>,
        doctorNames: Map<String, String>
    ): Boolean {
        val seen = mutableSetOf<String>()
        var current: String? = null

        for (p in patients) {
            val supervisor = p.responsibleSpecialistId?.let { doctorNames[it] }.orEmpty()
            if (supervisor != current) {
                if (supervisor in seen) return false
                seen += supervisor
                current = supervisor
            }
        }
        return true
    }
}
