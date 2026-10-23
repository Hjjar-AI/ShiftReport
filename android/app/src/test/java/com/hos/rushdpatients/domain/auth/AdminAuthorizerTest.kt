package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

import com.hos.rushdpatients.data.repository.DoctorRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest

class AdminAuthorizerTest {
    private val sessions = mockk<SessionManager>()
    private val doctors = mockk<DoctorRepository>()
    private val authorizer = AdminAuthorizer(sessions, doctors)
    private val session = Session("doc-1", "أرز", null, Role.ADMIN, Instant.EPOCH)
    @Test fun `active admin is returned from live registry`() = runTest {
        val actor = Fixtures.doctor(rank = 1)
        every { sessions.current() } returns session
        coEvery { doctors.getById("doc-1") } returns actor
        assertEquals(actor, authorizer.requireAdmin())
        coVerify(exactly = 1) { doctors.getById("doc-1") }
    }
    @Test fun `cached admin cannot authorize deleted demoted or missing actor`() = runTest {
        every { sessions.current() } returns session
        for (actor in listOf(null, Fixtures.doctor(), Fixtures.doctor(rank = 1).copy(deletedAt = Instant.EPOCH))) {
            coEvery { doctors.getById("doc-1") } returns actor
            try { authorizer.requireAdmin(); fail("Unauthorized actor accepted") }
            catch (_: IllegalStateException) { }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun `session also needs admin authority and must exist`() = runTest {
        coEvery { doctors.getById("doc-1") } returns Fixtures.doctor(rank = 1)
        every { sessions.current() } returns session.copy(role = Role.NON_ADMIN)
        try { authorizer.requireAdmin(); fail("Non-admin session accepted") } catch (_: IllegalArgumentException) { }
        every { sessions.current() } returns null
        try { authorizer.requireAdmin(); fail("Missing session accepted") } catch (_: IllegalStateException) { }
    }
}
