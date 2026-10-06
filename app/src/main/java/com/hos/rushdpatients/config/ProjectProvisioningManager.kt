package com.hos.rushdpatients.config

import android.content.Context
import android.net.Uri
import com.hos.rushdpatients.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom
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
    suspend fun export(uri: Uri, config: ProjectConfig, password: CharArray) =
        withContext(dispatchers.io) {
            require(config.initialized && !config.demoMode) { "لا يوجد مشروع مهيأ للتصدير" }
            require(password.size >= MIN_PASSWORD_LENGTH) { "عبارة المرور يجب ألا تقل عن 10 محارف" }
            val json = JSONObject()
                .put("version", 1)
                .put("hospitalName", config.hospitalName)
                .put("botToken", config.botToken)
                .put("chatId", config.chatId)
                .put("reportsTopicId", config.reportsTopicId)
                .put("announcementsTopicId", config.announcementsTopicId)
                .put("csvTopicId", config.csvTopicId)
                .put("doctorsTopicId", config.doctorsTopicId)
                .toString()
                .toByteArray(Charsets.UTF_8)
            val encrypted = encrypt(json, password)
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(encrypted) }
                ?: error("تعذر إنشاء ملف الانضمام")
        }

    suspend fun importConfig(uri: Uri, password: CharArray): ProjectConfig =
        withContext(dispatchers.io) {
            require(password.size >= MIN_PASSWORD_LENGTH) { "عبارة المرور يجب ألا تقل عن 10 محارف" }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("تعذر قراءة ملف الانضمام")
            val json = runCatching {
                JSONObject(decrypt(bytes, password).toString(Charsets.UTF_8))
            }.getOrElse { throw IllegalArgumentException("عبارة المرور خاطئة أو الملف تالف") }
            require(json.optInt("version") == 1) { "إصدار ملف الانضمام غير مدعوم" }
            ProjectConfig(
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
        const val MIN_PASSWORD_LENGTH = 10
        private val MAGIC = "RUSHDJOIN1".toByteArray(Charsets.US_ASCII)
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
    }
}
