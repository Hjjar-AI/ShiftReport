package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.data.model.Doctor

/** Role-only handover: both accounts retain their ordinary-admin ranks and clinical data. */
object PermanentAdminTransfer {
    fun propose(doctors: List<Doctor>, actorId: String, recipientId: String): List<Doctor> {
        val actor = doctors.single { it.id == actorId && !it.isDeleted }
        val recipient = doctors.single { it.id == recipientId && !it.isDeleted }
        require(actor.isPermanentAdmin && actor.isAdmin && actor.telegramId != null &&
            recipient.id != actor.id && recipient.isAdmin && !recipient.isPermanentAdmin &&
            recipient.telegramId != null && recipient.telegramId != actor.telegramId) {
            "اختر مديراً عادياً مرتبطاً بحساب تليجرام مختلف لنقل الإدارة الدائمة"
        }
        return doctors.map {
            when (it.id) {
                actor.id -> it.copy(isPermanentAdmin = false)
                recipient.id -> it.copy(isPermanentAdmin = true)
                else -> it
            }
        }
    }

    /** Only used after reading and confirming the live published registry, never for imports. */
    fun transferredOwnerIds(local: List<Doctor>, published: List<Doctor>): Set<String> {
        val owners = local.filter { !it.isDeleted && it.isPermanentAdmin }
        val nextOwners = published.filter { !it.isDeleted && it.isPermanentAdmin && it.isAdmin && it.telegramId != null }
        val removed = owners.filter { old -> nextOwners.none { it.id == old.id } }
        val added = nextOwners.filter { next -> owners.none { it.id == next.id } }
        if (removed.isEmpty() || added.size != removed.size || nextOwners.size != owners.size) return emptySet()
        // A client may have missed several publications after the handover. The former
        // owner may since have changed rank or left. The live publication is authoritative;
        // require balanced, linked successors rather than replaying an immediate transition.
        return removed.mapTo(mutableSetOf()) { it.id }
    }
}
