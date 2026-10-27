package com.hos.rushdpatients.ui

import androidx.lifecycle.ViewModelStore
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootUnlockSafetyTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "synthetic unlock warning" }
    private val doctor = Fixtures.doctor(telegramId = 123)
    private var session: Session? = Session(doctor.id, doctor.fullName, doctor.telegramId, doctor.role, Fixtures.timestamp)
    private val sessions = mockk<SessionManager>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val biometrics = mockk<BiometricHelper>()
    private val doctors = mockk<DoctorRepository>()
    private val projects = mockk<ProjectConfigStore>()
    private val pin = mockk<LocalPinLoginManager>()
    private val store = ViewModelStore()
    private fun model() = RootViewModel(text, sessions, settings, biometrics, mockk(relaxed = true), doctors, pin, projects)
        .also { store.put("root", it) }
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { sessions.current() } answers { session }
        every { sessions.sessionFlow } returns MutableStateFlow(session)
        every { projects.config } returns MutableStateFlow(ProjectConfig())
        every { biometrics.isAvailable() } returns true
        coEvery { settings.getBoolean(AppConstants.SETTING_BIOMETRIC_ENABLED, true) } returns true
        coEvery { settings.getAutoLockMinutes() } returns 5
        coEvery { doctors.getActiveById(doctor.id) } returns doctor
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    @Test fun `biometric error and short background resume cannot clear an existing lock`() = runTest {
        val model = model()
        assertTrue(model.shouldPromptUnlock())
        model.biometricFailed(); model.onBackground()
        assertTrue(model.shouldPromptUnlock())
        assertNotNull(model.unlockError.value)
        verify(exactly = 0) { sessions.markUnlocked() }
    }
    @Test fun `unenrolled or opted out biometrics retain required PIN lock`() = runTest {
        val model = model()
        every { biometrics.isAvailable() } returns false
        assertTrue(model.shouldPromptUnlock()); assertFalse(model.biometricAvailable.value)
        every { biometrics.isAvailable() } returns true
        coEvery { settings.getBoolean(AppConstants.SETTING_BIOMETRIC_ENABLED, true) } returns false
        assertTrue(model.shouldPromptUnlock()); assertFalse(model.biometricAvailable.value)
        verify(exactly = 0) { sessions.markUnlocked() }
    }
    @Test fun `stale biometric success cannot unlock another session`() = runTest {
        val expected = requireNotNull(session)
        val model = model()
        session = expected.copy(doctorId = "another")
        model.markUnlockedAfterBiometric(expected); runCurrent()
        verify(exactly = 0) { sessions.markUnlocked() }
        coVerify(exactly = 0) { doctors.getActiveById(any()) }
    }
    @Test fun `session changed during biometric identity read cannot unlock`() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { doctors.getActiveById(doctor.id) } coAnswers { release.await(); doctor }
        val expected = requireNotNull(session)
        val model = model()
        model.markUnlockedAfterBiometric(expected)
        assertTrue(model.unlocking.value)
        session = expected.copy(doctorId = "another")
        release.complete(Unit); runCurrent()
        verify(exactly = 0) { sessions.markUnlocked() }
        assertFalse(model.unlocking.value)
    }
    @Test fun `biometric identity removal signs out rather than unlocking`() = runTest {
        val expected = requireNotNull(session)
        coEvery { doctors.getActiveById(doctor.id) } returns null
        val model = model()
        model.markUnlockedAfterBiometric(expected); runCurrent()
        verify(exactly = 1) { sessions.clear() }
        verify(exactly = 0) { sessions.markUnlocked() }
        assertFalse(model.unlocking.value)
    }
    @Test fun `biometric database failure preserves lock and offers PIN retry`() = runTest {
        val expected = requireNotNull(session)
        coEvery { doctors.getActiveById(doctor.id) } throws java.io.IOException("synthetic failure")
        val model = model()
        model.markUnlockedAfterBiometric(expected); runCurrent()
        assertNotNull(model.unlockError.value)
        assertFalse(model.unlocking.value)
        verify(exactly = 0) { sessions.markUnlocked() }
        verify(exactly = 0) { sessions.clear() }
    }
}
