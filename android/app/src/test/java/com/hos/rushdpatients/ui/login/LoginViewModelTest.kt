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
    private val doctorSync = mockk<com.hos.rushdpatients.sync.DoctorSyncCoordinator>()
    private val recovery = mockk<PinRecoveryManager>(relaxed = true)
    private val projects = mockk<ProjectConfigStore> { every { current() } returns ProjectConfig(hospitalName = "تجريبي") }
    private val actor = Fixtures.doctor(telegramId = 123).copy(extraOptions = setOf("pin:synthetic-hash"))
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { doctors.getAll() } returns listOf(actor)
        coEvery { doctors.getActiveById(actor.id) } returns actor
        coEvery { pins.isEnrolled(any()) } returns true
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun vm() = LoginViewModel(text, mockk<Context>(), doctors, sessions, bootstrap, telegram,
        pins, mockk(relaxed = true), doctorSync, recovery, projects).also { store.put("login", it) }

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
        coVerify(exactly = 0) { telegram.beginVerification(any(), any()) }
    }
    @Test fun `doctor without Telegram identity cannot begin verification`() {
        coEvery { doctors.getActiveById(actor.id) } returns actor.copy(telegramId = null)
        coEvery { pins.isEnrolled(any()) } returns false
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
        coEvery { doctors.getActiveById(actor.id) } returns live
        val vm = vm(); vm.pickDoctor(actor); vm.loginWithPin("1234")
        verify(timeout = 2000) { sessions.start(match { it.doctorName == live.fullName && it.role == live.role && it.telegramId == live.telegramId }) }
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(2000) {
                while (vm.state.value.busy) kotlinx.coroutines.delay(1)
            }
        }
        assertFalse(vm.state.value.busy)
    }
    @Test fun `duplicate PIN submit cannot start second verification`() {
        coEvery { pins.verify(actor.id, any()) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.pickDoctor(actor); vm.loginWithPin("1234"); vm.loginWithPin("1234")
        coVerify(exactly = 1) { pins.verify(actor.id, any()) }
        assertTrue(vm.state.value.busy)
    }
    @Test fun `cancelling Telegram verification returns to chooser without login`() {
        coEvery { telegram.beginVerification(any(), any()) } just Runs
        coEvery { telegram.remainingSeconds(any()) } returns 300
        val vm = vm(); vm.verifyViaTelegram(actor)
        assertTrue(vm.state.value.step is LoginStep.Verifying)
        vm.cancelVerification()
        assertTrue(vm.state.value.step is LoginStep.PickDoctor)
        verify(exactly = 0) { sessions.start(any()) }
    }
    @Test fun refreshSuccessReplacesRosterAndRecomputesEnrollment() {
        val vm = vm()
        val newer = actor.copy(fullName = "د. بلوط", telegramId = 456)
        coEvery { doctorSync.refreshDoctorsForLogin() } returns Result.success(1)
        coEvery { doctors.getAll() } returns listOf(newer)
        coEvery { pins.isEnrolled(newer) } returns false
        vm.refreshDoctors()
        assertEquals(LoginStep.PickDoctor(listOf(newer)), vm.state.value.step)
        assertTrue(vm.state.value.enrolledDoctorIds.isEmpty())
        assertNotNull(vm.state.value.notice)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.refreshingDoctors)
        verify(exactly = 0) { sessions.start(any()) }
    }
    @Test fun refreshFailureRetainsCachedRosterAndEnrollment() {
        val vm = vm(); val before = vm.state.value
        coEvery { doctorSync.refreshDoctorsForLogin() } returns Result.failure(java.io.IOException("synthetic offline"))
        vm.refreshDoctors()
        assertEquals(before.step, vm.state.value.step)
        assertEquals(before.enrolledDoctorIds, vm.state.value.enrolledDoctorIds)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.refreshingDoctors)
        assertFalse(vm.state.value.busy)
        verify(exactly = 0) { sessions.start(any()) }
    }
    @Test fun duplicateRefreshCannotStartSecondRegistryFetch() {
        coEvery { doctorSync.refreshDoctorsForLogin() } coAnswers { awaitCancellation() }
        val vm = vm(); vm.refreshDoctors(); vm.refreshDoctors()
        assertTrue(vm.state.value.busy)
        assertTrue(vm.state.value.refreshingDoctors)
        coVerify(exactly = 1) { doctorSync.refreshDoctorsForLogin() }
    }
    @Test fun clearingViewModelCancelsRefreshAndClearsProgress() {
        coEvery { doctorSync.refreshDoctorsForLogin() } coAnswers { awaitCancellation() }
        val vm = vm(); val before = vm.state.value.step; vm.refreshDoctors(); store.clear()
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.refreshingDoctors)
        assertNull(vm.state.value.error)
        assertEquals(before, vm.state.value.step)
    }
    @Test fun refreshIsIgnoredOnPinEntry() {
        val vm = vm(); vm.pickDoctor(actor); vm.refreshDoctors()
        assertTrue(vm.state.value.step is LoginStep.EnterPin)
        coVerify(exactly = 0) { doctorSync.refreshDoctorsForLogin() }
    }
    @Test fun refreshIsIgnoredDuringRecoveryRejoinStep() {
        val vm = vm(); vm.startPinRecovery(actor.id); vm.refreshDoctors()
        assertEquals(LoginStep.RecoverPin(actor), vm.state.value.step)
        coVerify(exactly = 0) { doctorSync.refreshDoctorsForLogin() }
    }
    @Test fun successfulEmptyRefreshShowsEmptyChooserWithoutBootstrap() {
        val vm = vm()
        coEvery { doctorSync.refreshDoctorsForLogin() } returns Result.success(0)
        coEvery { doctors.getAll() } returns emptyList()
        vm.refreshDoctors()
        assertEquals(LoginStep.PickDoctor(emptyList()), vm.state.value.step)
        assertTrue(vm.state.value.enrolledDoctorIds.isEmpty())
        coVerify(exactly = 0) { bootstrap.bootstrap() }
    }
    @Test fun failedPostRefreshRosterReadRetainsVisibleCachedDoctors() {
        val vm = vm()
        coEvery { doctorSync.refreshDoctorsForLogin() } returns Result.success(1)
        coEvery { doctors.getAll() } throws java.io.IOException("synthetic unreadable registry")
        vm.refreshDoctors()
        assertEquals(LoginStep.PickDoctor(listOf(actor)), vm.state.value.step)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }
    @Test fun chooserRetainsAllFortyDoctorsWithoutCap() {
        val roster = (1..40).map { actor.copy(id = "tree-$it", fullName = "شجرة $it") }
        coEvery { doctors.getAll() } returns roster
        val vm = vm()
        assertEquals(roster, (vm.state.value.step as LoginStep.PickDoctor).doctors)
        assertEquals(roster.map { it.id }.toSet(), vm.state.value.enrolledDoctorIds)
    }
    @Test fun recoveryRequiresActiveDoctorBeforeOpeningRejoin() {
        coEvery { doctors.getActiveById(actor.id) } returns null
        val vm = vm(); vm.startPinRecovery(actor.id)
        assertTrue(vm.state.value.step is LoginStep.PickDoctor)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 0) { recovery.beginVerification(any(), any()) }
    }
    @Test fun recoveryStepUsesLiveRenamedDoctor() {
        val renamed = actor.copy(fullName = "د. بلوط")
        coEvery { doctors.getActiveById(actor.id) } returns renamed
        val vm = vm(); vm.startPinRecovery(actor.id)
        assertEquals(LoginStep.RecoverPin(renamed), vm.state.value.step)
        coVerify(exactly = 0) { telegram.beginVerification(any(), any()) }
    }
    @Test fun unsolicitedRecoveryHandleIsCancelledWithoutVerification() {
        val handle = PinRecoveryAuthorization()
        val vm = vm(); vm.onRecoveryRejoined(handle)
        verify(exactly = 1) { recovery.cancel(handle) }
        coVerify(exactly = 0) { recovery.beginVerification(any(), any()) }
        assertTrue(vm.state.value.step is LoginStep.PickDoctor)
    }
    @Test fun confirmedRecoveryStartsOnlyManagerGuardedVerification() {
        val handle = PinRecoveryAuthorization()
        coEvery { recovery.beginVerification(handle, actor.id) } returns actor
        coEvery { telegram.remainingSeconds(actor.id) } returns 300
        val vm = vm(); vm.startPinRecovery(actor.id); vm.onRecoveryRejoined(handle)
        assertEquals(LoginStep.Verifying(actor, 300), vm.state.value.step)
        coVerify(exactly = 1) { recovery.beginVerification(handle, actor.id) }
        coVerify(exactly = 0) { telegram.beginVerification(any(), any()) }
    }
    private fun recovering(): Pair<LoginViewModel, PinRecoveryAuthorization> {
        val handle = PinRecoveryAuthorization()
        coEvery { recovery.beginVerification(handle, actor.id) } returns actor
        coEvery { telegram.remainingSeconds(actor.id) } returns 300
        val vm = vm(); vm.startPinRecovery(actor.id); vm.onRecoveryRejoined(handle)
        return vm to handle
    }
    @Test fun leavingRecoveryCancelsAuthorityAndReturnsToRoster() {
        val (vm, handle) = recovering(); vm.cancelVerification()
        verify(exactly = 1) { recovery.cancel(handle) }
        assertTrue(vm.state.value.step is LoginStep.PickDoctor)
        verify(exactly = 0) { sessions.start(any()) }
    }
    @Test fun clearingViewModelRevokesRetainedRecoveryAuthority() {
        val (_, handle) = recovering(); store.clear()
        verify(exactly = 1) { recovery.cancel(handle) }
    }
    @Test fun incorrectRecoveryCodeRetainsVerificationWithoutOpeningPinEditor() {
        val (vm, handle) = recovering()
        coEvery { recovery.verify(handle, actor.id, 123, "synthetic code") } returns LoginVerificationResult.Incorrect
        vm.verifyTelegramCode("synthetic code")
        assertTrue(vm.state.value.step is LoginStep.Verifying)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 0) { pins.enrollVerified(any(), any(), any(), any()) }
        verify(exactly = 0) { sessions.start(any()) }
    }
    @Test fun exhaustedRecoveryCodeReturnsRetryFailureWithoutEnrollment() {
        val (vm, handle) = recovering()
        coEvery { recovery.verify(handle, actor.id, 123, "synthetic code") } returns LoginVerificationResult.Exhausted
        vm.verifyTelegramCode("synthetic code")
        assertTrue(vm.state.value.step is LoginStep.VerifyFailed)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 0) { recovery.replacePin(any(), any(), any()) }
    }
    @Test fun successfulRecoveryProofOpensPinEditorEvenWhenAlreadyEnrolled() {
        val (vm, handle) = recovering()
        coEvery { recovery.verify(handle, actor.id, 123, "synthetic code") } returns LoginVerificationResult.Success(123)
        coEvery { recovery.verifiedDoctor(handle) } returns actor
        vm.verifyTelegramCode("synthetic code")
        assertEquals(LoginStep.SetPin(actor, 123), vm.state.value.step)
        verify(exactly = 0) { sessions.start(any()) }
    }
    private fun recoveryPinEditor(): Pair<LoginViewModel, PinRecoveryAuthorization> {
        val (vm, handle) = recovering()
        coEvery { recovery.verify(handle, actor.id, 123, "synthetic code") } returns LoginVerificationResult.Success(123)
        coEvery { recovery.verifiedDoctor(handle) } returns actor
        vm.verifyTelegramCode("synthetic code")
        return vm to handle
    }
    @Test fun mismatchedPinConfirmationCannotReplaceOrEnroll() {
        val (vm, _) = recoveryPinEditor(); vm.setPin(actor, 123, "2468", "1357")
        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { recovery.replacePin(any(), any(), any()) }
        coVerify(exactly = 0) { pins.enrollVerified(any(), any(), any(), any()) }
    }
    @Test fun nonNumericPinCannotReplaceRecoveryPin() {
        val (vm, _) = recoveryPinEditor(); vm.setPin(actor, 123, "tree", "tree")
        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { recovery.replacePin(any(), any(), any()) }
    }
    @Test fun anotherDoctorCannotUseOpenRecoveryPinEditor() {
        val (vm, _) = recoveryPinEditor(); vm.setPin(actor.copy(id = "other"), 123, "2468", "2468")
        coVerify(exactly = 0) { recovery.replacePin(any(), any(), any()) }
    }
    @Test fun anotherTelegramIdentityCannotUseOpenRecoveryPinEditor() {
        val (vm, _) = recoveryPinEditor(); vm.setPin(actor, 456, "2468", "2468")
        coVerify(exactly = 0) { recovery.replacePin(any(), any(), any()) }
    }
    @Test fun failedRecoveryReplacementDoesNotFallBackToOrdinaryEnrollment() {
        val (vm, handle) = recoveryPinEditor()
        coEvery { recovery.replacePin(handle, actor.id, "2468") } throws IllegalStateException("synthetic expired authority")
        vm.setPin(actor, 123, "2468", "2468")
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 0) { pins.enrollVerified(any(), any(), any(), any()) }
        verify(exactly = 0) { sessions.start(any()) }
    }
    @Test fun duplicateRecoveryReplacementCannotSubmitTwice() {
        val (vm, handle) = recoveryPinEditor()
        coEvery { recovery.replacePin(handle, actor.id, "2468") } coAnswers { awaitCancellation() }
        vm.setPin(actor, 123, "2468", "2468"); vm.setPin(actor, 123, "2468", "2468")
        assertTrue(vm.state.value.busy)
        coVerify(exactly = 1) { recovery.replacePin(handle, actor.id, "2468") }
    }
}
