package com.hos.rushdpatients.domain.announcement

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.config.ProjectDataCipher
import com.hos.rushdpatients.config.Topic
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.domain.report.DeliveryResolution
import com.hos.rushdpatients.domain.report.ReportDeliveryLedger
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.TelegramException
import com.hos.rushdpatients.sync.DoctorSyncCoordinator
import com.hos.rushdpatients.sync.RemoteSyncStateStore
import com.hos.rushdpatients.sync.SyncState
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class AnnouncementReview internal constructor(
    val text: String, val existingMessageId: Long?,
    internal val state: SyncState, internal val actorId: String, internal val connection: String
)

/** Uses the existing delivery ledger; UNKNOWN includes both message and index uncertainty. */
@Singleton
class AnnouncementPublisher @Inject constructor(
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val projects: ProjectConfigStore,
    private val cipher: ProjectDataCipher,
    private val remote: RemoteSyncStateStore,
    private val doctors: DoctorSyncCoordinator,
    private val ledger: ReportDeliveryLedger,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider
) {
    companion object { const val SCOPE = "announcement" }
    private val mutex = Mutex()
    private var prepared: AnnouncementReview? = null
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun connection(): String = projects.current().let {
        digest("${it.chatId}|${it.announcementsTopicId}|${it.botToken}|${it.telegramDataKey}")
    }
    private fun contentKey(review: AnnouncementReview) = digest("${review.text}|${review.state.announcementMessageId}|${review.state.announcementUpdatedAt}|${review.connection}")
    private suspend fun state() = requireNotNull(remote.readState()) { "فهرس المشروع غير متاح؛ أعد الجلب" }
    suspend fun currentMessageId(): Long? = withContext(dispatchers.io) { state().announcementMessageId }

    suspend fun prepare(text: String): AnnouncementReview = withContext(dispatchers.io) {
        mutex.withLock {
            prepared = null
            require(text.isNotBlank() && text.length <= 4096) { "أدخل إعلاناً غير فارغ ضمن حد الرسالة" }
            cipher.encodeText(text) // Validate encrypted wire length before registering a request.
            val projectStamp = connection()
            val state = state()
            val actor = doctors.requirePublishedAdmin(state)
            check(projectStamp == connection()) { "تغير اتصال المشروع؛ أعد المراجعة" }
            val review = AnnouncementReview(text, state.announcementMessageId, state, actor.id, projectStamp)
            ledger.check(SCOPE, topics.chatId, contentKey(review), SCOPE)
            prepared = review
            review
        }
    }

    suspend fun publish(review: AnnouncementReview): Long = withContext(dispatchers.io) {
        mutex.withLock {
            check(review == prepared) { "راجع نص الإعلان نفسه قبل النشر" }
            prepared = null
            check(connection() == review.connection) { "تغير اتصال المشروع؛ أعد المراجعة" }
            check(state() == review.state) { "تغير فهرس المشروع؛ أعد مراجعة الإعلان" }
            val actor = doctors.requirePublishedAdmin(review.state)
            check(actor.id == review.actorId) { "تغير المدير؛ أعد المراجعة" }
            cipher.encodeText(review.text)
            check(connection() == review.connection) { "تغير اتصال المشروع؛ أعد المراجعة" }
            ledger.deliver(SCOPE, topics.chatId, contentKey(review), SCOPE, "إعلان المجموعة", actor.id,
                metadata = mapOf("previousMessage" to (review.state.announcementMessageId ?: 0L),
                    "previousUpdate" to review.state.announcementUpdatedAt)) { record ->
                check(connection() == review.connection) { "تغير اتصال المشروع؛ أعد المراجعة" }
                val existingId = review.existingMessageId
                val messageId = try {
                    if (existingId != null) telegram.editMessageText(
                        topics.chatId, existingId, review.text, parseMode = null).messageId
                    else telegram.sendMessage(topics.chatId, review.text, parseMode = null,
                        messageThreadId = topics.threadId(Topic.ANNOUNCEMENTS)).messageId
                } catch (e: TelegramException) {
                    if (existingId != null && e.message.contains("not modified", ignoreCase = true)) existingId
                    else throw e
                }
                require(messageId > 0) { "لم يتأكد معرّف رسالة الإعلان؛ افحص تليجرام" }
                check(connection() == review.connection) { "تغير الاتصال بعد الإرسال؛ راجع سجل التسليم" }
                val latest = state()
                check(doctors.requirePublishedAdmin(latest).id == actor.id) { "تغيرت صلاحية المدير؛ راجع التسليم" }
                val at = System.currentTimeMillis()
                val committed = remote.updateExistingState { current ->
                    check(current.announcementMessageId == review.state.announcementMessageId &&
                        current.announcementUpdatedAt == review.state.announcementUpdatedAt) { "نُشر إعلان آخر؛ راجع تليجرام قبل الإعادة" }
                    current.copy(announcementMessageId = messageId, announcementUpdatedAt = at)
                }
                check(committed.announcementMessageId == messageId && committed.announcementUpdatedAt == at) { "لم يتأكد فهرس الإعلان؛ راجع التسليم" }
                audit.record(actor.id, actor.fullName, AppConstants.AUDIT_ANNOUNCEMENT_UPDATED, "messageId=$messageId")
                ledger.confirm(record.id, listOf(messageId))
                messageId
            }
        }
    }

    suspend fun resolve(record: ReportDeliveryRecord, receivedMessageId: Long?): Unit = withContext(dispatchers.io) {
        mutex.withLock {
            val row = ledger.requireResolvable(record.id, "UNKNOWN")
            require(row.shiftId == SCOPE && row.format == SCOPE) { "هذه العملية ليست إعلاناً" }
            val projectStamp = connection()
            val current = state()
            val actor = doctors.requirePublishedAdmin(current)
            check(projectStamp == connection()) { "تغير الاتصال؛ أعد حسم العملية" }
            if (receivedMessageId != null) {
                require(receivedMessageId > 0) { "أدخل معرّف رسالة الإعلان الذي تحققت منه في تليجرام" }
                // Repair a missing/older reference; never replace a newer announcement.
                var repaired = false
                remote.updateExistingState { latest ->
                    check(projectStamp == connection()) { "تغير الاتصال؛ أعد حسم العملية" }
                    val sameReference = row.metadata["previousMessage"] == (latest.announcementMessageId ?: 0L) &&
                        row.metadata["previousUpdate"] == latest.announcementUpdatedAt
                    if (latest.announcementMessageId != receivedMessageId && sameReference) {
                        repaired = true
                        latest.copy(announcementMessageId = receivedMessageId, announcementUpdatedAt = System.currentTimeMillis())
                    }
                    else latest
                }.also { committed ->
                    check(!repaired || committed.announcementMessageId == receivedMessageId) { "تعذر تأكيد إحالة الإعلان" }
                }
            }
            check(doctors.requirePublishedAdmin(state()).id == actor.id) { "تغيرت صلاحية المدير؛ أعد المراجعة" }
            check(projectStamp == connection()) { "تغير الاتصال؛ أعد حسم العملية" }
            ledger.resolve(row.id, "UNKNOWN", if (receivedMessageId == null) DeliveryResolution.NOT_RECEIVED else DeliveryResolution.RECEIVED)
        }
    }
}
