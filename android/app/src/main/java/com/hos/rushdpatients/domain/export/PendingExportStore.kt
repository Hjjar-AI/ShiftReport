package com.hos.rushdpatients.domain.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.security.crypto.MasterKeys
import com.hos.rushdpatients.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Recoverable private staging. Saved UI state contains only random IDs and record counts. */
@Singleton
class PendingExportStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider
) {
    suspend fun stage(bytes: ByteArray): String = withContext(dispatchers.io) {
        require(bytes.isNotEmpty()) { "محتوى التصدير فارغ" }
        val directory = directory().also { check(it.isDirectory || it.mkdirs()) }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > MAX_AGE_MS }
            ?.forEach { it.delete() }
        val id = UUID.randomUUID().toString()
        val file = file(id)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            check(cipher.iv.size == IV_BYTES) { "تعذر تجهيز تشفير ملف التصدير" }
            file.outputStream().use { output ->
                output.write(cipher.iv)
                output.write(cipher.doFinal(bytes))
                output.flush()
            }
            id
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    suspend fun write(uri: Uri, id: String) = withContext(dispatchers.io) {
        val file = file(id)
        check(file.isFile) { "انتهت جلسة التصدير؛ أعد إنشاء الملف" }
        val encrypted = file.readBytes()
        require(encrypted.size > IV_BYTES + 16) { "ملف التصدير المؤقت تالف؛ أعد التصدير" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, IV_BYTES)))
        val bytes = cipher.doFinal(encrypted.copyOfRange(IV_BYTES, encrypted.size))
        try {
            writeVerified(context, uri, bytes)
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun discard(id: String) = withContext(dispatchers.io + NonCancellable) {
        file(id).delete()
        Unit
    }

    suspend fun discardDestination(uri: Uri) = withContext(dispatchers.io + NonCancellable) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        Unit
    }

    private fun directory() = File(context.noBackupFilesDir, "pending_document_exports")

    private fun file(id: String): File {
        require(UUID.fromString(id).toString() == id) { "جلسة تصدير غير صالحة" }
        return File(directory(), id)
    }

    private fun key(): SecretKey {
        val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as SecretKey
    }

    companion object {
        private const val IV_BYTES = 12
        private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

        /** Caller must use an IO dispatcher; close the output before verifying provider contents. */
        fun writeVerified(context: Context, uri: Uri, bytes: ByteArray) {
            require(bytes.isNotEmpty()) { "محتوى التصدير فارغ" }
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("تعذر فتح ملف التصدير")
            val saved = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("تعذر التحقق من حفظ الملف")
            try {
                check(saved.contentEquals(bytes)) { "لم يُحفظ الملف كاملاً؛ أعد التصدير" }
            } finally {
                saved.fill(0)
            }
        }
    }
}
