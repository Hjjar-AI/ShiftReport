package com.hos.rushdpatients.domain.export

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
class PendingExportStoreTest {
    private val context = IsolatedStorageContext()
    private fun store() = PendingExportStore(context, DefaultDispatcherProvider())
    @After fun cleanup() = context.cleanUp()
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Invalid export unexpectedly accepted")
    }
    @Test fun encryptedStagingSurvivesRecreationAndWritesVerifiedBytes() = runBlocking<Unit> {
        val bytes = "شاي: بيانات تجريبية only".toByteArray()
        val id = store().stage(bytes)
        val staged = File(context.noBackupFilesDir, "pending_document_exports/$id")
        assertFalse(staged.readBytes().toString(Charsets.UTF_8).contains("شاي"))
        val output = File(context.cacheDir, "export.txt")
        store().write(Uri.fromFile(output), id)
        assertArrayEquals(bytes, output.readBytes())
    }
    @Test fun tamperedCiphertextCannotOverwriteDestination() = runBlocking<Unit> {
        val id = store().stage("synthetic".toByteArray())
        val staged = File(context.noBackupFilesDir, "pending_document_exports/$id")
        staged.writeBytes(staged.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        val output = File(context.cacheDir, "export.txt").apply { writeText("existing") }
        rejected { store().write(Uri.fromFile(output), id) }
        assertEquals("existing", output.readText())
    }
    @Test fun discardAndInvalidIdsCannotAccessOtherFiles() = runBlocking<Unit> {
        val id = store().stage("synthetic".toByteArray())
        store().discard(id)
        rejected { store().write(Uri.fromFile(File(context.cacheDir, "out")), id) }
        rejected { store().discard("../outside") }
        rejected { store().stage(byteArrayOf()) }
    }
    @Test fun identicalPlaintextProducesDifferentAuthenticatedStagingFiles() = runBlocking<Unit> {
        val bytes = "شاي، قهوة\nنتيجة تجريبية".toByteArray(Charsets.UTF_8)
        val first = store().stage(bytes); val second = store().stage(bytes)
        assertNotEquals(first, second)
        val directory = File(context.noBackupFilesDir, "pending_document_exports")
        assertFalse(File(directory, first).readBytes().contentEquals(File(directory, second).readBytes()))
        for (id in listOf(first, second)) {
            val destination = File(context.cacheDir, "$id.txt")
            store().write(Uri.fromFile(destination), id)
            assertArrayEquals(bytes, destination.readBytes())
        }
    }
    @Test fun discardingOneExportPreservesOtherPendingExports() = runBlocking<Unit> {
        val first = store().stage("first".toByteArray()); val second = store().stage("second".toByteArray())
        store().discard(first)
        val destination = File(context.cacheDir, "second.txt")
        store().write(Uri.fromFile(destination), second)
        assertEquals("second", destination.readText())
    }
    @Test fun truncatedCiphertextCannotOverwriteExistingDestination() = runBlocking<Unit> {
        val id = store().stage("synthetic plaintext".toByteArray())
        File(context.noBackupFilesDir, "pending_document_exports/$id").writeBytes(ByteArray(12))
        val destination = File(context.cacheDir, "existing.txt").apply { writeText("preserve this") }
        rejected { store().write(Uri.fromFile(destination), id) }
        assertEquals("preserve this", destination.readText())
    }
    @Test fun stagingCleanupRemovesExpiredExportAndKeepsFreshSibling() = runBlocking<Unit> {
        val expired = store().stage("expired".toByteArray()); val fresh = store().stage("fresh".toByteArray())
        val expiredFile = File(context.noBackupFilesDir, "pending_document_exports/$expired")
        assertTrue(expiredFile.setLastModified(System.currentTimeMillis() - 48L * 60 * 60 * 1000))
        store().stage("newest".toByteArray())
        assertFalse(expiredFile.exists())
        val destination = File(context.cacheDir, "fresh.txt")
        store().write(Uri.fromFile(destination), fresh)
        assertEquals("fresh", destination.readText())
    }
    @Test fun malformedStagingIdsCannotReadOrDeleteSiblingFile() = runBlocking<Unit> {
        val sentinel = File(context.noBackupFilesDir, "outside").apply { writeText("preserve this") }
        val destination = File(context.cacheDir, "out.txt").apply { writeText("original") }
        for (id in listOf("", "../outside", "/outside", "not-a-uuid")) {
            rejected { store().write(Uri.fromFile(destination), id) }
            rejected { store().discard(id) }
        }
        assertEquals("preserve this", sentinel.readText()); assertEquals("original", destination.readText())
    }
}
