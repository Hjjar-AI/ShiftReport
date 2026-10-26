package com.hos.rushdpatients.domain.usecase

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.DoctorMutationRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PromoteResult {
    data class Success(val doctor: Doctor) : PromoteResult
    data class Failure(val reason: String) : PromoteResult
}

@Singleton
class PromoteAdminUseCase @Inject constructor(
    private val doctorMutations: DoctorMutationRepository
) {
    suspend fun execute(expected: Doctor, customTitle: String?, actorDoctorId: String?): PromoteResult = try {
        PromoteResult.Success(doctorMutations.promote(expected, customTitle, actorDoctorId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PromoteResult.Failure(e.message ?: "تعذر تغيير صلاحية المدير")
    }
}
