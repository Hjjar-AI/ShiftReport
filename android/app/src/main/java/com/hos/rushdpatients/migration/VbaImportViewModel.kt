package com.hos.rushdpatients.migration

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.Logging
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VbaImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: VbaCsvImporter,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val _state = MutableStateFlow(VbaImportUiState())
    val state: StateFlow<VbaImportUiState> = _state.asStateFlow()

    fun loadUri(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = true, error = null, preview = null, imported = null)
            }
            try {
                val file = copyToCache(uri)
                Logging.d("VBA import: cached ${file.absolutePath} (${file.length()} bytes)")
                val preview = importer.preview(file)
                Logging.d(
                    "VBA import: parsed ${preview.totalPatients} patients " +
                            "from ${preview.fileName}"
                )
                _state.update { it.copy(loading = false, preview = preview) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Catches Error subclasses too (OutOfMemoryError, VerifyError, …)
                // so a bad file never takes the whole process down.
                Logging.e("VBA import: loadUri failed", t)
                _state.update {
                    it.copy(
                        loading = false,
                        error = t.message ?: "فشل قراءة الملف"
                    )
                }
            }
        }
    }

    fun apply() {
        val preview = _state.value.preview ?: return
        val mode = _state.value.importMode
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val result = importer.apply(preview, mode)
                Logging.d(
                    "VBA import: applied. inserted=${result.inserted} " +
                            "updated=${result.updated} skipped=${result.skipped}"
                )
                _state.update { it.copy(loading = false, preview = null, imported = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logging.e("VBA import: apply failed", t)
                _state.update {
                    it.copy(loading = false, error = t.message ?: "فشل الاستيراد")
                }
            }
        }
    }

    fun setImportMode(mode: ImportMode) {
        _state.update { it.copy(importMode = mode, imported = null, error = null) }
    }

    fun reset() {
        _state.value = VbaImportUiState()
    }

    private suspend fun copyToCache(uri: Uri): File = withContext(dispatchers.io) {
        // Best-effort: some OEM file providers require the persistable grant
        // even for an immediate read. If it fails, we still try to open.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onFailure {
            Logging.w("VBA import: takePersistableUriPermission failed for $uri", it)
        }

        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Logging.e("VBA import: openInputStream threw for $uri", e)
            null
        } ?: throw IllegalStateException("لا يمكن فتح الملف المحدد")

        val name = "vba_import_${System.currentTimeMillis()}.csv"
        val target = File(context.cacheDir, name)
        try {
            input.use { stream ->
                target.outputStream().use { output -> stream.copyTo(output) }
            }
        } catch (e: Exception) {
            target.delete()
            throw IllegalStateException("فشل نسخ الملف: ${e.message}", e)
        }

        if (!target.exists() || target.length() == 0L) {
            target.delete()
            throw IllegalStateException("الملف المحدد فارغ أو تعذرت قراءته")
        }
        if (target.length() > MAX_IMPORT_BYTES) {
            target.delete()
            throw IllegalStateException(
                "حجم الملف يتجاوز الحد الأقصى (${MAX_IMPORT_BYTES / (1024 * 1024)} ميغابايت)"
            )
        }
        target
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 8L * 1024L * 1024L // 8 MB
    }
}
