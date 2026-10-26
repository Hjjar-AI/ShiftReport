package com.hos.rushdpatients.ui.settings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.backup.EncryptedBackupManager
import com.hos.rushdpatients.domain.export.PendingExportStore
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackupOperationsTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val state = MutableStateFlow(SettingsUiState())
    private val backup = mockk<EncryptedBackupManager>()
    private val pending = mockk<PendingExportStore>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val handle = SavedStateHandle()
    private val uri = mockk<Uri>()
    private var refreshed = 0
    private fun operations(scope: CoroutineScope) = SettingsBackupOperations(text, state, scope, backup,
        handle, pending, settings) { refreshed++ }

    @Test fun `preparation stages encrypted bytes wipes arrays and saves only opaque metadata`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val password = slot<CharArray>()
        coEvery { backup.prepareExport(capture(password)) } returns EncryptedBackupManager.PreparedBackup(bytes, 7)
        coEvery { pending.stage(any()) } answers { assertArrayEquals(byteArrayOf(1, 2, 3), firstArg()); "opaque-id" }
        operations(this).prepareEncryptedBackup("synthetic-password")
        runCurrent()
        assertTrue(password.captured.all { it == '\u0000' })
        assertArrayEquals(ByteArray(3), bytes)
        assertEquals(setOf(PENDING_BACKUP_ID, PENDING_BACKUP_COUNT), handle.keys())
        assertEquals("opaque-id", handle.get<String>(PENDING_BACKUP_ID))
        assertTrue(state.value.backupReady)
    }
    @Test fun `staging failure wipes bytes and password and clears busy state`() = runTest {
        val bytes = byteArrayOf(4, 5)
        val password = slot<CharArray>()
        coEvery { backup.prepareExport(capture(password)) } returns EncryptedBackupManager.PreparedBackup(bytes, 1)
        coEvery { pending.stage(any()) } throws IllegalStateException("synthetic disk full")
        operations(this).prepareEncryptedBackup("synthetic-password"); runCurrent()
        assertArrayEquals(ByteArray(2), bytes)
        assertTrue(password.captured.all { it == '\u0000' })
        assertFalse(state.value.backupBusy); assertFalse(state.value.backupReady)
        assertTrue(handle.keys().isEmpty())
    }
    @Test fun `duplicate preparation cannot stage two backups`() = runTest {
        coEvery { backup.prepareExport(any()) } coAnswers { awaitCancellation() }
        val operation = operations(backgroundScope)
        operation.prepareEncryptedBackup("synthetic-password")
        operation.prepareEncryptedBackup("synthetic-password")
        runCurrent()
        coVerify(exactly = 1) { backup.prepareExport(any()) }
    }
    @Test fun `cancelled preparation wipes password without showing ordinary error`() = runTest {
        val password = slot<CharArray>()
        coEvery { backup.prepareExport(capture(password)) } throws CancellationException("synthetic cancellation")
        operations(this).prepareEncryptedBackup("synthetic-password"); runCurrent()
        assertTrue(password.captured.all { it == '\u0000' })
        assertFalse(state.value.backupBusy); assertNull(state.value.snackbar)
    }
    @Test fun `cancelled picker discards staging without writing destination`() = runTest {
        handle[PENDING_BACKUP_ID] = "opaque-id"; handle[PENDING_BACKUP_COUNT] = 7
        operations(this).exportEncryptedBackup(null); runCurrent()
        coVerify { pending.discard("opaque-id") }
        coVerify(exactly = 0) { pending.write(any(), any()) }
        assertTrue(handle.keys().isEmpty()); assertFalse(state.value.backupBusy)
    }
    @Test fun `export success stamps time only after verified write and removes staging`() = runTest {
        handle[PENDING_BACKUP_ID] = "opaque-id"; handle[PENDING_BACKUP_COUNT] = 7
        operations(this).exportEncryptedBackup(uri); runCurrent()
        coVerifyOrder {
            pending.write(uri, "opaque-id")
            settings.putLong(AppConstants.SETTING_LAST_ENCRYPTED_BACKUP_AT, any())
            pending.discard("opaque-id")
        }
        assertEquals(1, refreshed); assertFalse(state.value.backupBusy)
    }
    @Test fun `failed destination removes partial file and never records successful backup`() = runTest {
        handle[PENDING_BACKUP_ID] = "opaque-id"
        coEvery { pending.write(uri, "opaque-id") } throws IllegalStateException("synthetic partial write")
        operations(this).exportEncryptedBackup(uri); runCurrent()
        coVerify { pending.discardDestination(uri); pending.discard("opaque-id") }
        coVerify(exactly = 0) { settings.putLong(any(), any()) }
        assertFalse(state.value.backupBusy)
    }
    @Test fun `duplicate picker callback cannot write the same export twice`() = runTest {
        handle[PENDING_BACKUP_ID] = "opaque-id"
        coEvery { pending.write(uri, "opaque-id") } coAnswers { awaitCancellation() }
        val operation = operations(backgroundScope)
        operation.exportEncryptedBackup(uri); operation.exportEncryptedBackup(uri); runCurrent()
        coVerify(exactly = 1) { pending.write(uri, "opaque-id") }
    }
    @Test fun `restore cancellation propagates without error snackbar and wipes password`() = runTest {
        val password = slot<CharArray>()
        coEvery { backup.restore(uri, capture(password)) } throws CancellationException("synthetic cancellation")
        operations(this).restoreEncryptedBackup(uri, "synthetic-password"); runCurrent()
        assertTrue(password.captured.all { it == '\u0000' })
        assertFalse(state.value.backupBusy); assertNull(state.value.snackbar)
        assertEquals(0, refreshed)
    }
    @Test fun `restore failure wipes password and leaves retry available`() = runTest {
        val password = slot<CharArray>()
        coEvery { backup.restore(uri, capture(password)) } throws IllegalArgumentException("synthetic bad backup")
        operations(this).restoreEncryptedBackup(uri, "synthetic-password"); runCurrent()
        assertTrue(password.captured.all { it == '\u0000' })
        assertFalse(state.value.backupBusy)
        assertEquals("synthetic bad backup", state.value.snackbar)
    }
}
