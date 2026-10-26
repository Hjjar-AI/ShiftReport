package com.hos.rushdpatients.config

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.testutil.IsolatedStorageContext
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProjectProvisioningManagerTest {
    private val context = IsolatedStorageContext()
    private val dispatchers = DefaultDispatcherProvider()
    private fun manager() = ProjectProvisioningManager(context, dispatchers)
    private val password get() = "synthetic-password".toCharArray()
    private val config = ProjectConfig(initialized = true, hospitalName = "مشروع تجريبي",
        botToken = "12345:" + "a".repeat(20), chatId = -100, reportsTopicId = 1,
        announcementsTopicId = 2, csvTopicId = 3, doctorsTopicId = 4, telegramDataKey = ProjectDataCipher.generateKey())
    private fun destination() = Uri.fromFile(File(context.cacheDir, "join.srjoin.json"))
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Invalid join/export unexpectedly accepted")
    }
    @After fun cleanup() = context.cleanUp()
    @Test fun protectedJsonRoundTripPreservesConnectionAndKeyOnly() = runBlocking<Unit> {
        val pass = password
        val encrypted = manager().prepareExport(config, pass)
        assertTrue(pass.all { it == '\u0000' })
        manager().writeExport(destination(), encrypted)
        val document = File(requireNotNull(destination().path)).readText()
        assertFalse(document.contains(config.botToken))
        assertFalse(document.contains(config.telegramDataKey))
        val importPass = password
        val imported = manager().importConfig(destination(), importPass)
        assertTrue(importPass.all { it == '\u0000' })
        assertEquals(config.copy(initialized = false), imported)
    }
    @Test fun wrongPasswordTamperingAndEmptyFilesAreRejectedAndWiped() = runBlocking<Unit> {
        val encrypted = manager().prepareExport(config, password)
        manager().writeExport(destination(), encrypted)
        val wrong = "wrong-password".toCharArray()
        rejected { manager().importConfig(destination(), wrong) }
        assertTrue(wrong.all { it == '\u0000' })
        val damaged = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        File(requireNotNull(destination().path)).writeBytes(damaged)
        val pass = password
        rejected { manager().importConfig(destination(), pass) }
        assertTrue(pass.all { it == '\u0000' })
        File(requireNotNull(destination().path)).writeBytes(byteArrayOf())
        val emptyPass = password
        rejected { manager().importConfig(destination(), emptyPass) }
        assertTrue(emptyPass.all { it == '\u0000' })
    }
    @Test fun stagingSurvivesManagerRecreationAndDiscardRemovesOnlyThatExport() = runBlocking<Unit> {
        val id = manager().stageExport(manager().prepareExport(config, password))
        manager().writeStagedExport(destination(), id)
        assertEquals(config.chatId, manager().importConfig(destination(), password).chatId)
        manager().discardStagedExport(id)
        rejected { manager().writeStagedExport(destination(), id) }
    }
    @Test fun invalidProjectAndShortPasswordsAreRejectedWithoutRetainingPassword() = runBlocking<Unit> {
        for (candidate in listOf(config.copy(initialized = false), config.copy(demoMode = true), config.copy(telegramDataKey = "invalid"))) {
            val pass = password
            rejected { manager().prepareExport(candidate, pass) }
            assertTrue(pass.all { it == '\u0000' })
        }
        val pass = "short".toCharArray()
        rejected { manager().importConfig(destination(), pass) }
        assertTrue(pass.all { it == '\u0000' })
        rejected { manager().writeStagedExport(destination(), "../outside") }
    }
}
