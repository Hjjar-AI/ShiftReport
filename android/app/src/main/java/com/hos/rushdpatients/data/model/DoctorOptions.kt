package com.hos.rushdpatients.data.model

/** Typed access to the local PIN and portable team/supervisor destination options. */
class DoctorOptions private constructor(private val stored: Set<String>) {
    val pinHash: String? get() = stored.firstOrNull { it.startsWith(PIN) }?.removePrefix(PIN)
    val teamSupervisorIds: List<String> get() = stored.filter { it.startsWith(TEAM) }.map { it.removePrefix(TEAM) }.sorted()
    val teamSupervisorId: String? get() = teamSupervisorIds.firstOrNull()
    val supervisorTopics: List<String> get() = stored.filter { it.startsWith(SUPERVISOR_TOPIC) }.map { it.removePrefix(SUPERVISOR_TOPIC) }
    val supervisorTopicId: Long? get() = supervisorTopics.singleOrNull()?.toLongOrNull()
    val portable: Set<String> get() = stored.filterNot { it.startsWith(PIN) }.toSet()
    fun localIdentity(includePin: Boolean): Set<String> = stored.filter {
        includePin && it.startsWith(PIN)
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
    fun withSupervisorTopic(id: Long?): Set<String> = stored.filterNot { it.startsWith(SUPERVISOR_TOPIC) }.toSet() +
        id?.let { setOf(SUPERVISOR_TOPIC + it) }.orEmpty()

    companion object {
        private const val PIN = "pin:"
        private const val TEAM = "teamSupervisor:"
        private const val SUPERVISOR_TOPIC = "supervisorTopicId:"
        fun from(stored: Set<String>): DoctorOptions = DoctorOptions(stored)
    }
}
