package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.data.model.DoctorOptions
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor

/** Shared one-supervisor membership, stored in the existing portable options set. */
object DoctorTeams {

    fun assign(doctor: Doctor, supervisorId: String?): Doctor {
        require(supervisorId == null || supervisorId.isNotBlank() &&
            supervisorId.none { it.isWhitespace() || it in ",|@;\"" }) { "معرّف مشرف الفريق غير صالح" }
        return doctor.copy(extraOptions = doctor.options.withTeamSupervisor(supervisorId))
    }

    fun validate(doctors: List<Doctor>) {
        val active = doctors.filterNot { it.isDeleted }.associateBy { it.id }
        active.values.forEach { doctor ->
            val links = doctor.options.teamSupervisorIds
            require(links.size <= 1 && links.none { it.isBlank() }) { "لكل مقيم مشرف فريق واحد فقط" }
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
        DoctorOptions.from(local).withSharedTeam(incoming.options)

    fun remap(doctors: List<Doctor>, aliases: Map<String, String>): List<Doctor> = doctors.map { doctor ->
        require(doctor.options.teamSupervisorIds.size <= 1) { "لكل مقيم مشرف فريق واحد فقط" }
        assign(doctor, doctor.teamSupervisorId?.let { aliases[it] ?: it })
    }
}
