package com.hos.rushdpatients.domain.usecase

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.domain.doctor.DoctorNaming
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PromoteResult {
    data class Success(val doctor: Doctor) : PromoteResult
    data class Failure(val reason: String) : PromoteResult
}

@Singleton
class PromoteAdminUseCase @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val auditRepository: AuditRepository
) {

    suspend fun execute(
        doctorId: String,
        customTitle: String?,
        actorDoctorId: String?,
        actorName: String?
    ): PromoteResult {
        val target = doctorRepository.getById(doctorId)
            ?: return PromoteResult.Failure("الطبيب غير موجود")
        if (target.isDeleted) return PromoteResult.Failure("الطبيب محذوف")
        if (target.rank > 0) return PromoteResult.Success(target)

        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: DoctorNaming.defaultAdminTitle(target)

        val updated = doctorRepository.promoteToAdmin(target.id, title)
            ?: return PromoteResult.Failure("فشل رفع الصلاحية")

        auditRepository.record(
            actorDoctorId = actorDoctorId,
            actorName = actorName,
            action = AppConstants.AUDIT_ADMIN_PROMOTED,
            detail = "target=${target.fullName} rank=${updated.rank}"
        )
        return PromoteResult.Success(updated)
    }
}