package com.hos.rushdpatients.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.hos.rushdpatients.config.ProjectDataCipher
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.Topic
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.network.telegram.ParseMode
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.util.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SendResult(
    val messageIds: List<Long>,
    val firstMessageId: Long,
    val sentAt: Instant
)

@Singleton
class ReportSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataCipher: ProjectDataCipher,
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val shiftRepository: ShiftRepository,
    private val auditRepository: AuditRepository
) {

    suspend fun sendTextReport(
        shift: Shift,
        chunks: List<String>,
        actor: Doctor?
    ): SendResult = withContext(Dispatchers.IO) {
        require(chunks.isNotEmpty()) { "No chunks to send" }

        val messageIds = mutableListOf<Long>()

        try {
            if (dataCipher.enabled) {
                val file = File.createTempFile("text_report_", ".txt", context.cacheDir)
                try {
                    file.writeText(chunks.joinToString("\n\n"), Charsets.UTF_8)
                    val msg = telegram.sendDocument(
                        chatId = topics.chatId, file = file,
                        messageThreadId = topics.threadId(Topic.REPORTS)
                    )
                    messageIds += msg.messageId
                } finally {
                    file.delete()
                }
            } else {
                for (chunk in chunks) {
                    val msg = telegram.sendMessage(
                        chatId = topics.chatId,
                        text = chunk,
                        parseMode = ParseMode.MARKDOWN_V2,
                        replyToMessageId = null,
                        disableNotification = true,
                        messageThreadId = topics.threadId(Topic.REPORTS)
                    )
                    messageIds += msg.messageId
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messageIds.asReversed().forEach { messageId ->
                try {
                    telegram.deleteMessage(topics.chatId, messageId)
                } catch (_: Exception) {
                    // Best-effort rollback of a partially delivered multi-part report.
                }
            }
            throw e
        }

        val sentAt = Instant.now()
        recordDeliveryBestEffort(
            shift = shift,
            sentAt = sentAt,
            reportMessageId = messageIds.first(),
            pdfMessageId = null,
            actor = actor,
            detail = if (dataCipher.enabled) "encrypted-text; 1 document" else "text; ${chunks.size} message(s)"
        )

        SendResult(messageIds, messageIds.first(), sentAt)
    }

    suspend fun sendPdfReport(
        shift: Shift,
        pdfFile: File,
        caption: String?,
        actor: Doctor?
    ): SendResult = withContext(Dispatchers.IO) {
        val msg = telegram.sendDocument(
            chatId = topics.chatId,
            file = pdfFile,
            caption = caption,
            parseMode = if (caption.isNullOrBlank()) null else ParseMode.MARKDOWN_V2,
            replyToMessageId = null,
            disableNotification = true,
            messageThreadId = topics.threadId(Topic.REPORTS)
        )

        val sentAt = Instant.now()
        recordDeliveryBestEffort(
            shift = shift,
            sentAt = sentAt,
            reportMessageId = null,
            pdfMessageId = msg.messageId,
            actor = actor,
            detail = "pdf; size=${pdfFile.length()} bytes"
        )

        SendResult(listOf(msg.messageId), msg.messageId, sentAt)
    }

    suspend fun sendSupervisorPdfReport(
        shift: Shift,
        pdfFile: File,
        caption: String?,
        actor: Doctor?,
        supervisor: Doctor,
        chatId: Long
    ): SendResult = withContext(Dispatchers.IO) {
        require(chatId < 0L) { "معرف مجموعة المشرف غير صالح" }
        val msg = telegram.sendDocument(
            chatId = chatId,
            file = pdfFile,
            caption = caption,
            parseMode = if (caption.isNullOrBlank()) null else ParseMode.MARKDOWN_V2,
            replyToMessageId = null,
            disableNotification = true,
            messageThreadId = null
        )
        val sentAt = Instant.now()
        try {
            auditRepository.record(
                actorDoctorId = actor?.id,
                actorName = actor?.fullName,
                action = AppConstants.AUDIT_REPORT_SENT,
                detail = "supervisor-pdf; supervisor=${supervisor.id}; " +
                    "shift=${shift.id}; size=${pdfFile.length()} bytes"
            )
        } catch (e: Exception) {
            Logging.e("Supervisor report delivered, but audit recording failed", e)
        }
        SendResult(listOf(msg.messageId), msg.messageId, sentAt)
    }

    private suspend fun recordDeliveryBestEffort(
        shift: Shift,
        sentAt: Instant,
        reportMessageId: Long?,
        pdfMessageId: Long?,
        actor: Doctor?,
        detail: String
    ) {
        try {
            shiftRepository.markSent(
                shiftId = shift.id,
                sentAt = sentAt,
                reportMessageId = reportMessageId,
                pdfMessageId = pdfMessageId
            )
        } catch (e: Exception) {
            // The Telegram delivery is authoritative; do not invite a duplicate retry.
            Logging.e("Report delivered, but local sent state could not be recorded", e)
        }
        try {
            auditRepository.record(
                actorDoctorId = actor?.id,
                actorName = actor?.fullName,
                action = AppConstants.AUDIT_REPORT_SENT,
                detail = detail
            )
        } catch (e: Exception) {
            // Auditing must not change a successful delivery into a UI failure.
            Logging.e("Report delivered, but the audit entry could not be recorded", e)
        }
    }
}
