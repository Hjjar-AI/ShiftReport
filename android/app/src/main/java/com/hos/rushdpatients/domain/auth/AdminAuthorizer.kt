package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.DoctorRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Re-checks admin authority against the live registry, not only navigation or cached UI state. */
@Singleton
class AdminAuthorizer @Inject constructor(
    private val sessionManager: SessionManager,
    private val doctorRepository: DoctorRepository
) {
    suspend fun requireAdmin(): Doctor {
        val session = sessionManager.current() ?: error("يجب تسجيل الدخول كمدير")
        val actor = doctorRepository.getById(session.doctorId)
            ?: error("المستخدم الحالي غير موجود")
        require(session.isAdmin && actor.isAdmin && !actor.isDeleted) {
            "هذه العملية متاحة للمدير فقط"
        }
        return actor
    }
}
