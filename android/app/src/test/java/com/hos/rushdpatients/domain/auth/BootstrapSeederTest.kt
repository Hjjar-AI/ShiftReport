package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.dto.*
import com.hos.rushdpatients.sync.DoctorsRegistryCodec
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class BootstrapSeederTest {
    private val telegram = mockk<TelegramClient>()
    private val projects = mockk<ProjectConfigStore>()
    private val topics = mockk<Topics> { every { chatId } returns -100 }
    private var config = ProjectConfig(initialAdmin = InitialAdminConfig("د. أرز شجرة", 123, "F", "SUPERVISOR"))
    private var uploaded: File? = null
    private var registry: List<Doctor> = emptyList()
    private fun seeder() = BootstrapSeeder(telegram, topics, projects)
    @Before fun emptyProject() {
        every { projects.current() } answers { config }
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "supergroup")
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            uploaded = secondArg<File>()
            registry = DoctorsRegistryCodec.decode(uploaded!!.readText()).doctors
            TgMessage(42)
        }
        coEvery { telegram.pinMessage(-100, 42, true) } returns true
    }
    private fun assertNoPublication() {
        coVerify(exactly = 0) { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { telegram.pinMessage(any(), any(), any()) }
    }
    @Test fun initializedProjectCannotSeedRegistry() = runBlocking<Unit> {
        config = config.copy(initialized = true)
        assertTrue(seeder().seed() is BootstrapSeeder.Result.Failure)
        assertNoPublication()
        coVerify(exactly = 0) { telegram.getChat(any()) }
    }
    @Test fun demoProjectCannotSeedRegistry() = runBlocking<Unit> {
        config = config.copy(demoMode = true)
        assertTrue(seeder().seed() is BootstrapSeeder.Result.Failure)
        assertNoPublication()
    }
    @Test fun existingPinIsNeverReplaced() = runBlocking<Unit> {
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "supergroup", pinnedMessage = TgMessage(1))
        assertTrue(seeder().seed() is BootstrapSeeder.Result.Failure)
        assertNoPublication()
    }
    @Test fun invalidInitialTelegramIdentityCannotUpload() = runBlocking<Unit> {
        config = config.copy(initialAdmin = config.initialAdmin.copy(telegramId = 0))
        assertTrue(seeder().seed() is BootstrapSeeder.Result.Failure)
        assertNoPublication()
    }
    @Test fun blankInitialAdminNameCannotUpload() = runBlocking<Unit> {
        config = config.copy(initialAdmin = config.initialAdmin.copy(fullName = "  "))
        assertTrue(seeder().seed() is BootstrapSeeder.Result.Failure)
        assertNoPublication()
    }
    @Test fun successfulSeedPublishesOnePermanentAdminAndRemovesTemporaryRegistry() = runBlocking<Unit> {
        assertEquals(BootstrapSeeder.Result.Success(42, 1), seeder().seed())
        val doctor = registry.single()
        assertEquals("د. أرز شجرة", doctor.fullName)
        assertEquals("أرز", doctor.firstName); assertEquals("شجرة", doctor.lastName)
        assertEquals(123L, doctor.telegramId)
        assertEquals(Gender.FEMALE, doctor.gender); assertEquals(ClinicalRole.SUPERVISOR, doctor.clinicalRole)
        assertTrue(doctor.isPermanentAdmin); assertTrue(doctor.isAdmin)
        assertTrue(doctor.extraOptions.none { it.startsWith("pin:") })
        assertFalse(requireNotNull(uploaded).exists())
        coVerifyOrder { telegram.getChat(-100); telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()); telegram.getChat(-100); telegram.pinMessage(-100, 42, true) }
    }
    @Test fun projectChangeDuringUploadPreventsPinningUploadedRegistry() = runBlocking<Unit> {
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            uploaded = secondArg(); config = config.copy(hospitalName = "changed project"); TgMessage(42)
        }
        assertTrue(seeder().seed() is BootstrapSeeder.Result.Failure)
        coVerify(exactly = 0) { telegram.pinMessage(any(), any(), any()) }
        assertFalse(requireNotNull(uploaded).exists())
    }
    @Test fun newPinAppearingDuringUploadIsNotOverwritten() = runBlocking<Unit> {
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "supergroup") andThen
            TgChat(-100, "supergroup", pinnedMessage = TgMessage(99))
        assertTrue(seeder().seed() is BootstrapSeeder.Result.Failure)
        coVerify(exactly = 0) { telegram.pinMessage(any(), any(), any()) }
        assertFalse(requireNotNull(uploaded).exists())
    }
    @Test fun failedUploadDeletesTemporaryFileWithoutPinning() = runBlocking<Unit> {
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            uploaded = secondArg(); throw IOException("synthetic upload failure")
        }
        assertEquals(BootstrapSeeder.Result.Failure("synthetic upload failure"), seeder().seed())
        assertFalse(requireNotNull(uploaded).exists())
        coVerify(exactly = 0) { telegram.pinMessage(any(), any(), any()) }
    }
    @Test fun cancelledUploadPropagatesAndDeletesTemporaryFile() = runBlocking<Unit> {
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            uploaded = secondArg(); throw CancellationException("synthetic cancel")
        }
        try { seeder().seed(); fail("Cancellation swallowed") } catch (_: CancellationException) { }
        assertFalse(requireNotNull(uploaded).exists())
        coVerify(exactly = 0) { telegram.pinMessage(any(), any(), any()) }
    }
    @Test fun pinFailureDoesNotReturnSuccessOrUploadAgain() = runBlocking<Unit> {
        coEvery { telegram.pinMessage(any(), any(), any()) } throws IOException("synthetic pin failure")
        assertEquals(BootstrapSeeder.Result.Failure("synthetic pin failure"), seeder().seed())
        coVerify(exactly = 1) { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) }
        assertFalse(requireNotNull(uploaded).exists())
    }
    @Test fun chatLookupFailureDoesNotUploadOrPin() = runBlocking<Unit> {
        coEvery { telegram.getChat(any()) } throws IOException("synthetic offline")
        assertEquals(BootstrapSeeder.Result.Failure("synthetic offline"), seeder().seed())
        assertNoPublication()
    }
    /** A false Telegram receipt must not be presented as successful project seeding. */
    @Test fun unconfirmedPinReceiptCannotReturnSuccessfulSeed() = runBlocking<Unit> {
        coEvery { telegram.pinMessage(-100, 42, true) } returns false
        assertTrue("An unconfirmed pin must require review", seeder().seed() is BootstrapSeeder.Result.Failure)
        coVerify(exactly = 1) { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) }
        assertFalse(requireNotNull(uploaded).exists())
    }
}
