package com.hos.rushdpatients.domain.auth

import android.os.SystemClock
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.sync.DoctorSyncCoordinator
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.util.DispatcherProvider
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real recovery coordinator; all transport/storage boundaries are synthetic mocks. */
class PinRecoveryManagerTest {
    private val connections = mockk<ProjectConnectionManager>()
    private val projects = mockk<ProjectConfigStore>()
    private val doctors = mockk<DoctorRepository>()
    private val sync = mockk<DoctorSyncCoordinator>()
    private val telegram = mockk<TelegramLoginManager>()
    private val pins = mockk<LocalPinLoginManager>()
    private val dispatchers = mockk<DispatcherProvider> { every { io } returns Dispatchers.Unconfined }
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "synthetic recovery warning" }
    private val actor = Fixtures.doctor(telegramId = 123).copy(extraOptions = setOf("pin:synthetic-original"))
    private var current: Doctor? = actor
    private var project = ProjectConfig(initialized = true, hospitalName = "synthetic ward")
    private var now = 10_000L
    private val manager = PinRecoveryManager(connections, projects, doctors, sync, telegram, pins, dispatchers, text)
    private fun change(protected: Boolean = true) = ConnectionChange(project, "synthetic replacement", fromProtectedFile = protected)
    @Before fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { now }
        every { projects.current() } answers { project }
        coEvery { connections.apply(any()) } just Runs
        coEvery { sync.refreshDoctorsForLogin() } returns Result.success(1)
        coEvery { doctors.getActiveById(actor.id) } coAnswers { current }
        coEvery { telegram.beginVerification(any(), any()) } just Runs
        coEvery { telegram.verify(any(), any(), any()) } returns LoginVerificationResult.Success(123)
        coEvery { pins.enrollVerified(any(), any(), any(), any()) } coAnswers {
            val live = requireNotNull(current)
            arg<(Doctor) -> Unit>(3)(live)
            live.copy(extraOptions = live.options.withPinHash("synthetic-replacement"))
        }
    }
    @After fun cleanup() { unmockkStatic(SystemClock::class) }
    private suspend fun grant() = manager.confirmRejoin(change(), actor.id)
    private suspend fun verified(): PinRecoveryAuthorization = grant().also {
        manager.beginVerification(it, actor.id)
        assertEquals(LoginVerificationResult.Success(123), manager.verify(it, actor.id, 123, "synthetic code"))
    }
    private fun rejects(action: suspend () -> Unit) {
        assertThrows(IllegalStateException::class.java) { runBlocking { action() } }
    }
    private fun invalid(action: suspend () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { action() } }
    }
    @Test fun manualTokenChangeCannotAuthorizeRecovery() = runBlocking<Unit> {
        invalid { manager.confirmRejoin(change(false), actor.id) }
        coVerify(exactly = 0) { connections.apply(any()) }
        coVerify(exactly = 0) { sync.refreshDoctorsForLogin() }
    }
    @Test fun protectedRejoinAppliesBeforeRegistryRefreshAndIdentityRead() = runBlocking<Unit> {
        grant()
        coVerifyOrder { connections.apply(any()); sync.refreshDoctorsForLogin(); doctors.getActiveById(actor.id) }
        coVerify(exactly = 0) { pins.enrollVerified(any(), any(), any(), any()) }
    }
    @Test fun failedRejoinDoesNotRefreshRegistryOrIssueAuthority() = runBlocking<Unit> {
        coEvery { connections.apply(any()) } throws IllegalStateException("synthetic rejection")
        rejects { grant() }
        coVerify(exactly = 0) { sync.refreshDoctorsForLogin() }
        coVerify(exactly = 0) { doctors.getActiveById(any()) }
    }
    @Test fun failedRegistryRefreshDoesNotReadOrReplaceDoctor() = runBlocking<Unit> {
        coEvery { sync.refreshDoctorsForLogin() } returns Result.failure(IllegalStateException("synthetic offline"))
        rejects { grant() }
        coVerify(exactly = 0) { doctors.getActiveById(any()) }
        coVerify(exactly = 0) { pins.enrollVerified(any(), any(), any(), any()) }
    }
    @Test fun connectionChangedDuringRefreshCannotIssueAuthority() = runBlocking<Unit> {
        coEvery { sync.refreshDoctorsForLogin() } coAnswers { project = project.copy(hospitalName = "changed"); Result.success(1) }
        rejects { grant() }
        coVerify(exactly = 0) { doctors.getActiveById(any()) }
    }
    @Test fun deletedDoctorCannotReceiveRecoveryAuthority() = runBlocking<Unit> {
        current = null
        invalid { grant() }
        coVerify(exactly = 0) { telegram.beginVerification(any(), any()) }
    }
    @Test fun unlinkedDoctorCannotReceiveRecoveryAuthority() = runBlocking<Unit> {
        current = actor.copy(telegramId = null)
        invalid { grant() }
    }
    @Test fun fabricatedHandleCannotBeginVerification() = runBlocking<Unit> {
        grant()
        invalid { manager.beginVerification(PinRecoveryAuthorization(), actor.id) }
        coVerify(exactly = 0) { telegram.beginVerification(any(), any()) }
    }
    @Test fun wrongDoctorCannotUseAnotherDoctorsGrant() = runBlocking<Unit> {
        val handle = grant()
        invalid { manager.beginVerification(handle, "other-doctor") }
        coVerify(exactly = 0) { telegram.beginVerification(any(), any()) }
    }
    @Test fun correctGrantBeginsVerificationUsingRefreshedIdentity() = runBlocking<Unit> {
        val handle = grant()
        assertEquals(actor, manager.beginVerification(handle, actor.id))
        coVerify(exactly = 1) { telegram.beginVerification(actor.id, 123) }
    }
    @Test fun newGrantInvalidatesPreviousHandle() = runBlocking<Unit> {
        val old = grant(); val newest = grant()
        invalid { manager.beginVerification(old, actor.id) }
        assertEquals(actor, manager.beginVerification(newest, actor.id))
    }
    @Test fun failedNewRejoinStillInvalidatesPreviousHandle() = runBlocking<Unit> {
        val old = grant()
        coEvery { connections.apply(any()) } throws IllegalStateException("synthetic rejected rejoin")
        rejects { grant() }
        invalid { manager.beginVerification(old, actor.id) }
    }
    @Test fun cancellingCurrentHandleRevokesVerification() = runBlocking<Unit> {
        val handle = grant(); manager.cancel(handle)
        invalid { manager.beginVerification(handle, actor.id) }
    }
    @Test fun cancellingForeignHandleDoesNotRevokeCurrentGrant() = runBlocking<Unit> {
        val handle = grant(); manager.cancel(PinRecoveryAuthorization())
        assertEquals(actor, manager.beginVerification(handle, actor.id))
    }
    @Test fun cancellingOldHandleDoesNotRevokeReplacementGrant() = runBlocking<Unit> {
        val old = grant(); val newest = grant(); manager.cancel(old)
        assertEquals(actor, manager.beginVerification(newest, actor.id))
    }
    @Test fun grantRemainsValidImmediatelyBeforeDeadline() = runBlocking<Unit> {
        val handle = grant(); now += AppConstants.TELEGRAM_LOGIN_TIMEOUT_MS - 1
        assertEquals(actor, manager.beginVerification(handle, actor.id))
    }
    @Test fun grantExpiresExactlyAtDeadline() = runBlocking<Unit> {
        val handle = grant(); now += AppConstants.TELEGRAM_LOGIN_TIMEOUT_MS
        rejects { manager.beginVerification(handle, actor.id) }
        invalid { manager.beginVerification(handle, actor.id) }
    }
    @Test fun changedProjectRevokesGrantPermanently() = runBlocking<Unit> {
        val handle = grant(); val original = project; project = project.copy(csvTopicId = 55)
        rejects { manager.beginVerification(handle, actor.id) }
        project = original
        invalid { manager.beginVerification(handle, actor.id) }
    }
    @Test fun removedDoctorRevokesGrantPermanently() = runBlocking<Unit> {
        val handle = grant(); current = null
        rejects { manager.beginVerification(handle, actor.id) }
        current = actor
        invalid { manager.beginVerification(handle, actor.id) }
    }
    @Test fun changedTelegramIdentityRevokesGrant() = runBlocking<Unit> {
        val handle = grant(); current = actor.copy(telegramId = 456)
        rejects { manager.beginVerification(handle, actor.id) }
        coVerify(exactly = 0) { telegram.beginVerification(any(), any()) }
    }
    @Test fun changedStoredPinRevokesGrant() = runBlocking<Unit> {
        val handle = grant(); current = actor.copy(extraOptions = setOf("pin:another-synthetic-hash"))
        rejects { manager.beginVerification(handle, actor.id) }
    }
    @Test fun harmlessNameAndRoleChangesReturnCurrentDoctor() = runBlocking<Unit> {
        val handle = grant(); current = actor.copy(fullName = "بلوط", rank = 1)
        assertEquals(current, manager.beginVerification(handle, actor.id))
    }
    @Test fun verificationCannotUseWrongTelegramAccount() = runBlocking<Unit> {
        val handle = grant()
        invalid { manager.verify(handle, actor.id, 456, "synthetic code") }
        coVerify(exactly = 0) { telegram.verify(any(), any(), any()) }
    }
    @Test fun incorrectCodeDoesNotAuthorizePinReplacement() = runBlocking<Unit> {
        val handle = grant()
        coEvery { telegram.verify(any(), any(), any()) } returns LoginVerificationResult.Incorrect
        assertEquals(LoginVerificationResult.Incorrect, manager.verify(handle, actor.id, 123, "synthetic wrong code"))
        invalid { manager.replacePin(handle, actor.id, "2468") }
        coVerify(exactly = 0) { pins.enrollVerified(any(), any(), any(), any()) }
    }
    @Test fun timedOutCodeConsumesRecoveryGrant() = runBlocking<Unit> {
        val handle = grant()
        coEvery { telegram.verify(any(), any(), any()) } returns LoginVerificationResult.Timeout
        assertEquals(LoginVerificationResult.Timeout, manager.verify(handle, actor.id, 123, "synthetic code"))
        invalid { manager.beginVerification(handle, actor.id) }
    }
    @Test fun exhaustedCodesConsumeRecoveryGrant() = runBlocking<Unit> {
        val handle = grant()
        coEvery { telegram.verify(any(), any(), any()) } returns LoginVerificationResult.Exhausted
        assertEquals(LoginVerificationResult.Exhausted, manager.verify(handle, actor.id, 123, "synthetic code"))
        invalid { manager.verifiedDoctor(handle) }
    }
    @Test fun successReturnsCurrentVerifiedDoctorWithoutWritingPin() = runBlocking<Unit> {
        val handle = verified()
        assertEquals(actor, manager.verifiedDoctor(handle))
        coVerify(exactly = 0) { pins.enrollVerified(any(), any(), any(), any()) }
    }
    @Test fun unverifiedGrantCannotReadVerifiedDoctor() = runBlocking<Unit> {
        val handle = grant(); invalid { manager.verifiedDoctor(handle) }
    }
    @Test fun restartingVerificationClearsEarlierProof() = runBlocking<Unit> {
        val handle = verified(); manager.beginVerification(handle, actor.id)
        invalid { manager.verifiedDoctor(handle) }
        invalid { manager.replacePin(handle, actor.id, "2468") }
    }
    @Test fun successfulReplacementConsumesGrantExactlyOnce() = runBlocking<Unit> {
        val handle = verified()
        val result = manager.replacePin(handle, actor.id, "2468")
        assertEquals("synthetic-replacement", result.options.pinHash)
        invalid { manager.replacePin(handle, actor.id, "2468") }
        coVerify(exactly = 1) { pins.enrollVerified(actor.id, 123, "2468", any()) }
    }
    @Test fun replacementCannotTargetAnotherDoctor() = runBlocking<Unit> {
        val handle = verified(); invalid { manager.replacePin(handle, "other-doctor", "2468") }
        coVerify(exactly = 0) { pins.enrollVerified(any(), any(), any(), any()) }
    }
    @Test fun pinChangedInsideWriteBoundaryIsRejected() = runBlocking<Unit> {
        val handle = verified()
        coEvery { pins.enrollVerified(any(), any(), any(), any()) } coAnswers {
            arg<(Doctor) -> Unit>(3)(actor.copy(extraOptions = setOf("pin:newer-synthetic-hash")))
            actor
        }
        invalid { manager.replacePin(handle, actor.id, "2468") }
    }
    @Test fun projectChangedInsideWriteBoundaryIsRejected() = runBlocking<Unit> {
        val handle = verified()
        coEvery { pins.enrollVerified(any(), any(), any(), any()) } coAnswers {
            project = project.copy(doctorsTopicId = 88)
            arg<(Doctor) -> Unit>(3)(actor)
            actor
        }
        rejects { manager.replacePin(handle, actor.id, "2468") }
    }
    @Test fun expiryDuringTelegramVerificationDoesNotAuthorizeWrite() = runBlocking<Unit> {
        val handle = grant()
        coEvery { telegram.verify(any(), any(), any()) } coAnswers {
            now += AppConstants.TELEGRAM_LOGIN_TIMEOUT_MS
            LoginVerificationResult.Success(123)
        }
        rejects { manager.verify(handle, actor.id, 123, "synthetic code") }
        invalid { manager.replacePin(handle, actor.id, "2468") }
    }
    @Test fun transportCancellationRemainsCancellationAndNeverWritesPin() = runBlocking<Unit> {
        val handle = grant()
        coEvery { telegram.beginVerification(any(), any()) } throws CancellationException("synthetic cancellation")
        assertThrows(CancellationException::class.java) { runBlocking { manager.beginVerification(handle, actor.id) } }
        coVerify(exactly = 0) { pins.enrollVerified(any(), any(), any(), any()) }
    }
}
