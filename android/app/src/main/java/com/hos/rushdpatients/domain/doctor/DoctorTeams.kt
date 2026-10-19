package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor

/** Shared one-supervisor membership, stored in the existing portable options set. */
object DoctorTeams {
    const val PREFIX = "teamSupervisor:"

    fun assign(doctor: Doctor, supervisorId: String?): Doctor {
        require(supervisorId == null || supervisorId.isNotBlank() &&
            supervisorId.none { it.isWhitespace() || it in ",|@;\"" }) { "معرّف مشرف الفريق غير صالح" }
        return doctor.copy(extraOptions = doctor.extraOptions.filterNot { it.startsWith(PREFIX) }.toSet() +
            supervisorId?.let { setOf(PREFIX + it) }.orEmpty())
    }

    fun validate(doctors: List<Doctor>) {
        val active = doctors.filterNot { it.isDeleted }.associateBy { it.id }
        active.values.forEach { doctor ->
            val links = doctor.extraOptions.filter { it.startsWith(PREFIX) }
            require(links.size <= 1 && links.none { it == PREFIX }) { "لكل مقيم مشرف فريق واحد فقط" }
            doctor.teamSupervisorId?.let { id ->
                require(doctor.clinicalRole == ClinicalRole.RESIDENT && id != doctor.id &&
                    active[id]?.clinicalRole == ClinicalRole.SUPERVISOR) {
                    "اختر مشرفاً نشطاً للفريق؛ انقل المقيمين قبل حذف المشرف أو تغيير تصنيفه"
                }
            }
        }
    }

    /** A patient-level cover assignment never changes the resident's permanent team. */
    fun isCrossTeamCover(resident: Doctor?, patientSupervisorId: String?): Boolean =
        resident?.teamSupervisorId != null && patientSupervisorId != null &&
            resident.teamSupervisorId != patientSupervisorId

    fun sharedOptions(local: Set<String>, incoming: Doctor): Set<String> =
        local.filterNot { it.startsWith(PREFIX) }.toSet() +
            incoming.extraOptions.filter { it.startsWith(PREFIX) }

    fun remap(doctors: List<Doctor>, aliases: Map<String, String>): List<Doctor> = doctors.map { doctor ->
        require(doctor.extraOptions.count { it.startsWith(PREFIX) } <= 1) { "لكل مقيم مشرف فريق واحد فقط" }
        assign(doctor, doctor.teamSupervisorId?.let { aliases[it] ?: it })
    }
}
