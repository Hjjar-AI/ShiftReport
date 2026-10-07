package com.hos.rushdpatients.config

import android.util.Base64
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Project-shared authenticated encryption. Base64 is only the envelope's text representation. */
@Singleton
class ProjectDataCipher @Inject constructor(
    private val projects: ProjectConfigStore,
    private val dispatchers: DispatcherProvider
) {
    val enabled: Boolean get() = projects.current().telegramDataKey.isNotEmpty()

    suspend fun encodeText(text: String): String = withContext(dispatchers.io) {
        if (!enabled) return@withContext text
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            output.toByteArray()
        }
        try {
            val encoded = TEXT_PREFIX + Base64.encodeToString(encrypt(compressed, "text"), Base64.NO_WRAP)
            require(encoded.length <= 4096) { "النص المشفر أطول من حد تليجرام؛ اختصر النص أو أرسل التقرير كملف" }
            encoded
        } finally {
            compressed.fill(0)
        }
    }

    suspend fun decodeText(text: String?, requireEncrypted: Boolean = false): String? = withContext(dispatchers.io) {
        if (text == null) return@withContext null
        if (!text.startsWith(TEXT_PREFIX)) {
            require(!requireEncrypted || !enabled) { "بيانات المشروع غير مشفرة؛ تحقق من ملف الانضمام والمشروع" }
            return@withContext text
        }
        val decoded = decrypt(Base64.decode(text.removePrefix(TEXT_PREFIX), Base64.NO_WRAP), "text")
        try {
            GZIPInputStream(ByteArrayInputStream(decoded)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            decoded.fill(0)
        }
    }

    suspend fun encryptedDocument(file: File): File? = withContext(dispatchers.io) {
        if (!enabled || isPdf(file)) return@withContext null
        val plain = file.readBytes()
        val encrypted = try { encrypt(plain, "file") } finally { plain.fill(0) }
        val output = File.createTempFile("project_data_", ".srdata", file.parentFile)
        try {
            output.writeBytes(encrypted)
            output
        } catch (e: Exception) {
            output.delete()
            throw e
        }
    }

    suspend fun decryptDocument(file: File) = withContext(dispatchers.io) {
        val header = ByteArray(MAGIC.size)
        val count = file.inputStream().use { it.read(header) }
        if (count != MAGIC.size || !header.contentEquals(MAGIC)) {
            require(!enabled || header.startsWithBytes(PDF_MAGIC)) { "الملف غير مشفر؛ تحقق من ملف الانضمام والمشروع" }
            return@withContext
        }
        val plain = decrypt(file.readBytes(), "file")
        try { file.writeBytes(plain) } finally { plain.fill(0) }
    }

    private fun encrypt(plain: ByteArray, purpose: String): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC + purpose.toByteArray(Charsets.US_ASCII))
        return MAGIC + nonce + cipher.doFinal(plain)
    }

    private fun decrypt(payload: ByteArray, purpose: String): ByteArray {
        require(payload.startsWithBytes(MAGIC) && payload.size >= MAGIC.size + NONCE_BYTES + 16) {
            "ملف المشروع المشفر تالف أو غير مدعوم"
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, payload.copyOfRange(MAGIC.size, MAGIC.size + NONCE_BYTES)))
            cipher.updateAAD(MAGIC + purpose.toByteArray(Charsets.US_ASCII))
            return cipher.doFinal(payload.copyOfRange(MAGIC.size + NONCE_BYTES, payload.size))
        } catch (e: Exception) {
            throw IllegalArgumentException("تعذر فك بيانات المشروع؛ ملف الانضمام لا يحمل المفتاح الصحيح أو البيانات تالفة", e)
        }
    }

    private fun key(): SecretKeySpec {
        val encoded = projects.current().telegramDataKey
        require(encoded.isNotBlank()) { "هذا المشروع مشفر؛ استورد ملف الانضمام الذي يتضمن مفتاحه" }
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        try {
            require(bytes.size == KEY_BYTES) { "مفتاح تشفير المشروع غير صالح" }
            return SecretKeySpec(bytes, "AES")
        } finally { bytes.fill(0) }
    }

    private fun isPdf(file: File): Boolean = file.inputStream().use { input ->
        val prefix = ByteArray(PDF_MAGIC.size)
        input.read(prefix) == prefix.size && prefix.contentEquals(PDF_MAGIC)
    }

    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        private val MAGIC = "SRDATA1".toByteArray(Charsets.US_ASCII)
        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
        private const val TEXT_PREFIX = "SRMSG1:"
        private const val NONCE_BYTES = 12
        private const val KEY_BYTES = 32

        /** Call off the main thread. Every new encrypted project gets an independent random key. */
        fun generateKey(): String {
            val bytes = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
            return try { Base64.encodeToString(bytes, Base64.NO_WRAP) } finally { bytes.fill(0) }
        }

        fun validKey(encoded: String): Boolean = runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            try { bytes.size == KEY_BYTES } finally { bytes.fill(0) }
        }.getOrDefault(false)
    }
}
