package com.hos.rushdpatients.testutil

import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.network.telegram.*
import com.hos.rushdpatients.network.telegram.dto.TgMessage
import com.hos.rushdpatients.network.telegram.dto.TgDocument
import com.hos.rushdpatients.sync.SyncState
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/** One fake remote, independent real clients. Every HTTP request terminates here. */
class SharedTelegramServer {
    val json = Json { ignoreUnknownKeys = true }
    var state = SyncState()
    var bootstrapPin: TgMessage? = null
    private val uploadedMessages = mutableMapOf<Long, TgMessage>()
    val files = mutableMapOf<String, ByteArray>()
    val calls = mutableListOf<Pair<String, String>>()
    val deliveredTexts = mutableListOf<String>()
    var fault: (String, String, String) -> Unit = { _, _, _ -> }
    private var nextMessage = 100L
    private fun envelope(value: JsonElement) = buildJsonObject { put("ok", true); put("result", value) }.toString()
    fun client(label: String, projects: ProjectConfigStore, dispatchers: DispatcherProvider): TelegramClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val method = request.url.pathSegments.last()
            val bytes = synchronized(this) {
                calls += label to method
                fault(label, method, "before")
                val form = request.body as? FormBody
                fun field(key: String): String = requireNotNull(form).let { body ->
                    body.value((0 until body.size).first { body.name(it) == key }) }
                val result = when (method) {
                    "getChat" -> envelope(buildJsonObject {
                        put("id", -100); put("type", "supergroup")
                        put("pinned_message", bootstrapPin?.let { json.encodeToJsonElement(it) }
                            ?: buildJsonObject { put("message_id", 1); put("text", json.encodeToString(state)) })
                    }).toByteArray()
                    "getMe" -> envelope(buildJsonObject { put("id", 12345); put("is_bot", true) }).toByteArray()
                    "getChatAdministrators" -> envelope(buildJsonArray {
                        add(buildJsonObject { put("status", "administrator")
                            put("user", buildJsonObject { put("id", 123); put("is_bot", false) }) })
                    }).toByteArray()
                    "pinChatMessage" -> {
                        bootstrapPin = requireNotNull(uploadedMessages[field("message_id").toLong()])
                        envelope(JsonPrimitive(true)).toByteArray()
                    }
                    "getChatMember" -> envelope(buildJsonObject {
                        put("status", "administrator"); put("can_pin_messages", true)
                        put("user", buildJsonObject { put("id", 12345); put("is_bot", true) })
                    }).toByteArray()
                    "getFile" -> envelope(buildJsonObject {
                        val id = field("file_id"); check(files.containsKey(id))
                        put("file_id", id); put("file_path", "$id.bin")
                    }).toByteArray()
                    "sendDocument" -> {
                        val id = "synthetic-${nextMessage}"
                        val upload = (request.body as MultipartBody).parts.first {
                            it.headers?.get("Content-Disposition")?.contains("name=\"document\"") == true }
                        files[id] = Buffer().also { upload.body.writeTo(it) }.readByteArray()
                        uploadedMessages[nextMessage] = TgMessage(nextMessage, document = TgDocument(id))
                        envelope(buildJsonObject {
                            put("message_id", nextMessage++); put("date", 1800000000)
                            put("document", buildJsonObject { put("file_id", id) })
                        }).toByteArray()
                    }
                    "editMessageText" -> {
                        state = json.decodeFromString<SyncState>(field("text"))
                        envelope(buildJsonObject { put("message_id", 1); put("text", field("text")) }).toByteArray()
                    }
                    "sendMessage" -> {
                        deliveredTexts += field("text")
                        envelope(buildJsonObject { put("message_id", nextMessage++); put("date", 1800000000) }).toByteArray()
                    }
                    else -> if (method.endsWith(".bin")) requireNotNull(files[method.removeSuffix(".bin")])
                        else error("Unexpected synthetic method: $method")
                }
                fault(label, method, "after")
                result
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic")
                .body(bytes.toResponseBody()).build()
        }.build()
        return TelegramClient(BotTokenProvider(projects), ProjectDataCipher(projects, dispatchers), json,
            dispatchers, TelegramRateLimiter(), http)
    }
}
