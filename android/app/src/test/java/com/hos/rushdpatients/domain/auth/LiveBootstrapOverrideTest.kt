package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.network.telegram.*
import com.hos.rushdpatients.sync.DoctorsRegistryCodec
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit owner-selected disposable group only; skipped in ordinary offline runs. */
class LiveBootstrapOverrideTest {
    @Test fun reviewedOverrideAndChangedPinAgainstLiveTelegram() = runBlocking<Unit> {
        val configPath = System.getenv("SHIFTREPORT_LIVE_BOOTSTRAP_CONFIG")
        assumeTrue("Live disposable-group config was not explicitly supplied", !configPath.isNullOrBlank())
        val supplied = Json.parseToJsonElement(File(requireNotNull(configPath)).readText()).jsonObject
        val config = ProjectConfig(
            botToken = supplied.getValue("botToken").jsonPrimitive.content,
            chatId = supplied.getValue("chatId").jsonPrimitive.content.toLong(),
            initialAdmin = InitialAdminConfig("د. أرز شجرة", supplied.getValue("adminTelegramId").jsonPrimitive.content.toLong())
        )
        val projects = mockk<ProjectConfigStore> { every { current() } returns config }
        val cipher = mockk<ProjectDataCipher> {
            every { enabled } returns false
            coEvery { encodeText(any()) } answers { firstArg() }
            coEvery { decodeText(any(), any()) } answers { firstArg() }
            coEvery { encryptedDocument(any()) } returns null
            coEvery { decryptDocument(any()) } just Runs
        }
        val http = OkHttpClient()
        val client = TelegramClient(BotTokenProvider(projects), cipher,
            Json { ignoreUnknownKeys = true }, DefaultDispatcherProvider(), TelegramRateLimiter(), http)
        val seeder = BootstrapSeeder(client, Topics(projects), projects)
        val created = mutableListOf<Long>()
        val receipt = File(requireNotNull(configPath)).resolveSibling("live-bootstrap-receipt.txt")
        fun record(line: String) { receipt.appendText(line + "\n") }
        val downloaded = File.createTempFile("live-bootstrap-check-", ".txt")
        var originalPin: Long? = null
        var completed = false
        var cleaned = true
        try {
            assertTrue(client.getMe().isBot)
            val chat = client.getChat(config.chatId)
            assertTrue(chat.type in setOf("group", "supergroup"))
            originalPin = chat.pinnedMessage?.messageId
            assertTrue(client.getChatAdministrators(config.chatId).any {
                it.user.id == config.initialAdmin.telegramId && it.isAdmin
            })
            record("original-pin=${originalPin ?: "none"}")
            val first = client.sendMessage(config.chatId, "TEST ONLY — synthetic bootstrap review", parseMode = null)
            created += first.messageId; record("created=${first.messageId}")
            assertTrue(client.pinMessage(config.chatId, first.messageId, true))
            val review = BootstrapSeeder.PinReview(config.chatId, requireNotNull(client.getChat(config.chatId).pinnedMessage))
            assertEquals(first.messageId, review.message.messageId)
            val changed = client.sendMessage(config.chatId, "TEST ONLY — synthetic changed pin", parseMode = null)
            created += changed.messageId; record("created=${changed.messageId}")
            assertTrue(client.pinMessage(config.chatId, changed.messageId, true))
            assertTrue(seeder.seed(review) is BootstrapSeeder.Result.Failure)
            assertEquals(changed.messageId, client.getChat(config.chatId).pinnedMessage?.messageId)
            record("changed-pin-rejected=true")
            val freshReview = BootstrapSeeder.PinReview(config.chatId, requireNotNull(client.getChat(config.chatId).pinnedMessage))
            val result = seeder.seed(freshReview)
            assertTrue("Live seeding did not confirm success; inspect private receipt before retrying", result is BootstrapSeeder.Result.Success)
            val seeded = result as BootstrapSeeder.Result.Success
            created += seeded.messageId; record("created=${seeded.messageId}")
            val pinned = requireNotNull(client.getChat(config.chatId).pinnedMessage)
            assertEquals(seeded.messageId, pinned.messageId)
            val remote = client.getFile(requireNotNull(pinned.document).fileId)
            client.downloadFile(requireNotNull(remote.filePath), downloaded)
            val doctors = DoctorsRegistryCodec.decode(downloaded.readText()).doctors
            assertEquals(config.initialAdmin.telegramId, doctors.single().telegramId)
            assertTrue(doctors.single().isPermanentAdmin)
            record("reviewed-override-and-registry-download=true")
            completed = true
        } catch (failure: Throwable) {
            // Transport exceptions can contain credential-bearing URLs. Never expose causes.
            record("failed-type=${failure.javaClass.simpleName}")
        } finally {
            // Unpin only messages this run created. Existing pins/files/messages are retained.
            for (id in created.asReversed()) {
                try { if (!client.unpinMessage(config.chatId, id)) cleaned = false }
                catch (_: Exception) { cleaned = false }
            }
            try {
                if (created.isNotEmpty() && client.getChat(config.chatId).pinnedMessage?.messageId != originalPin) cleaned = false
            } catch (_: Exception) { cleaned = false }
            record("completed=$completed cleanup-confirmed=$cleaned")
            downloaded.delete()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
        assertTrue("Live check incomplete; inspect private receipt and group before retrying", completed)
        assertTrue("Synthetic-pin cleanup needs inspection; see private receipt", cleaned)
    }
}
