package com.hos.rushdpatients.data.model

/** Typed access to the legacy options set. Unknown entries survive every local edit. */
class DoctorOptions private constructor(private val stored: Set<String>) {
    val pinHash: String? get() = stored.firstOrNull { it.startsWith(PIN) }?.removePrefix(PIN)
    val syncAliases: Set<String> get() = stored.filter { it.startsWith(ALIAS) }.mapTo(linkedSetOf()) { it.removePrefix(ALIAS) }
    val teamSupervisorIds: List<String> get() = stored.filter { it.startsWith(TEAM) }.map { it.removePrefix(TEAM) }
    val teamSupervisorId: String? get() = teamSupervisorIds.firstOrNull()
    val portable: Set<String> get() = stored.filterNot { it.startsWith(PIN) || it.startsWith(ALIAS) }.toSet()
    fun localIdentity(includePin: Boolean): Set<String> = stored.filter {
        it.startsWith(ALIAS) || (includePin && it.startsWith(PIN))
    }.toSet()
    fun withPinHash(hash: String?): Set<String> = stored.filterNot { it.startsWith(PIN) }.toSet() +
        hash?.let { setOf(PIN + it) }.orEmpty()
    fun withoutInheritedPin(previous: DoctorOptions): Set<String> = stored.filterNot {
        it.startsWith(PIN) && it in previous.stored
    }.toSet()
    fun withTeamSupervisor(id: String?): Set<String> = stored.filterNot { it.startsWith(TEAM) }.toSet() +
        id?.let { setOf(TEAM + it) }.orEmpty()
    fun withSharedTeam(incoming: DoctorOptions): Set<String> = stored.filterNot { it.startsWith(TEAM) }.toSet() +
        incoming.stored.filter { it.startsWith(TEAM) }
    fun withAlias(id: String): Set<String> = stored + (ALIAS + id)

    companion object {
        private const val PIN = "pin:"
        private const val ALIAS = "syncAlias:"
        private const val TEAM = "teamSupervisor:"
        fun from(stored: Set<String>): DoctorOptions = DoctorOptions(stored)
    }
}
