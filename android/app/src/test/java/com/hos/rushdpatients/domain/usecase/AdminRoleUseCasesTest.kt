package com.hos.rushdpatients.domain.usecase

import com.hos.rushdpatients.data.repository.DoctorMutationRepository
import com.hos.rushdpatients.testutil.Fixtures
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdminRoleUseCasesTest {
    private val mutations = mockk<DoctorMutationRepository>()
    private val expected = Fixtures.doctor()
    @Test fun `promotion delegates expected snapshot and actor to protected repository`() = runBlocking<Unit> {
        val promoted = expected.copy(rank = 1)
        coEvery { mutations.promote(expected, "مدير تجريبي", "actor") } returns promoted
        assertEquals(PromoteResult.Success(promoted), PromoteAdminUseCase(mutations).execute(expected, "مدير تجريبي", "actor"))
        coVerify(exactly = 1) { mutations.promote(expected, "مدير تجريبي", "actor") }
    }
    @Test fun `demotion delegates expected snapshot and actor to protected repository`() = runBlocking<Unit> {
        coEvery { mutations.demote(expected, "actor") } returns expected
        assertEquals(PromoteResult.Success(expected), DemoteAdminUseCase(mutations).execute(expected, "actor"))
        coVerify(exactly = 1) { mutations.demote(expected, "actor") }
    }
    @Test fun `repository rejection becomes failure for both actions`() = runBlocking<Unit> {
        coEvery { mutations.promote(any(), any(), any()) } throws IllegalStateException("stale or unauthorized")
        coEvery { mutations.demote(any(), any()) } throws IllegalStateException("stale or unauthorized")
        val failure = PromoteResult.Failure("stale or unauthorized")
        assertEquals(failure, PromoteAdminUseCase(mutations).execute(expected, null, null))
        assertEquals(failure, DemoteAdminUseCase(mutations).execute(expected, null))
    }
    @Test fun `cancellation propagates from both role mutations`() = runBlocking<Unit> {
        coEvery { mutations.promote(any(), any(), any()) } throws CancellationException("cancelled")
        coEvery { mutations.demote(any(), any()) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { PromoteAdminUseCase(mutations).execute(expected, null, null) } }
        assertThrows(CancellationException::class.java) { runBlocking { DemoteAdminUseCase(mutations).execute(expected, null) } }
    }
}
