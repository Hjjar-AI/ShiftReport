package com.hos.rushdpatients.ui.admin

import com.hos.rushdpatients.data.model.Role
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.usecase.*
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.testutil.ViewModelFixture
import io.mockk.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AdminViewModelTest : ViewModelFixture() {
    private val actor = Fixtures.doctor(id = "actor", rank = 1)
    private val target = Fixtures.doctor(id = "target", rank = 0)
    private val registry = MutableStateFlow(listOf(actor, target))
    private val doctors = mockk<DoctorRepository>()
    private val sessions = mockk<SessionManager>()
    private val promote = mockk<PromoteAdminUseCase>()
    private val demote = mockk<DemoteAdminUseCase>()
    private val sync = mockk<SyncService>()
    private fun vm() = keep(AdminViewModel(text, doctors, sessions, promote, demote, sync))
    private fun review() = com.hos.rushdpatients.sync.PermanentAdminTransferReview(
        "أرز", "بلوط", 222, actor.id, target.id, com.hos.rushdpatients.sync.SyncState(), "before", "after", { true })
    @Before fun seedRegistry() {
        every { doctors.observeAll() } returns registry
        every { sessions.current() } returns Session(actor.id, actor.fullName, null, Role.ADMIN, Instant.EPOCH)
    }
    @Test fun registrySeparatesAdminsAndCliniciansAndUsesLiveActor() {
        val vm = vm()
        assertFalse(vm.state.value.loading)
        assertEquals(listOf(actor), vm.state.value.admins)
        assertEquals(listOf(target), vm.state.value.nonAdmins)
        assertEquals(actor, vm.state.value.currentActor)
        registry.value = listOf(actor.copy(fullName = "أرز محدث"), target)
        assertEquals("أرز محدث", vm.state.value.currentActor!!.fullName)
    }
    @Test fun staleSessionAdminRoleDoesNotBypassRegistryDemotion() {
        registry.value = listOf(actor.copy(rank = 0), target)
        val vm = vm(); val messages = mutableListOf<String>()
        vm.assignAdmin(target, null, messages::add); vm.removeAdmin(target, messages::add)
        assertEquals(2, messages.size)
        coVerify(exactly = 0) { promote.execute(any(), any(), any()) }
        coVerify(exactly = 0) { demote.execute(any(), any()) }
    }
    @Test fun absentSessionCannotMutateAdminRoles() {
        every { sessions.current() } returns null
        val vm = vm(); val messages = mutableListOf<String>()
        vm.assignAdmin(target, null, messages::add); vm.removeAdmin(actor, messages::add)
        assertNull(vm.state.value.currentActor)
        assertEquals(2, messages.size)
        coVerify(exactly = 0) { promote.execute(any(), any(), any()) }
        coVerify(exactly = 0) { demote.execute(any(), any()) }
    }
    @Test fun missingActorCannotUseStaleSessionAuthority() {
        registry.value = listOf(target)
        val vm = vm(); vm.assignAdmin(target, null) { }
        assertNull(vm.state.value.currentActor)
        coVerify(exactly = 0) { promote.execute(any(), any(), any()) }
    }
    @Test fun promotionPassesReviewedDoctorTitleAndActorToUseCase() {
        coEvery { promote.execute(target, "عنوان", actor.id) } returns PromoteResult.Success(target.copy(rank = 2))
        val vm = vm(); val messages = mutableListOf<String>()
        vm.assignAdmin(target, "عنوان", messages::add)
        coVerify(exactly = 1) { promote.execute(target, "عنوان", actor.id) }
        assertEquals(1, messages.size)
        assertFalse(vm.state.value.saving)
        // Repository observation, rather than an optimistic local copy, remains authoritative.
        assertEquals(listOf(target), vm.state.value.nonAdmins)
    }
    @Test fun promotionFailureIsReturnedWithoutChangingRegistry() {
        coEvery { promote.execute(target, null, actor.id) } returns PromoteResult.Failure("synthetic stale doctor")
        val vm = vm(); val messages = mutableListOf<String>(); vm.assignAdmin(target, null, messages::add)
        assertEquals(listOf("synthetic stale doctor"), messages)
        assertEquals(listOf(target), vm.state.value.nonAdmins)
        assertFalse(vm.state.value.saving)
    }
    @Test fun demotionUsesExactExpectedSnapshotAndActor() {
        coEvery { demote.execute(actor, actor.id) } returns PromoteResult.Success(actor.copy(rank = 0))
        val vm = vm(); val messages = mutableListOf<String>(); vm.removeAdmin(actor, messages::add)
        coVerify(exactly = 1) { demote.execute(actor, actor.id) }
        assertEquals(1, messages.size)
        assertFalse(vm.state.value.saving)
    }
    @Test fun demotionFailureIsReturnedWithoutOptimisticRemoval() {
        coEvery { demote.execute(actor, actor.id) } returns PromoteResult.Failure("synthetic protected admin")
        val vm = vm(); val messages = mutableListOf<String>(); vm.removeAdmin(actor, messages::add)
        assertEquals(listOf("synthetic protected admin"), messages)
        assertEquals(listOf(actor), vm.state.value.admins)
        assertFalse(vm.state.value.saving)
    }
    @Test fun duplicatePromotionAndDemotionAreBlockedWhileSaving() {
        coEvery { promote.execute(target, null, actor.id) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.assignAdmin(target, null) { }; vm.assignAdmin(target, null) { }; vm.removeAdmin(actor) { }
        assertTrue(vm.state.value.saving)
        coVerify(exactly = 1) { promote.execute(any(), any(), any()) }
        coVerify(exactly = 0) { demote.execute(any(), any()) }
    }
    @Test fun duplicateDemotionAndPromotionAreBlockedWhileSaving() {
        coEvery { demote.execute(actor, actor.id) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.removeAdmin(actor) { }; vm.removeAdmin(actor) { }; vm.assignAdmin(target, null) { }
        assertTrue(vm.state.value.saving)
        coVerify(exactly = 1) { demote.execute(any(), any()) }
        coVerify(exactly = 0) { promote.execute(any(), any(), any()) }
    }
    @Test fun liveRegistryDemotionBlocksSubsequentAction() {
        val vm = vm(); registry.value = listOf(actor.copy(rank = 0), target)
        vm.removeAdmin(target) { }
        coVerify(exactly = 0) { demote.execute(any(), any()) }
    }
    @Test fun cancellationReleasesSavingWithoutSuccessCallback() {
        coEvery { promote.execute(target, null, actor.id) } coAnswers { awaitCancellation() }
        val vm = vm(); val messages = mutableListOf<String>(); vm.assignAdmin(target, null, messages::add)
        clearViewModels()
        assertFalse(vm.state.value.saving)
        assertTrue(messages.isEmpty())
    }
    @Test fun transferPreparationPublishesNothingAndStoresExactReview() {
        val review = review()
        coEvery { sync.preparePermanentAdminTransfer(target.id) } returns Result.success(review)
        val vm = vm(); val results = mutableListOf<String?>()
        vm.prepareTransfer(target.id, results::add)
        assertSame(review, vm.state.value.transferReview)
        assertEquals(listOf<String?>(null), results)
        assertFalse(vm.state.value.saving)
        coVerify(exactly = 0) { sync.publishPermanentAdminTransfer(any()) }
        vm.dismissTransferReview(); assertNull(vm.state.value.transferReview)
    }
    @Test fun confirmationConsumesReviewAndCannotBeRepeatedAfterUncertainFailure() {
        val review = review()
        coEvery { sync.preparePermanentAdminTransfer(target.id) } returns Result.success(review)
        coEvery { sync.publishPermanentAdminTransfer(review) } returns Result.failure(IllegalStateException("synthetic uncertainty"))
        val vm = vm(); val messages = mutableListOf<String>()
        vm.prepareTransfer(target.id) { }; vm.confirmTransfer(messages::add); vm.confirmTransfer(messages::add)
        assertEquals(1, messages.size)
        assertNull(vm.state.value.transferReview); assertFalse(vm.state.value.saving)
        coVerify(exactly = 1) { sync.publishPermanentAdminTransfer(review) }
        assertEquals(listOf(actor), vm.state.value.admins)
    }
    @Test fun inFlightTransferBlocksRepeatedConfirmationAndOtherRoleActions() {
        val review = review()
        coEvery { sync.preparePermanentAdminTransfer(target.id) } returns Result.success(review)
        coEvery { sync.publishPermanentAdminTransfer(review) } coAnswers { awaitCancellation() }
        val vm = vm(); val messages = mutableListOf<String>()
        vm.prepareTransfer(target.id) { }; vm.confirmTransfer(messages::add); vm.confirmTransfer(messages::add)
        vm.assignAdmin(target, null) { }; vm.removeAdmin(actor) { }
        assertTrue(vm.state.value.saving)
        coVerify(exactly = 1) { sync.publishPermanentAdminTransfer(review) }
        coVerify(exactly = 0) { promote.execute(any(), any(), any()) }
        coVerify(exactly = 0) { demote.execute(any(), any()) }
        clearViewModels(); assertFalse(vm.state.value.saving); assertTrue(messages.isEmpty())
    }
    @Test fun failedPreparationClearsOldReviewWithoutChangingRoles() {
        coEvery { sync.preparePermanentAdminTransfer(target.id) } returnsMany listOf(
            Result.success(review()), Result.failure(IllegalStateException("synthetic stale authority")))
        val vm = vm(); val results = mutableListOf<String?>()
        vm.prepareTransfer(target.id, results::add); vm.prepareTransfer(target.id, results::add)
        assertNull(vm.state.value.transferReview); assertFalse(vm.state.value.saving)
        assertNotNull(results.last()); assertEquals(listOf(actor), vm.state.value.admins)
    }
    @Test fun successfulTransferReliesOnRegistryObservationForRoleChanges() {
        val review = review()
        coEvery { sync.preparePermanentAdminTransfer(target.id) } returns Result.success(review)
        coEvery { sync.publishPermanentAdminTransfer(review) } returns Result.success(Unit)
        val vm = vm(); val messages = mutableListOf<String>()
        vm.prepareTransfer(target.id) { }; vm.confirmTransfer(messages::add)
        assertEquals(1, messages.size); assertNull(vm.state.value.transferReview)
        assertEquals(actor, vm.state.value.currentActor)
        registry.value = listOf(actor.copy(isPermanentAdmin = false), target.copy(rank = 2, isPermanentAdmin = true))
        assertEquals(2, vm.state.value.admins.size)
    }

}
