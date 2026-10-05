package com.hos.rushdpatients.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
        mimeType = "text/csv"
    )

    private suspend fun saveFile(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri? = withContext(Dispatchers.IO) {

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
                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            // API < 29: save to app-specific external documents dir
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            val target = File(dir, "$subFolder/$displayName")
            target.parentFile?.mkdirs()
            sourceFile.copyTo(target, overwrite = true)
            Uri.fromFile(target)
        }
    }
}