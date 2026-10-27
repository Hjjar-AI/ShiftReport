package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class PermanentAdminTransferTest {
    private val owner = Fixtures.doctor(id = "owner", rank = 1, telegramId = 111, isPermanentAdmin = true)
        .copy(extraOptions = setOf("pin:synthetic-local-hash"))
    private val recipient = Fixtures.doctor(id = "recipient", fullName = "د. بلوط", rank = 2, telegramId = 222)
    private val registry = listOf(owner, recipient)
    private fun reject(block: () -> Unit) {
        try { block(); fail("Invalid handover accepted") } catch (e: RuntimeException) {
            assertTrue(e is IllegalArgumentException || e is NoSuchElementException)
        }
    }
    @Test fun handoverChangesOnlyTwoFlagsAndKeepsRanksAndPrivateFields() {
        val next = PermanentAdminTransfer.propose(registry, owner.id, recipient.id)
        assertEquals(listOf(owner.copy(isPermanentAdmin = false), recipient.copy(isPermanentAdmin = true)), next)
        assertTrue(next.first().isAdmin)
        assertEquals(setOf("owner"), PermanentAdminTransfer.transferredOwnerIds(registry, next))
        assertEquals(registry, listOf(owner, recipient))
    }
    @Test fun ordinaryOrUnlinkedOrDeletedOwnerCannotTransfer() {
        listOf(owner.copy(isPermanentAdmin = false), owner.copy(telegramId = null),
            owner.copy(deletedAt = Fixtures.timestamp)).forEach { invalid ->
            reject { PermanentAdminTransfer.propose(listOf(invalid, recipient), owner.id, recipient.id) }
        }
    }
    @Test fun invalidRecipientsCannotReceivePermanentAuthority() {
        listOf(recipient.copy(rank = 0), recipient.copy(isPermanentAdmin = true),
            recipient.copy(telegramId = null), recipient.copy(telegramId = owner.telegramId),
            recipient.copy(deletedAt = Fixtures.timestamp)).forEach { invalid ->
            reject { PermanentAdminTransfer.propose(listOf(owner, invalid), owner.id, recipient.id) }
        }
        reject { PermanentAdminTransfer.propose(registry, owner.id, owner.id) }
        reject { PermanentAdminTransfer.propose(registry, owner.id, "missing") }
    }
    @Test fun unrelatedPermanentOwnersRemainUntouched() {
        val other = recipient.copy(id = "other", rank = 3, telegramId = 333, isPermanentAdmin = true)
        val next = PermanentAdminTransfer.propose(registry + other, owner.id, recipient.id)
        assertEquals(other, next.last())
        assertEquals(setOf(owner.id), PermanentAdminTransfer.transferredOwnerIds(registry + other, next))
    }
    @Test fun ownerRemovalWithoutLinkedSuccessorIsRejected() {
        listOf(listOf(recipient), listOf(recipient.copy(isPermanentAdmin = true, telegramId = null)),
            listOf(owner.copy(isPermanentAdmin = false), recipient)).forEach { incoming ->
            assertTrue(PermanentAdminTransfer.transferredOwnerIds(registry, incoming).isEmpty())
        }
    }
    @Test fun fetchAcceptsSuccessorAfterMissedFormerOwnerDemotionOrRemoval() {
        val successor = recipient.copy(isPermanentAdmin = true)
        assertEquals(setOf(owner.id), PermanentAdminTransfer.transferredOwnerIds(registry, listOf(successor)))
        assertEquals(setOf(owner.id), PermanentAdminTransfer.transferredOwnerIds(registry,
            listOf(owner.copy(rank = 0, isPermanentAdmin = false), successor)))
    }
    @Test fun unbalancedReplacementDoesNotBypassOwnerProtection() {
        val other = recipient.copy(id = "other", rank = 3, telegramId = 333, isPermanentAdmin = true)
        assertTrue(PermanentAdminTransfer.transferredOwnerIds(registry,
            listOf(owner.copy(isPermanentAdmin = false), recipient.copy(isPermanentAdmin = true), other)).isEmpty())
    }
}
