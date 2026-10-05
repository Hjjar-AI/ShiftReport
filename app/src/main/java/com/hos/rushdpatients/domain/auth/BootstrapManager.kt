package com.hos.rushdpatients.domain.auth

import android.content.Context
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.sync.DoctorsRegistryCodec
import com.hos.rushdpatients.sync.SyncState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BootstrapResult {
    data class Success(val doctorCount: Int) : BootstrapResult
    data object NoPin : BootstrapResult
    data object UnrecognizedPin : BootstrapResult
    data class Failed(val message: String) : BootstrapResult
}

@Singleton
class BootstrapManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val json: Json,
    private val doctorRepository: DoctorRepository
) {

    suspend fun bootstrap(): BootstrapResult = withContext(Dispatchers.IO) {
        try {
            val chat = telegram.getChat(topics.chatId)
            val pinned = chat.pinnedMessage ?: return@withContext BootstrapResult.NoPin

            // Case A — pinned document IS the registry (bootstrap.txt)
            pinned.document?.let { doc ->
                val local = downloadToCache(doc.fileId, "bootstrap_registry.txt")
                val text = local.readText(Charsets.UTF_8)
                val decoded = runCatching { DoctorsRegistryCodec.decode(text) }.getOrNull()
                if (decoded != null && decoded.doctors.isNotEmpty()) {
                    doctorRepository.replaceAll(decoded.doctors)
                    return@withContext BootstrapResult.Success(decoded.doctors.size)
                }
            }

            // Case B — pinned message is SyncState JSON
            pinned.text?.let { text ->
                val state = runCatching { json.decodeFromString<SyncState>(text) }.getOrNull()
                if (state?.isRecognized == true) {
                    val fileId = state.doctorsFileId
                        ?: return@withContext BootstrapResult.Failed(
                            "لم يتم نشر سجل الأطباء بعد. اطلب من المدير المزامنة أولاً."
                        )
                    val local = downloadToCache(fileId, "bootstrap_registry.txt")
                    val decoded = DoctorsRegistryCodec.decode(local.readText(Charsets.UTF_8))
                    if (decoded.doctors.isNotEmpty()) {
                        doctorRepository.replaceAll(decoded.doctors)
                        return@withContext BootstrapResult.Success(decoded.doctors.size)
                    }
                }
            }

            BootstrapResult.UnrecognizedPin
        } catch (e: Exception) {
            BootstrapResult.Failed(e.message ?: "فشل الاتصال بتليجرام")
        }
    }

    private suspend fun downloadToCache(fileId: String, name: String): File {
        val remote = telegram.getFile(fileId)
        val path = remote.filePath ?: error("تعذر الحصول على مسار الملف")
        val local = File(context.cacheDir, name)
        telegram.downloadFile(path, local)
        return local
    }
}