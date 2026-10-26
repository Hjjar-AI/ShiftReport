package com.hos.rushdpatients.network

import com.hos.rushdpatients.R
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
import com.hos.rushdpatients.domain.report.ReportDeliveryLedger
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The request started; an exception cannot establish that Telegram received nothing. */
class ReportDeliveryUncertainException(cause: Throwable) : Exception(
    "نتيجة تسليم التقرير غير مؤكدة؛ افحص تليجرام قبل إعادة الإرسال", cause)

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
    private val auditRepository: AuditRepository,
    private val deliveryLedger: ReportDeliveryLedger
) {

    suspend fun checkGeneralDelivery(shift: Shift, contentKey: String, pdf: Boolean, legacyContentKey: String? = null) =
        deliveryLedger.check(shift.id, topics.chatId, contentKey, if (pdf) "PDF" else "TEXT", legacyContentKey)

    suspend fun checkSupervisorDelivery(shift: Shift, contentKey: String, chatId: Long, legacyContentKey: String? = null) =
        deliveryLedger.check(shift.id, chatId, contentKey, "PDF", legacyContentKey)

    suspend fun sendTextReport(
        shift: Shift, chunks: List<String>, actor: Doctor?, contentKey: String, legacyContentKey: String? = null
    ): SendResult = withContext(Dispatchers.IO) {
        require(chunks.isNotEmpty()) { "No chunks to send" }
        val file = if (dataCipher.enabled) File.createTempFile("text_report_", ".txt", context.cacheDir) else null
        val messageIds = mutableListOf<Long>()
        try {
            file?.writeText(chunks.joinToString("\n\n"), Charsets.UTF_8)
            deliveryLedger.deliver(shift.id, topics.chatId, contentKey, "TEXT", context.getString(R.string.report_general_route_label), actor?.id, legacyContentKey = legacyContentKey) { receipt ->
                try {
                    if (file != null) {
                        val msg = uncertainDelivery { telegram.sendDocument(chatId = topics.chatId, file = file,
                            messageThreadId = topics.threadId(Topic.REPORTS)) }
                        messageIds += msg.messageId
                    } else {
                        for (chunk in chunks) {
                            val msg = uncertainDelivery { telegram.sendMessage(chatId = topics.chatId, text = chunk,
                                parseMode = ParseMode.MARKDOWN_V2, replyToMessageId = null, disableNotification = true,
                                messageThreadId = topics.threadId(Topic.REPORTS)) }
                            messageIds += msg.messageId
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    messageIds.asReversed().forEach { id ->
                        try { telegram.deleteMessage(topics.chatId, id) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { /* Partial cleanup is not proof of non-delivery. */ }
                    }
                    if (messageIds.isNotEmpty() && e is com.hos.rushdpatients.network.telegram.TelegramException && e.isDefiniteRejection)
                        throw ReportDeliveryUncertainException(e)
                    throw e
                }
                confirmBestEffort(receipt, messageIds)
                val sentAt = Instant.now()
                recordDeliveryBestEffort(shift, sentAt, messageIds.first(), null, actor,
                    if (file != null) "encrypted-text; 1 document" else "text; ${chunks.size} message(s)")
                SendResult(messageIds, messageIds.first(), sentAt)
            }
        } finally { file?.delete() }
    }

    suspend fun sendPdfReport(
        shift: Shift,
        pdfFile: File,
        caption: String?,
        actor: Doctor?,
        contentKey: String,
        legacyContentKey: String? = null,
        fileName: String? = null
    ): SendResult = withContext(Dispatchers.IO) {
        require(pdfFile.isFile && pdfFile.length() > 0) { context.getString(R.string.report_preview_file_missing) }
        deliveryLedger.deliver(shift.id, topics.chatId, contentKey, "PDF", context.getString(R.string.report_general_route_label), actor?.id, legacyContentKey = legacyContentKey) { receipt ->
            val titleParts = com.hos.rushdpatients.domain.report.ReportChunking.captionParts(caption.orEmpty())
            val msg = uncertainDelivery { telegram.sendDocument(
                chatId = topics.chatId,
                file = pdfFile,
                fileName = fileName,
                caption = titleParts.firstOrNull(),
                parseMode = if (caption.isNullOrBlank()) null else ParseMode.MARKDOWN_V2,
                replyToMessageId = null,
                disableNotification = true,
                messageThreadId = topics.threadId(Topic.REPORTS)
            ) }

            val ids = completePdfTitle(topics.chatId, msg.messageId, titleParts.drop(1), topics.threadId(Topic.REPORTS))
            confirmBestEffort(receipt, ids)
            val sentAt = Instant.now()
            recordDeliveryBestEffort(
                shift = shift,
                sentAt = sentAt,
                reportMessageId = null,
                pdfMessageId = msg.messageId,
                actor = actor,
                detail = "pdf; size=${pdfFile.length()} bytes"
            )

            SendResult(ids, msg.messageId, sentAt)
        }
    }

    suspend fun sendSupervisorPdfReport(
        shift: Shift,
        pdfFile: File,
        caption: String?,
        actor: Doctor?,
        supervisor: Doctor,
        chatId: Long,
        contentKey: String,
        legacyContentKey: String? = null,
        fileName: String? = null
    ): SendResult = withContext(Dispatchers.IO) {
        require(chatId < 0L) { context.getString(R.string.report_supervisor_chat_invalid) }
        require(pdfFile.isFile && pdfFile.length() > 0) { context.getString(R.string.report_preview_file_missing) }
        deliveryLedger.deliver(shift.id, chatId, contentKey, "PDF", supervisor.fullName, actor?.id, legacyContentKey = legacyContentKey) { receipt ->
            val titleParts = com.hos.rushdpatients.domain.report.ReportChunking.captionParts(caption.orEmpty())
            val msg = uncertainDelivery { telegram.sendDocument(
                chatId = chatId,
                file = pdfFile,
                fileName = fileName,
                caption = titleParts.firstOrNull(),
                parseMode = if (caption.isNullOrBlank()) null else ParseMode.MARKDOWN_V2,
                replyToMessageId = null,
                disableNotification = true,
                messageThreadId = null
            ) }
            val ids = completePdfTitle(chatId, msg.messageId, titleParts.drop(1), null)
            confirmBestEffort(receipt, ids)
            val sentAt = Instant.now()
            recordAuditBestEffort(actor, "supervisor-pdf; supervisor=${supervisor.id}; shift=${shift.id}; size=${pdfFile.length()} bytes")
            SendResult(ids, msg.messageId, sentAt)
        }
    }

    private suspend fun completePdfTitle(chatId: Long, pdfId: Long, remaining: List<String>,
        topicId: Long?): List<Long> {
        val ids = mutableListOf(pdfId)
        try {
            remaining.forEach { title ->
                val message = telegram.sendPublicReportTitle(chatId, title, pdfId, topicId)
                ids += message.messageId
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // The PDF already reached Telegram; a title failure must never permit automatic resending.
            throw ReportDeliveryUncertainException(e)
        }
        return ids
    }

    private suspend fun confirmBestEffort(receipt: ReportDeliveryRecord, ids: List<Long>) {
        withContext(NonCancellable + Dispatchers.IO) {
            try { deliveryLedger.confirm(receipt.id, ids) }
            catch (e: Exception) {
                // Telegram acknowledged delivery; the persisted UNKNOWN entry still blocks retries.
                Logging.e("Delivery confirmed; local receipt remains unresolved", e)
            }
        }
    }

    private suspend fun <T> uncertainDelivery(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e is com.hos.rushdpatients.network.telegram.TelegramException && e.isDefiniteRejection) throw e
        throw ReportDeliveryUncertainException(e)
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
        recordAuditBestEffort(actor, detail)
    }

    private suspend fun recordAuditBestEffort(actor: Doctor?, detail: String) {
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
