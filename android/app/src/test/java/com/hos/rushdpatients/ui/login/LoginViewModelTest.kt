package com.hos.rushdpatients.ui.login

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.hos.rushdpatients.config.ProjectConfig
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val store = ViewModelStore()
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val doctors = mockk<DoctorRepository>()
    private val sessions = mockk<SessionManager>(relaxed = true)
    private val bootstrap = mockk<BootstrapManager>()
    private val telegram = mockk<TelegramLoginManager>()
    private val pins = mockk<LocalPinLoginManager>()
    private val projects = mockk<ProjectConfigStore> { every { current() } returns ProjectConfig(hospitalName = "تجريبي") }
    private val actor = Fixtures.doctor(telegramId = 123).copy(extraOptions = setOf("pin:synthetic-hash"))
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { doctors.getAll() } returns listOf(actor)
        every { telegram.generateNonce() } returns "SYNTHETIC"
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun vm() = LoginViewModel(text, mockk<Context>(), doctors, sessions, bootstrap, telegram,
        pins, mockk(), mockk(relaxed = true), mockk(relaxed = true), projects).also { store.put("login", it) }

    @Test fun `local registry loads without network bootstrap`() {
        val vm = vm()
        assertEquals(LoginStep.PickDoctor(listOf(actor)), vm.state.value.step)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 0) { bootstrap.bootstrap() }
    }
    @Test fun `missing project index exposes bootstrap failure without starting session`() {
        coEvery { doctors.getAll() } returns emptyList()
        coEvery { bootstrap.bootstrap() } returns BootstrapResult.NoPin
        val vm = vm()
        assertTrue(vm.state.value.step is LoginStep.BootstrapFailed)
        assertFalse(vm.state.value.busy)
        verify(exactly = 0) { sessions.start(any()) }
    }
    @Test fun `bootstrap error becomes visible retry state`() {
        coEvery { doctors.getAll() } returns emptyList()
        coEvery { bootstrap.bootstrap() } returns BootstrapResult.Failed("synthetic offline")
        val vm = vm()
        assertEquals(LoginStep.BootstrapFailed("synthetic offline"), vm.state.value.step)
        assertFalse(vm.state.value.busy)
    }
    @Test fun `enrolled doctor opens PIN screen without Telegram verification`() {
        val vm = vm(); vm.pickDoctor(actor)
        assertEquals(LoginStep.EnterPin(actor), vm.state.value.step)
        coVerify(exactly = 0) { telegram.awaitVerification(any(), any(), any()) }
    }
    @Test fun `doctor without Telegram identity cannot begin verification`() {
        val vm = vm(); vm.pickDoctor(actor.copy(telegramId = null))
        assertNotNull(vm.state.value.error)
        verify(exactly = 0) { sessions.start(any()) }
    }
    @Test fun `PIN failure clears busy state without starting session`() {
        coEvery { pins.verify(actor.id, any()) } throws IllegalArgumentException("synthetic wrong PIN")
        val vm = vm(); vm.pickDoctor(actor); vm.loginWithPin("0000")
        assertFalse(vm.state.value.busy)
        assertEquals("synthetic wrong PIN", vm.state.value.error)
        verify(exactly = 0) { sessions.start(any()) }
    }
    @Test fun `successful PIN uses live returned doctor identity`() {
        val live = actor.copy(fullName = "اسم محدث", rank = 0)
        coEvery { pins.verify(actor.id, any()) } returns live
        val vm = vm(); vm.pickDoctor(actor); vm.loginWithPin("1234")
        verify { sessions.start(match { it.doctorName == live.fullName && it.role == live.role && it.telegramId == live.telegramId }) }
        assertFalse(vm.state.value.busy)
    }
    @Test fun `duplicate PIN submit cannot start second verification`() {
        coEvery { pins.verify(actor.id, any()) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.pickDoctor(actor); vm.loginWithPin("1234"); vm.loginWithPin("1234")
        coVerify(exactly = 1) { pins.verify(actor.id, any()) }
        assertTrue(vm.state.value.busy)
    }
    @Test fun `cancelling Telegram verification returns to chooser without login`() {
        coEvery { telegram.awaitVerification(any(), any(), any()) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.verifyViaTelegram(actor)
        assertTrue(vm.state.value.step is LoginStep.Verifying)
        vm.cancelVerification()
        assertTrue(vm.state.value.step is LoginStep.PickDoctor)
        verify(exactly = 0) { sessions.start(any()) }
    }
}
