package com.hos.rushdpatients.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreSaver @Inject constructor() {

    private val subFolder = "RushdPatients"

    suspend fun savePdf(
        context: Context,
        sourceFile: File,
        displayName: String
    ): Uri? = saveFile(
        context = context,
        sourceFile = sourceFile,
        displayName = displayName,
        mimeType = "application/pdf"
    )

    suspend fun saveCsv(
        context: Context,
        sourceFile: File,
        displayName: String
    ): Uri? = saveFile(
        context = context,
        sourceFile = sourceFile,
        displayName = displayName,
        mimeType = "text/csv",
        verifyContents = true
    )

    private suspend fun saveFile(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        verifyContents: Boolean = false
    ): Uri? = withContext(Dispatchers.IO) {

        if (verifyContents && (!sourceFile.isFile || sourceFile.length() == 0L)) {
            return@withContext null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return@withContext null
            try {
                val stream = resolver.openOutputStream(uri)
                if (verifyContents && stream == null) error("تعذر فتح ملف CSV")
                stream?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                    if (verifyContents) output.flush()
                }
                if (verifyContents) {
                    verifyCopy(sourceFile, resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("تعذر التحقق من حفظ CSV"))
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                val published = resolver.update(uri, values, null, null)
                if (verifyContents) check(published > 0) { "تعذر إكمال حفظ CSV" }
                uri
            } catch (e: Exception) {
                if (verifyContents) {
                    runCatching { resolver.delete(uri, null, null) }
                    if (e is CancellationException) throw e
                } else {
                    resolver.delete(uri, null, null)
                }
                null
            }
        } else {
            // API < 29: save to app-specific external documents dir
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            val target = File(dir, "$subFolder/$displayName")
            target.parentFile?.mkdirs()
            if (verifyContents) {
                try {
                    sourceFile.copyTo(target, overwrite = true)
                    verifyCopy(sourceFile, target.readBytes())
                    Uri.fromFile(target)
                } catch (e: Exception) {
                    target.delete()
                    if (e is CancellationException) throw e
                    null
                }
            } else {
                sourceFile.copyTo(target, overwrite = true)
                Uri.fromFile(target)
            }
        }
    }

    private fun verifyCopy(source: File, saved: ByteArray) {
        var expected: ByteArray? = null
        try {
            expected = source.readBytes()
            check(expected.isNotEmpty() && saved.contentEquals(expected)) {
                "لم يُحفظ ملف CSV كاملاً"
            }
        } finally {
            expected?.fill(0)
            saved.fill(0)
        }
    }

}
