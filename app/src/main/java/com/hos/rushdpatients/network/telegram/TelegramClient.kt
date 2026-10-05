package com.hos.rushdpatients.network.telegram

import com.hos.rushdpatients.config.BotTokenProvider
import com.hos.rushdpatients.network.telegram.dto.TgChat
import com.hos.rushdpatients.network.telegram.dto.TgChatMember
import com.hos.rushdpatients.network.telegram.dto.TgEnvelope
import com.hos.rushdpatients.network.telegram.dto.TgFile
import com.hos.rushdpatients.network.telegram.dto.TgMessage
import com.hos.rushdpatients.network.telegram.dto.TgUpdate
import com.hos.rushdpatients.network.telegram.dto.TgUser
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramClient @Inject constructor(
    private val tokenProvider: BotTokenProvider,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
    private val rateLimiter: TelegramRateLimiter,
    baseOkHttp: OkHttpClient
) {

    private val http: OkHttpClient = baseOkHttp.newBuilder()
        .connectTimeout(TelegramConfig.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TelegramConfig.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TelegramConfig.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val token: String get() = tokenProvider.botToken

    suspend fun getMe(): TgUser = callAndUnwrap(
        method = "getMe",
        chatId = 0L,
        body = FormBody.Builder().build()
    )

    // ---------------- Messages ----------------

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        parseMode: ParseMode? = ParseMode.MARKDOWN_V2,
        replyToMessageId: Long? = null,
        disableNotification: Boolean = true,
        disableWebPagePreview: Boolean = false,
        messageThreadId: Long? = null
    ): TgMessage {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("text", text)
            .apply {
                parseMode?.let { add("parse_mode", it.wire) }
                replyToMessageId?.let {
                    add("reply_to_message_id", it.toString())
                    add("allow_sending_without_reply", "true")
                }
                if (disableNotification) add("disable_notification", "true")
                if (disableWebPagePreview) add("disable_web_page_preview", "true")
                messageThreadId?.let { add("message_thread_id", it.toString()) }
            }
            .build()
        return callAndUnwrap("sendMessage", chatId, body)
    }

    suspend fun editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
        parseMode: ParseMode? = ParseMode.MARKDOWN_V2
    ): TgMessage {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("message_id", messageId.toString())
            .add("text", text)
            .apply { parseMode?.let { add("parse_mode", it.wire) } }
            .build()
        return callAndUnwrap("editMessageText", chatId, body)
    }

    suspend fun editMessageCaption(
        chatId: Long,
        messageId: Long,
        caption: String,
        parseMode: ParseMode? = ParseMode.MARKDOWN_V2
    ): TgMessage {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("message_id", messageId.toString())
            .add("caption", caption)
            .apply { parseMode?.let { add("parse_mode", it.wire) } }
            .build()
        return callAndUnwrap("editMessageCaption", chatId, body)
    }

    suspend fun deleteMessage(chatId: Long, messageId: Long): Boolean {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("message_id", messageId.toString())
            .build()
        val env = callAndUnwrapEnvelope<Boolean>("deleteMessage", chatId, body)
        return env.result == true
    }

    suspend fun forwardMessage(
        toChatId: Long,
        fromChatId: Long,
        messageId: Long,
        disableNotification: Boolean = true
    ): TgMessage {
        val body = FormBody.Builder()
            .add("chat_id", toChatId.toString())
            .add("from_chat_id", fromChatId.toString())
            .add("message_id", messageId.toString())
            .apply { if (disableNotification) add("disable_notification", "true") }
            .build()
        return callAndUnwrap("forwardMessage", toChatId, body)
    }

    // ---------------- Pinning ----------------

    suspend fun pinMessage(
        chatId: Long,
        messageId: Long,
        disableNotification: Boolean = true
    ): Boolean {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("message_id", messageId.toString())
            .apply { if (disableNotification) add("disable_notification", "true") }
            .build()
        val env = callAndUnwrapEnvelope<Boolean>("pinChatMessage", chatId, body)
        return env.result == true
    }

    suspend fun unpinMessage(chatId: Long, messageId: Long): Boolean {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("message_id", messageId.toString())
            .build()
        val env = callAndUnwrapEnvelope<Boolean>("unpinChatMessage", chatId, body)
        return env.result == true
    }

    suspend fun unpinAllMessages(chatId: Long): Boolean {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .build()
        val env = callAndUnwrapEnvelope<Boolean>("unpinAllChatMessages", chatId, body)
        return env.result == true
    }

    // ---------------- Documents ----------------

    suspend fun sendDocument(
        chatId: Long,
        file: File,
        caption: String? = null,
        parseMode: ParseMode? = null,
        replyToMessageId: Long? = null,
        disableNotification: Boolean = true,
        messageThreadId: Long? = null
    ): TgMessage {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart(
                "document",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
        caption?.let { builder.addFormDataPart("caption", it) }
        parseMode?.let { builder.addFormDataPart("parse_mode", it.wire) }
        replyToMessageId?.let {
            builder.addFormDataPart("reply_to_message_id", it.toString())
            builder.addFormDataPart("allow_sending_without_reply", "true")
        }
        if (disableNotification) builder.addFormDataPart("disable_notification", "true")
        messageThreadId?.let { builder.addFormDataPart("message_thread_id", it.toString()) }
        return callAndUnwrap("sendDocument", chatId, builder.build(), needsUpload = true)
    }

    suspend fun getFile(fileId: String): TgFile {
        val body = FormBody.Builder()
            .add("file_id", fileId)
            .build()
        return callAndUnwrap("getFile", 0L, body)
    }

    suspend fun downloadFile(filePath: String, destination: File): File {
        val url = "${TelegramConfig.FILE_BASE_URL}/bot$token/$filePath"
        return withContext(dispatchers.io) {
            val request = Request.Builder().url(url).get().build()
            val client = http.newBuilder()
                .readTimeout(TelegramConfig.DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw TelegramException(
                            code = resp.code,
                            message = "Download failed: HTTP ${resp.code}"
                        )
                    }
                    val body = resp.body
                        ?: throw TelegramException(code = -1, message = "Empty download body")
                    destination.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            } catch (e: IOException) {
                throw TelegramException(
                    code = -1,
                    message = "Download network error: ${e.message}",
                    cause = e
                )
            }
            destination
        }
    }

    // ---------------- Chats & admins ----------------

    suspend fun getChat(chatId: Long): TgChat {
        val body = FormBody.Builder().add("chat_id", chatId.toString()).build()
        return callAndUnwrap("getChat", chatId, body)
    }

    suspend fun getChatAdministrators(chatId: Long): List<TgChatMember> {
        val body = FormBody.Builder().add("chat_id", chatId.toString()).build()
        return callAndUnwrap("getChatAdministrators", chatId, body)
    }

    suspend fun promoteChatMember(
        chatId: Long,
        userId: Long,
        rights: AdminRights
    ): Boolean {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("user_id", userId.toString())
            .apply {
                when (rights) {
                    AdminRights.DEMOTE -> {
                        add("can_manage_chat", "false")
                        add("can_delete_messages", "false")
                        add("can_manage_video_chats", "false")
                        add("can_restrict_members", "false")
                        add("can_promote_members", "false")
                        add("can_change_info", "false")
                        add("can_invite_users", "false")
                        add("can_pin_messages", "false")
                    }
                    AdminRights.PROMOTE -> {
                        add("can_manage_chat", "false")
                        add("can_delete_messages", "false")
                        add("can_manage_video_chats", "true")
                        add("can_restrict_members", "false")
                        add("can_promote_members", "false")
                        add("can_change_info", "false")
                        add("can_invite_users", "true")
                        add("can_pin_messages", "true")
                    }
                    AdminRights.PROMOTE_PERMANENT -> {
                        add("can_manage_chat", "true")
                        add("can_delete_messages", "true")
                        add("can_manage_video_chats", "true")
                        add("can_restrict_members", "true")
                        add("can_promote_members", "true")
                        add("can_change_info", "true")
                        add("can_invite_users", "true")
                        add("can_pin_messages", "true")
                    }
                }
            }
            .build()
        val env = callAndUnwrapEnvelope<Boolean>("promoteChatMember", chatId, body)
        return env.result == true
    }

    suspend fun setChatAdministratorCustomTitle(
        chatId: Long,
        userId: Long,
        customTitle: String
    ): Boolean {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("user_id", userId.toString())
            .add("custom_title", customTitle)
            .build()
        val env = callAndUnwrapEnvelope<Boolean>(
            "setChatAdministratorCustomTitle", chatId, body
        )
        return env.result == true
    }

    // ---------------- Polling ----------------

    suspend fun getUpdates(
        offset: Long? = null,
        limit: Int = 100,
        timeoutSeconds: Int = 0,
        allowedUpdates: List<String>? = null
    ): List<TgUpdate> {
        val builder = FormBody.Builder()
            .add("limit", limit.toString())
            .add("timeout", timeoutSeconds.toString())
        offset?.let { builder.add("offset", it.toString()) }
        allowedUpdates?.let { list ->
            val arr = list.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            builder.add("allowed_updates", arr)
        }
        return callAndUnwrap("getUpdates", 0L, builder.build())
    }

    // ---------------- Internals ----------------

    private suspend inline fun <reified T> callAndUnwrap(
        method: String,
        chatId: Long,
        body: RequestBody,
        needsUpload: Boolean = false
    ): T {
        val env = callAndUnwrapEnvelope<T>(method, chatId, body, needsUpload)
        return env.result
            ?: throw TelegramException(code = -1, message = "Empty result for $method")
    }

    private suspend inline fun <reified T> callAndUnwrapEnvelope(
        method: String,
        chatId: Long,
        body: RequestBody,
        needsUpload: Boolean = false
    ): TgEnvelope<T> {
        return rateLimiter.withRateLimit(chatId) {
            val safeToRetryServerFailure = method !in setOf("sendMessage", "sendDocument")
            TelegramRetry.withRetry(allowServerRetry = safeToRetryServerFailure) {
                val raw = executeRequest(method, body, needsUpload)
                val envelope: TgEnvelope<T> = try {
                    json.decodeFromString<TgEnvelope<T>>(raw)
                } catch (e: Exception) {
                    throw TelegramException(
                        code = -1,
                        message = "Failed to parse Telegram response: ${e.message}",
                        cause = e
                    )
                }
                if (!envelope.ok) {
                    throw TelegramException(
                        code = envelope.errorCode ?: -1,
                        message = envelope.description ?: "Unknown Telegram error",
                        retryAfterSeconds = envelope.parameters?.retryAfter
                    )
                }
                envelope
            }
        }
    }

    private suspend fun executeRequest(
        method: String,
        body: RequestBody,
        needsUpload: Boolean
    ): String = withContext(dispatchers.io) {
        if (token.isBlank()) {
            throw TelegramException(code = 401, message = "Telegram bot token is not configured")
        }
        val url = "${TelegramConfig.BASE_URL}/bot$token/$method"
        val request = Request.Builder().url(url).post(body).build()
        val client = if (needsUpload) {
            http.newBuilder()
                .writeTimeout(TelegramConfig.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TelegramConfig.UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        } else http
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.code >= 500) {
                    throw TelegramException(
                        code = response.code,
                        message = "Telegram server error (HTTP ${response.code})"
                    )
                }
                if (text.isBlank()) {
                    throw TelegramException(
                        code = response.code,
                        message = "Empty response body from Telegram (HTTP ${response.code})"
                    )
                }
                text
            }
        } catch (e: IOException) {
            throw TelegramException(
                code = -1,
                message = "Network error calling $method: ${e.message}",
                cause = e
            )
        }
    }
}

enum class AdminRights {
    PROMOTE,
    PROMOTE_PERMANENT,
    DEMOTE
}
