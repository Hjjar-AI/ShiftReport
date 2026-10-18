package com.hos.rushdpatients.domain.usecase

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.DoctorMutationRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoteAdminUseCase @Inject constructor(
    private val doctorMutations: DoctorMutationRepository
) {
    suspend fun execute(expected: Doctor, actorDoctorId: String?): PromoteResult = try {
        PromoteResult.Success(doctorMutations.demote(expected, actorDoctorId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PromoteResult.Failure(e.message ?: "تعذر تغيير صلاحية المدير")
    }
}
