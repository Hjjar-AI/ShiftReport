package com.hos.rushdpatients.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.model.Role
import com.hos.rushdpatients.data.repository.DoctorMutationRepository
import com.hos.rushdpatients.domain.auth.*
import com.hos.rushdpatients.sync.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import java.time.Instant

abstract class SettingsProjectFixture {
    protected val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    protected val state = MutableStateFlow(SettingsUiState())
    protected val emptyStart = mockk<EmptyStartCoordinator>()
    protected val sessions = mockk<SessionManager>()
    protected val mutations = mockk<DoctorMutationRepository>()
    protected val provisioning = mockk<ProjectProvisioningManager>(relaxed = true)
    protected val projects = mockk<ProjectConfigStore>()
    protected val admin = mockk<AdminAuthorizer>()
    protected val handle = SavedStateHandle()
    protected val actor = Fixtures.doctor(rank = 1)
    protected val review = EmptyStartReview(SyncState(), actor.id, emptyList(), emptyList(), null,
        Fixtures.shift(), "مراجعة حذف تجريبية")
    protected var reloads = 0
    protected var invalidations = 0
    internal fun operations(scope: CoroutineScope) = SettingsProjectOperations(text, state, scope,
        emptyStart, sessions, mutations, provisioning, projects, admin, handle,
        { reloads++ }, { invalidations++ })
    @Before fun configureProject() {
        every { projects.current() } returns ProjectConfig(hospitalName = "مشفى تجريبي")
        every { sessions.current() } returns Session(actor.id, actor.fullName, null, Role.ADMIN, Instant.EPOCH)
        coEvery { admin.requireAdmin() } returns actor
    }
}
