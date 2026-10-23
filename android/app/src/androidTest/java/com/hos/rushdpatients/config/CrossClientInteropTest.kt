package com.hos.rushdpatients.config

import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.domain.backup.EncryptedBackupManager
import com.hos.rushdpatients.sync.CsvBundleCodec
import com.hos.rushdpatients.testutil.ClinicalRepositoryFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Python-produced fixtures enter the actual Android cipher, join and backup implementations. */
@RunWith(AndroidJUnit4::class)
class CrossClientInteropTest : ClinicalRepositoryFixture() {
    private val assets get() = InstrumentationRegistry.getInstrumentation().context.assets
    private val key get() = Base64.encodeToString(ByteArray(32) { it.toByte() }, Base64.NO_WRAP)
    private fun config() = ProjectConfig(initialized = true, hospitalName = "مشروع تجريبي",
        botToken = "12345:" + "a".repeat(20), chatId = -100, reportsTopicId = 1,
        announcementsTopicId = 2, csvTopicId = 3, doctorsTopicId = 4, telegramDataKey = key)
    private fun cipher() = ProjectDataCipher(ProjectConfigStore(preferences).apply { saveDraft(config()) }, dispatchers)
    private fun copyAsset(name: String) = File(preferences.cacheDir, name).apply {
        assets.open("interop/$name").use { input -> outputStream().use { input.copyTo(it) } }
    }
    private fun output() = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        "verification-artifacts/interop").apply { mkdirs() }
    @Test fun desktopEncryptedBundleDecryptsAndRetainsClinicalTasks() = runBlocking<Unit> {
        val file = copyAsset("desktop.srdata")
        cipher().decryptDocument(file)
        val parsed = CsvBundleCodec.decode(file.readText()).single()
        assertEquals("synthetic-shift", parsed.shiftId)
        val patient = parsed.patients.single()
        assertEquals("شاي", patient.name)
        assertEquals("نتيجة ☕", patient.labs)
        assertEquals(2, patient.tasks.size)
        assertEquals("cedar", patient.tasks.single { it.done }.completedByDoctorId)
        assertEquals("أرز", patient.tasks.single { it.done }.completedByName)
    }
    @Test fun desktopProtectedJoinImportsAndAndroidJoinIsExportedForReverseCheck() = runBlocking<Unit> {
        val provisioning = ProjectProvisioningManager(preferences, dispatchers)
        val imported = provisioning.importConfig(Uri.fromFile(copyAsset("desktop.srjoin.json")), "synthetic-password".toCharArray())
        assertEquals(config().copy(initialized = false), imported)
        val bytes = provisioning.prepareExport(config(), "synthetic-password".toCharArray())
        try { provisioning.writeExport(Uri.fromFile(File(output(), "android.srjoin.json")), bytes) }
        finally { bytes.fill(0) }
    }
    @Test fun desktopEncryptedTextDecodesAndAndroidTextIsExportedForReverseCheck() = runBlocking<Unit> {
        val cipher = cipher()
        val text = "شاي — نتيجة تجريبية ☕"
        assertEquals(text, cipher.decodeText(assets.open("interop/desktop-text.txt").bufferedReader().use { it.readText() }, true))
        File(output(), "android-text.txt").writeText(cipher.encodeText(text))
    }
    @Test fun desktopBackupRestoresAndAndroidBackupAndEncryptedFileAreExported() = runBlocking<Unit> {
        // A distinct clinical identity must not merge into the seeded admin's name.
        doctors.upsert(doctor("cedar", "سدر"))
        assertNotNull(doctors.getById("cedar"))
        val manager = EncryptedBackupManager(preferences, db, shifts, patients, doctors, settings, dispatchers)
        assertEquals(1, manager.restore(Uri.fromFile(copyAsset("desktop.rpb")), "synthetic-password".toCharArray()))
        // Restore reuses an existing local shift for the same date.
        val savedShift = requireNotNull(shifts.getByDate(java.time.LocalDate.of(2026, 1, 1)))
        assertEquals("shift-1", savedShift.id)
        val stored = patients.getForShift(savedShift.id).single()
        assertEquals("نتيجة ☕", stored.labs)
        assertEquals(2, stored.tasks.size)
        assertTrue(stored.revision > 7)
        // prepareExport exports today's shift. Move this isolated fixture's shift to today.
        shifts.upsert(savedShift.copy(date = com.hos.rushdpatients.util.ShiftDate.current()))
        val backup = manager.prepareExport("synthetic-password".toCharArray())
        try { File(output(), "android.rpb").writeBytes(backup.bytes) } finally { backup.bytes.fill(0) }
        val file = copyAsset("desktop.bundle.csv")
        val encrypted = requireNotNull(cipher().encryptedDocument(file))
        try { encrypted.copyTo(File(output(), "android.srdata"), overwrite = true) } finally { encrypted.delete() }
    }
}
