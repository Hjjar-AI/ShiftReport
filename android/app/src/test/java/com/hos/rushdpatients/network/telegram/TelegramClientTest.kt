package com.hos.rushdpatients.network.telegram

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

import com.hos.rushdpatients.config.BotTokenProvider
import com.hos.rushdpatients.config.ProjectDataCipher
import com.hos.rushdpatients.util.DispatcherProvider
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Before

/** An application interceptor supplies responses; no socket or live Telegram request is made. */
class TelegramClientTest {
    private val token = mockk<BotTokenProvider>()
    private val cipher = mockk<ProjectDataCipher>()
    private val requests = mutableListOf<Request>()
    private var responseCode = 200
    private var responseBody = """{"ok":true,"result":{"id":123,"is_bot":true,"first_name":"Synthetic"}}"""
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(responseCode)
            .message("Synthetic").body(responseBody.toResponseBody()).build()
    }.build()
    private val client = TelegramClient(token, cipher, Json { ignoreUnknownKeys = true }, dispatchers, TelegramRateLimiter(), http)
    @Before fun setUp() {
        every { token.botToken } returns "synthetic-test-token"
        every { cipher.enabled } returns false
        coEvery { cipher.encodeText(any()) } answers { firstArg() }
    }
    @Test fun `getMe unwraps result and uses current runtime token`() = runBlocking<Unit> {
        assertEquals(123L, client.getMe().id)
        assertEquals("/botsynthetic-test-token/getMe", requests.single().url.encodedPath)
    }
    @Test fun `missing token fails locally without transport`() = runBlocking<Unit> {
        every { token.botToken } returns ""
        try { client.getMe(); fail("Missing token accepted") }
        catch (e: TelegramException) { assertEquals(401, e.code) }
        assertTrue(requests.isEmpty())
    }
    @Test fun `malformed response is ambiguous and never retried`() = runBlocking<Unit> {
        responseBody = "invalid JSON"
        try { client.getMe(); fail("Malformed response accepted") }
        catch (e: TelegramException) { assertEquals(-1, e.code) }
        assertEquals(1, requests.size)
    }
    @Test fun `send server failure is not retried`() = runBlocking<Unit> {
        responseCode = 503
        try { client.sendMessage(-100, "synthetic"); fail("Server failure accepted") }
        catch (e: TelegramException) { assertEquals(503, e.code) }
        assertEquals(1, requests.size)
    }
    @Test fun `encrypted message omits markdown parsing and preserves selected topic`() = runBlocking<Unit> {
        every { cipher.enabled } returns true
        coEvery { cipher.encodeText("synthetic") } returns "SRMSG1:synthetic"
        responseBody = """{"ok":true,"result":{"message_id":1,"date":1,"chat":{"id":-100,"type":"supergroup"}}}"""
        client.sendMessage(-100, "synthetic", messageThreadId = 42)
        val buffer = Buffer()
        requireNotNull(requests.single().body).writeTo(buffer)
        val body = buffer.readUtf8()
        assertTrue(body.contains("message_thread_id=42"))
        assertTrue(body.contains("SRMSG1%3Asynthetic"))
        assertFalse(body.contains("parse_mode"))
    }
    @Test fun `API authorization failure retains error code without retry`() = runBlocking<Unit> {
        responseCode = 403
        responseBody = """{"ok":false,"error_code":403,"description":"synthetic denial"}"""
        try { client.getMe(); fail("Forbidden response accepted") }
        catch (e: TelegramException) { assertTrue(e.isForbidden) }
        assertEquals(1, requests.size)
    }
}
