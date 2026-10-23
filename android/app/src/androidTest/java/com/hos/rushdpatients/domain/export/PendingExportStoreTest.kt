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
}
