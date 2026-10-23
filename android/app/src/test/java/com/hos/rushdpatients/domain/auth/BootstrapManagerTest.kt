package com.hos.rushdpatients.domain.auth

import android.content.Context
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.dto.*
import com.hos.rushdpatients.sync.*
import com.hos.rushdpatients.testutil.Fixtures
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BootstrapManagerTest {
    private val directory = Files.createTempDirectory("bootstrap_test_").toFile()
    private val context = mockk<Context> { every { cacheDir } returns directory }
    private val telegram = mockk<TelegramClient>()
    private val topics = mockk<Topics> { every { chatId } returns -100 }
    private val doctors = mockk<DoctorRepository>(relaxed = true)
    private val manager = BootstrapManager(context, telegram, topics, Json, doctors)
    @After fun cleanup() { directory.deleteRecursively() }
    @Test fun `absent unknown and incomplete pins never replace local registry`() = runBlocking<Unit> {
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "supergroup")
        assertEquals(BootstrapResult.NoPin, manager.bootstrap())
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "supergroup", pinnedMessage = TgMessage(1, text = "unrecognized"))
        assertEquals(BootstrapResult.UnrecognizedPin, manager.bootstrap())
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "supergroup", pinnedMessage = TgMessage(1, text = Json.encodeToString(SyncState())))
        assertTrue(manager.bootstrap() is BootstrapResult.Failed)
        coVerify(exactly = 0) { doctors.replaceAll(any()) }
    }
    @Test fun `recognized pinned registry imports through guarded repository and cleans cache`() = runBlocking<Unit> {
        val registry = listOf(Fixtures.doctor(rank = 1))
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "supergroup", pinnedMessage = TgMessage(1, document = TgDocument("registry")))
        coEvery { telegram.getFile("registry") } returns TgFile("registry", filePath = "registry.txt")
        coEvery { telegram.downloadFile("registry.txt", any()) } answers {
            secondArg<File>().apply { writeText(DoctorsRegistryCodec.encode(registry)) }
        }
        assertEquals(BootstrapResult.Success(1), manager.bootstrap())
        coVerify { doctors.replaceAll(match { it.single().id == registry.single().id }) }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun `bootstrap cancellation propagates`() = runBlocking<Unit> {
        coEvery { telegram.getChat(-100) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { manager.bootstrap() } }
    }
}
