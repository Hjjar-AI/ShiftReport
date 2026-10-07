package com.hos.rushdpatients.config

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.hos.rushdpatients.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Password-encrypted project connection file. It never contains patient or user records. */
@Singleton
class ProjectProvisioningManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider
) {
    suspend fun prepareExport(config: ProjectConfig, password: CharArray): ByteArray =
        withContext(dispatchers.io) {
            require(config.initialized && !config.demoMode) { "لا يوجد مشروع مهيأ للتصدير" }
            require(config.telegramDataKey.isEmpty() || ProjectDataCipher.validKey(config.telegramDataKey)) {
                "مفتاح تشفير المشروع غير صالح"
            }
            require(password.size >= MIN_PASSWORD_LENGTH) { "عبارة المرور يجب ألا تقل عن 10 محارف" }
            val json = JSONObject()
                .put("version", 1)
                .put("hospitalName", config.hospitalName)
                .put("botToken", config.botToken)
                .put("encryptTelegram", config.telegramDataKey.isNotEmpty())
                .put("telegramDataKey", config.telegramDataKey)
                .put("chatId", config.chatId)
                .put("reportsTopicId", config.reportsTopicId)
                .put("announcementsTopicId", config.announcementsTopicId)
                .put("csvTopicId", config.csvTopicId)
                .put("doctorsTopicId", config.doctorsTopicId)
                .toString()
                .toByteArray(Charsets.UTF_8)
            try {
                encrypt(json, password)
            } finally {
                json.fill(0)
                password.fill('\u0000')
            }
        }

    /** Only encrypted bytes go to private storage; saved UI state holds the random file ID. */
    suspend fun stageExport(encrypted: ByteArray): String = withContext(dispatchers.io) {
        validateEncrypted(encrypted)
        val directory = pendingDirectory().also { check(it.isDirectory || it.mkdirs()) }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > PENDING_MAX_AGE_MS }
            ?.forEach { it.delete() }
        val id = UUID.randomUUID().toString()
        val file = pendingFile(id)
        try {
            file.outputStream().use { stream ->
                stream.write(encrypted)
                stream.flush()
            }
            check(file.length() == encrypted.size.toLong()) { "تعذر تجهيز ملف الانضمام" }
            id
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    suspend fun writeStagedExport(uri: Uri, id: String) = withContext(dispatchers.io) {
        val file = pendingFile(id)
        check(file.isFile) { "انتهت جلسة التصدير؛ أعد إنشاء ملف الانضمام" }
        val encrypted = file.readBytes()
        try {
            writeExport(uri, encrypted)
        } finally {
            encrypted.fill(0)
        }
    }

    suspend fun discardStagedExport(id: String) = withContext(dispatchers.io) {
        pendingFile(id).delete()
        Unit
    }

    /** Called only for the newly created destination of a failed/cancelled export. */
    suspend fun discardFailedDestination(uri: Uri) = withContext(dispatchers.io) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        Unit
    }

    suspend fun writeExport(uri: Uri, encrypted: ByteArray) =
        withContext(dispatchers.io) {
            validateEncrypted(encrypted)
            // Base64 transports authenticated ciphertext inside a commonly supported file type.
            val document = JSONObject()
                .put("format", ENVELOPE_FORMAT)
                .put("encryptedPayload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .toString(2)
                .toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(document)
                it.flush()
            } ?: error("تعذر إنشاء ملف الانضمام")
            val saved = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("تعذر التحقق من حفظ ملف الانضمام")
            check(saved.contentEquals(document)) { "لم يُحفظ ملف الانضمام كاملاً؛ أعد التصدير" }
        }

    private fun validateEncrypted(encrypted: ByteArray) {
        require(encrypted.size > MAGIC.size + SALT_BYTES + IV_BYTES + 16 &&
            encrypted.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "محتوى ملف الانضمام غير صالح؛ أعد إنشاء الملف"
        }
    }

    private fun pendingDirectory() = File(context.noBackupFilesDir, "pending_join_exports")

    private fun pendingFile(id: String): File {
        require(UUID.fromString(id).toString() == id) { "جلسة تصدير غير صالحة" }
        return File(pendingDirectory(), id)
    }

    private fun encryptedPayload(document: ByteArray): ByteArray {
        // Recognize files by their content, including existing binary join files.
        if (document.size >= MAGIC.size && document.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            return document
        }
        val envelope = JSONObject(document.toString(Charsets.UTF_8))
        require(envelope.getString("format") == ENVELOPE_FORMAT) { "صيغة ملف الانضمام غير مدعومة" }
        return Base64.decode(envelope.getString("encryptedPayload"), Base64.DEFAULT)
            .also(::validateEncrypted)
    }

    suspend fun importConfig(uri: Uri, password: CharArray): ProjectConfig =
        withContext(dispatchers.io) {
            require(password.size >= MIN_PASSWORD_LENGTH) { "عبارة المرور يجب ألا تقل عن 10 محارف" }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("تعذر قراءة ملف الانضمام")
            require(bytes.isNotEmpty()) { "ملف الانضمام فارغ؛ اطلب من المدير تصدير ملف جديد" }
            val json = runCatching {
                val plain = decrypt(encryptedPayload(bytes), password)
                try { JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
            }.getOrElse { throw IllegalArgumentException("عبارة المرور خاطئة أو الملف تالف") }
            require(json.optInt("version") == 1) { "إصدار ملف الانضمام غير مدعوم" }
            val dataKey = json.optString("telegramDataKey", "")
            require(!json.optBoolean("encryptTelegram", false) || ProjectDataCipher.validKey(dataKey)) {
                "ملف الانضمام لا يحمل مفتاح تشفير مشروع صالحاً"
            }
            require(dataKey.isEmpty() || ProjectDataCipher.validKey(dataKey)) { "مفتاح المشروع غير صالح" }
            ProjectConfig(
                telegramDataKey = dataKey,
                hospitalName = json.getString("hospitalName"),
                botToken = json.getString("botToken"),
                chatId = json.getLong("chatId"),
                reportsTopicId = json.optLong("reportsTopicId"),
                announcementsTopicId = json.optLong("announcementsTopicId"),
                csvTopicId = json.optLong("csvTopicId"),
                doctorsTopicId = json.optLong("doctorsTopicId")
            )
        }

    private fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    private fun decrypt(payload: ByteArray, password: CharArray): ByteArray {
        require(payload.size > MAGIC.size + SALT_BYTES + IV_BYTES &&
            payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "صيغة ملف الانضمام غير مدعومة"
        }
        val saltStart = MAGIC.size
        val ivStart = saltStart + SALT_BYTES
        val dataStart = ivStart + IV_BYTES
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, payload.copyOfRange(saltStart, ivStart)),
            GCMParameterSpec(128, payload.copyOfRange(ivStart, dataStart))
        )
        return cipher.doFinal(payload.copyOfRange(dataStart, payload.size))
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES"
            )
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }

    companion object {
        const val EXPORT_MIME_TYPE = "application/json"
        const val EXPORT_EXTENSION = "srjoin.json"
        const val MIN_PASSWORD_LENGTH = 10
        private const val ENVELOPE_FORMAT = "ShiftReportJoin"
        private const val PENDING_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private val MAGIC = "RUSHDJOIN1".toByteArray(Charsets.US_ASCII)
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
    }
}
