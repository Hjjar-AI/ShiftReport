package com.hos.rushdpatients.pdf

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Private temporary files only; explicit user exports are never removed here. */
internal object ClinicalPdfCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun clear(context: Context) = scope.launch {
        listOf("pdf_previews", "shared_reports").forEach { name ->
            runCatching {
                File(context.cacheDir, name).listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            }
        }
    }

    fun delete(files: List<File>) {
        if (files.isNotEmpty()) scope.launch { files.forEach { file -> runCatching { file.delete() } } }
    }
}
