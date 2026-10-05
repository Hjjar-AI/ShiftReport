package com.hos.rushdpatients.domain.usecase

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoteAdminUseCase @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val auditRepository: AuditRepository
) {

    suspend fun execute(
        targetId: String,
        actorId: String,
        actorDoctorId: String?,
        actorName: String?
    ): PromoteResult {
        val actor = doctorRepository.getById(actorId)
            ?: return PromoteResult.Failure("المستخدم الحالي غير موجود")
        val target = doctorRepository.getById(targetId)
            ?: return PromoteResult.Failure("المدير غير موجود")

        if (!actor.role.isAdmin) return PromoteResult.Failure("ليس لديك صلاحية")
        if (target.isPermanentAdmin && !actor.isPermanentAdmin) {
            return PromoteResult.Failure("لا يمكن إزالة مدير دائم")
        }
        if (actor.rank <= target.rank && !actor.isPermanentAdmin) {
            return PromoteResult.Failure("لا يمكنك إزالة مدير أعلى رتبة")
        }

        val updated = doctorRepository.demoteFromAdmin(target.id)
            ?: return PromoteResult.Failure("فشل إزالة الصلاحية")

        auditRepository.record(
            actorDoctorId = actorDoctorId,
            actorName = actorName,
            action = AppConstants.AUDIT_ADMIN_DEMOTED,
            detail = "target=${target.fullName}"
        )
        return PromoteResult.Success(updated)
    }
}