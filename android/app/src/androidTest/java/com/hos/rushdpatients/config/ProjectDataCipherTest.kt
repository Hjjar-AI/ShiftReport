package com.hos.rushdpatients.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

import com.hos.rushdpatients.testutil.IsolatedPreferencesContext
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import org.junit.Before
import org.junit.After
import java.io.File
import android.util.Base64

@RunWith(AndroidJUnit4::class)
class ProjectDataCipherTest {
    private lateinit var context: IsolatedPreferencesContext
    private lateinit var projects: ProjectConfigStore
    private lateinit var cipher: ProjectDataCipher
    private val files = mutableListOf<File>()
    @Before fun setUp() {
        context = IsolatedPreferencesContext()
        projects = ProjectConfigStore(context)
        cipher = ProjectDataCipher(projects, DefaultDispatcherProvider())
    }
    @After fun cleanUp() { files.forEach { it.delete() }; context.clearTestPreferences() }
    private fun enable(key: String = ProjectDataCipher.generateKey()) { projects.saveDraft(ProjectConfig(telegramDataKey = key)) }
    private fun file(bytes: ByteArray): File = File.createTempFile("cipher_test_", ".bin", context.cacheDir)
        .also { it.writeBytes(bytes); files += it }
    @Test fun encryptedTextRoundTripUsesFreshNonces() = runBlocking<Unit> {
        enable()
        val plain = "تقرير تجريبي\nsynthetic report"
        val first = cipher.encodeText(plain)
        val second = cipher.encodeText(plain)
        assertNotEquals(first, second)
        assertTrue(first.startsWith("SRMSG1:"))
        assertEquals(plain, cipher.decodeText(first, requireEncrypted = true))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { cipher.decodeText(plain, true) } }
    }
    @Test fun tamperedPayloadAndWrongKeyFailExplicitly() = runBlocking<Unit> {
        enable()
        val encrypted = cipher.encodeText("synthetic")
        val bytes = Base64.decode(encrypted.removePrefix("SRMSG1:"), Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val tampered = "SRMSG1:" + Base64.encodeToString(bytes, Base64.NO_WRAP)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { cipher.decodeText(tampered) } }
        enable()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { cipher.decodeText(encrypted) } }
    }
    @Test fun encryptedFilesRoundTripButPdfRemainsReadable() = runBlocking<Unit> {
        enable()
        val original = file("synthetic,clinical\ndata".toByteArray())
        val encrypted = requireNotNull(cipher.encryptedDocument(original)).also { files += it }
        assertFalse(original.readBytes().contentEquals(encrypted.readBytes()))
        cipher.decryptDocument(encrypted)
        assertArrayEquals(original.readBytes(), encrypted.readBytes())
        val pdf = file("%PDF-1.4 synthetic".toByteArray())
        assertNull(cipher.encryptedDocument(pdf))
        cipher.decryptDocument(pdf)
        assertTrue(pdf.readText().startsWith("%PDF-"))
    }
    @Test fun unencryptedProjectPassesThroughAndGeneratedKeysAreIndependent() = runBlocking<Unit> {
        assertEquals("plain", cipher.encodeText("plain"))
        assertEquals("plain", cipher.decodeText("plain"))
        assertNull(cipher.decodeText(null))
        val a = ProjectDataCipher.generateKey()
        assertTrue(ProjectDataCipher.validKey(a))
        assertNotEquals(a, ProjectDataCipher.generateKey())
        assertFalse(ProjectDataCipher.validKey("invalid"))
    }
}
