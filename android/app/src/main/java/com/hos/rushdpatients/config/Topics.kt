package com.hos.rushdpatients.config

import javax.inject.Inject
import javax.inject.Singleton

enum class Topic(val label: String) {
    GENERAL("General"),
    REPORTS("reports"),
    CSV("csv"),
    DOCTORS("doctors")
}

@Singleton
class Topics @Inject constructor(
    private val projectConfigStore: ProjectConfigStore
) {
    /** Ephemeral review binding; never serialize connection credentials into UI state. */
    internal fun connectionUnchangedCheck(): () -> Boolean {
        val expected = projectConfigStore.current()
        return { projectConfigStore.current() == expected }
    }

    val chatId: Long get() = projectConfigStore.current().chatId
    val reportsChatId: Long get() = projectConfigStore.current().let { it.residentsChatId.takeIf { id -> id != 0L } ?: it.chatId }
    val projectName: String get() = projectConfigStore.current().hospitalName

    /** null means post to General. */
    fun threadId(topic: Topic): Long? {
        val config = projectConfigStore.current()
        val value = when (topic) {
            Topic.GENERAL -> 0L
            Topic.REPORTS -> if (config.residentsChatId != 0L) config.residentsTopicId else config.reportsTopicId
            Topic.CSV -> config.csvTopicId
            Topic.DOCTORS -> config.doctorsTopicId
        }
        return value.takeIf { it != 0L }
    }
}
