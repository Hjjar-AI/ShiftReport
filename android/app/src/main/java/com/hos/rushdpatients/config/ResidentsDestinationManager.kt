package com.hos.rushdpatients.config

import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.sync.DoctorSyncCoordinator
import com.hos.rushdpatients.sync.RemoteSyncStateStore
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResidentsDestinationManager @Inject constructor(
    private val projects: ProjectConfigStore,
    private val telegram: TelegramClient,
    private val remote: RemoteSyncStateStore,
    private val doctors: DoctorSyncCoordinator,
    private val dispatchers: DispatcherProvider
) {
    suspend fun save(expected: ProjectConfig, group: String, topic: String) = withContext(dispatchers.io) {
        require(expected.initialized && !expected.demoMode) { "افتح مشروعاً فعلياً أولاً" }
        val (chatId, topicId) = ResidentsDestination.parse(group, topic)
        val actor = doctors.requirePublishedAdmin(requireNotNull(remote.readState()))
        ResidentsDestination.validate(telegram, chatId, topicId)
        require(doctors.requirePublishedAdmin(requireNotNull(remote.readState())).id == actor.id) { "تغير المستخدم؛ أعد المراجعة" }
        projects.replaceResidentsDestination(expected, chatId, topicId)
    }
}
