package com.hos.rushdpatients.testutil

import android.content.Context
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.network.telegram.*
import com.hos.rushdpatients.network.telegram.dto.*
import com.hos.rushdpatients.sync.SyncState
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody

/** Intercepts every HTTP request before network I/O; unexpected methods fail locally. */
class SyntheticTelegram(context: Context, dispatchers: DispatcherProvider) {
    val json = Json { ignoreUnknownKeys = true }
    val projects = ProjectConfigStore(context).apply { saveDraft(ProjectConfig(hospitalName = "تجريبي",
        botToken = "12345:" + "a".repeat(20), chatId = -100, csvTopicId = 3, doctorsTopicId = 4)) }
    val topics = Topics(projects)
    var state = SyncState()
    val files = mutableMapOf<String, String>()
    val methods = mutableListOf<String>()
    var beforeResponse: (String) -> Unit = {}
    private fun envelope(value: JsonElement) = buildJsonObject { put("ok", true); put("result", value) }.toString()
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val method = request.url.pathSegments.last()
        methods += method
        beforeResponse(method)
        val form = request.body as? FormBody
        fun field(key: String) = form?.let { body -> (0 until body.size).firstOrNull { body.name(it) == key }?.let { body.value(it) } }
        val body = when {
            method.endsWith(".txt") -> requireNotNull(files[method.removeSuffix(".txt")])
            method == "getChat" -> envelope(json.encodeToJsonElement(TgChat(-100, "supergroup", pinnedMessage = TgMessage(1, text = json.encodeToString(state)))))
            method == "getFile" -> envelope(buildJsonObject { val id = requireNotNull(field("file_id")); put("file_id", id); put("file_path", "$id.txt") })
            method == "editMessageText" -> {
                val text = requireNotNull(field("text"))
                state = json.decodeFromString<SyncState>(text)
                envelope(json.encodeToJsonElement(TgMessage(1, text = text)))
            }
            else -> error("Unexpected synthetic Telegram method: $method")
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic")
            .body(body.toResponseBody()).build()
    }.build()
    val client = TelegramClient(BotTokenProvider(projects), ProjectDataCipher(projects, dispatchers), json, dispatchers, TelegramRateLimiter(), http)
}
