package com.hos.rushdpatients.ui.setup

import android.content.Context
import android.net.Uri
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.*
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.dto.*
import com.hos.rushdpatients.testutil.ViewModelFixture
import io.mockk.*
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProjectSetupViewModelTest : ViewModelFixture() {
    private val projects = mockk<ProjectConfigStore>(relaxed = true)
    private val telegram = mockk<TelegramClient>()
    private val bootstrap = mockk<BootstrapManager>()
    private val seeder = mockk<BootstrapSeeder>()
    private val sessions = mockk<SessionManager>(relaxed = true)
    private val provisioning = mockk<ProjectProvisioningManager>()
    private val uri = mockk<Uri>()
    // Generated syntax-only value; never a usable bot credential.
    private val token = "12345:" + "x".repeat(24)
    private fun vm() = keep(ProjectSetupViewModel(text, mockk<Context>(), projects, telegram,
        bootstrap, seeder, sessions, mockk<SettingsRepository>(), provisioning))
    private fun validCreate() = vm().apply {
        setMode(ProjectSetupMode.CREATE); setHospitalName("مشفى تجريبي"); setBotToken(token)
        setChatId("-100"); setAdminName("أرز شجرة"); setAdminTelegramId("123")
    }
    @Before fun configureTransport() {
        coEvery { telegram.getMe() } returns TgUser(12345, isBot = true)
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "supergroup")
        coEvery { seeder.seed() } returns BootstrapSeeder.Result.Success(1, 1)
        coEvery { bootstrap.bootstrap() } returns BootstrapResult.Success(1)
    }
    private fun assertNoInitialization() {
        verify(exactly = 0) { projects.markInitialized() }
        verify(exactly = 0) { sessions.clear() }
    }
    @Test fun demoEntryClearsSessionWithoutSeedingOrNetwork() {
        vm().initialize()
        verifyOrder { sessions.clear(); projects.enterDemo() }
        coVerify(exactly = 0) { telegram.getMe() }
        coVerify(exactly = 0) { seeder.seed() }
        verify(exactly = 0) { projects.saveDraft(any()) }
    }
    @Test fun joinRequiresProtectedFileRegardlessOfManualFields() {
        val vm = validCreate(); vm.setMode(ProjectSetupMode.JOIN); vm.initialize()
        assertNotNull(vm.state.value.error)
        verify(exactly = 0) { projects.saveDraft(any()) }
        coVerify(exactly = 0) { telegram.getMe() }
    }
    @Test fun invalidHospitalNameNeverWritesDraft() {
        val vm = validCreate(); vm.setHospitalName("a"); vm.initialize()
        assertNotNull(vm.state.value.error)
        verify(exactly = 0) { projects.saveDraft(any()) }
    }
    @Test fun malformedTokenNeverStartsNetworkValidation() {
        val vm = validCreate(); vm.setBotToken("invalid"); vm.initialize()
        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { telegram.getMe() }
    }
    @Test fun nonnegativeChatIdCannotCreateProject() {
        val vm = validCreate(); vm.setChatId("100"); vm.initialize()
        assertNotNull(vm.state.value.error)
        verify(exactly = 0) { projects.saveDraft(any()) }
    }
    @Test fun overflowTopicCannotCreateProject() {
        val vm = validCreate(); vm.setCsvTopicId("9".repeat(30)); vm.initialize()
        assertNotNull(vm.state.value.error)
        verify(exactly = 0) { projects.saveDraft(any()) }
    }
    @Test fun missingAdminIdentityCannotCreateProject() {
        val vm = validCreate(); vm.setAdminTelegramId("0"); vm.initialize()
        assertNotNull(vm.state.value.error)
        verify(exactly = 0) { projects.saveDraft(any()) }
    }
    @Test fun createSavesValidatedDraftBeforeBootstrapAndInitialization() {
        val vm = validCreate(); vm.setHospitalName("  مشفى تجريبي  "); vm.setReportsTopicId("12"); vm.initialize()
        verify { projects.saveDraft(match { it.hospitalName == "مشفى تجريبي" && it.botToken == token &&
            it.chatId == -100L && it.reportsTopicId == 12L && it.telegramDataKey.isEmpty() }) }
        coVerifyOrder { telegram.getMe(); telegram.getChat(-100); seeder.seed(); bootstrap.bootstrap() }
        verifyOrder { projects.saveDraft(any()); sessions.clear(); projects.markInitialized() }
    }
    @Test fun existingPinnedMessageBlocksCreateBeforeSeeding() {
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "supergroup", pinnedMessage = TgMessage(10))
        val vm = validCreate(); vm.initialize()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 0) { seeder.seed() }
        assertNoInitialization()
    }
    @Test fun privateChatCannotBeInitialized() {
        coEvery { telegram.getChat(-100) } returns TgChat(-100, "private")
        val vm = validCreate(); vm.initialize()
        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { seeder.seed() }
        assertNoInitialization()
    }
    @Test fun nonBotIdentityCannotBeInitialized() {
        coEvery { telegram.getMe() } returns TgUser(12345, isBot = false)
        val vm = validCreate(); vm.initialize()
        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { telegram.getChat(any()) }
        assertNoInitialization()
    }
    @Test fun seedFailureDoesNotBootstrapOrInitialize() {
        coEvery { seeder.seed() } returns BootstrapSeeder.Result.Failure("synthetic seed failure")
        val vm = validCreate(); vm.initialize()
        assertEquals("synthetic seed failure", vm.state.value.error)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 0) { bootstrap.bootstrap() }
        assertNoInitialization()
    }
    @Test fun bootstrapFailurePreservesExistingSession() {
        coEvery { bootstrap.bootstrap() } returns BootstrapResult.Failed("synthetic bootstrap failure")
        val vm = validCreate(); vm.initialize()
        assertEquals("synthetic bootstrap failure", vm.state.value.error)
        assertFalse(vm.state.value.busy)
        assertNoInitialization()
    }
    @Test fun missingBootstrapPinDoesNotInitialize() {
        coEvery { bootstrap.bootstrap() } returns BootstrapResult.NoPin
        val vm = validCreate(); vm.initialize()
        assertNotNull(vm.state.value.error)
        assertNoInitialization()
    }
    @Test fun unrecognizedBootstrapPinDoesNotInitialize() {
        coEvery { bootstrap.bootstrap() } returns BootstrapResult.UnrecognizedPin
        val vm = validCreate(); vm.initialize()
        assertNotNull(vm.state.value.error)
        assertNoInitialization()
    }
    @Test fun shortPassphraseDoesNotReadJoinFile() {
        val vm = vm(); vm.setMode(ProjectSetupMode.JOIN); vm.setProvisioningPassphrase("x"); vm.importProvisioning(uri)
        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { provisioning.importConfig(any(), any()) }
    }
    @Test fun importedJoinRetainsKeyAndTopicsWithoutSeeding() {
        val imported = ProjectConfig(hospitalName = "مشروع مستورد", botToken = token, chatId = -100,
            reportsTopicId = 1, announcementsTopicId = 2, csvTopicId = 3, doctorsTopicId = 4,
            telegramDataKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        coEvery { provisioning.importConfig(uri, any()) } returns imported
        val vm = vm(); vm.setMode(ProjectSetupMode.JOIN); vm.setProvisioningPassphrase("synthetic password")
        vm.importProvisioning(uri)
        assertTrue(vm.state.value.importedProvisioning)
        assertTrue(vm.state.value.encryptTelegram)
        assertEquals("", vm.state.value.provisioningPassphrase)
        vm.setEncryptTelegram(false)
        assertTrue(vm.state.value.encryptTelegram)
        vm.initialize()
        verify { projects.saveDraft(match { it.telegramDataKey == imported.telegramDataKey &&
            it.reportsTopicId == 1L && it.announcementsTopicId == 2L && it.csvTopicId == 3L && it.doctorsTopicId == 4L }) }
        coVerify(exactly = 0) { seeder.seed() }
        verify(exactly = 1) { projects.markInitialized() }
    }
    @Test fun importFailureClearsPassphraseAndBusyState() {
        coEvery { provisioning.importConfig(uri, any()) } throws IllegalArgumentException("synthetic bad password")
        val vm = vm(); vm.setMode(ProjectSetupMode.JOIN); vm.setProvisioningPassphrase("synthetic password"); vm.importProvisioning(uri)
        assertEquals("", vm.state.value.provisioningPassphrase)
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.importedProvisioning)
        assertEquals("synthetic bad password", vm.state.value.error)
        assertNoInitialization()
    }
    @Test fun changingModeDiscardsImportedScopeAndEncryptionChoice() {
        coEvery { provisioning.importConfig(uri, any()) } returns ProjectConfig(telegramDataKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        val vm = vm(); vm.setMode(ProjectSetupMode.JOIN); vm.setProvisioningPassphrase("synthetic password"); vm.importProvisioning(uri)
        vm.setMode(ProjectSetupMode.CREATE)
        assertFalse(vm.state.value.importedProvisioning)
        assertFalse(vm.state.value.encryptTelegram)
        assertEquals("", vm.state.value.telegramDataKey)
    }
    @Test fun duplicateInitializeCannotSeedTwiceWhileNetworkIsPending() {
        coEvery { telegram.getMe() } coAnswers { awaitCancellation() }
        val vm = validCreate(); vm.initialize(); vm.initialize(); vm.setMode(ProjectSetupMode.DEMO)
        assertTrue(vm.state.value.busy)
        assertEquals(ProjectSetupMode.CREATE, vm.state.value.mode)
        coVerify(exactly = 1) { telegram.getMe() }
        verify(exactly = 1) { projects.saveDraft(any()) }
    }
    @Test fun cancellationCannotMarkProjectInitializedOrClearSession() {
        coEvery { telegram.getMe() } coAnswers { awaitCancellation() }
        val vm = validCreate(); vm.initialize(); clearViewModels()
        assertNull(vm.state.value.error)
        assertNoInitialization()
        coVerify(exactly = 0) { seeder.seed() }
    }
}
