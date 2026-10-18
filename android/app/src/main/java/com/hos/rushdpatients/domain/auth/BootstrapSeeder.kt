package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.domain.doctor.DoctorNaming
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.sync.DoctorsRegistryCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hidden recovery tool: upload and pin a minimal doctors registry so the
 * bootstrap flow on the login screen can proceed.
 *
 * Triggered by long-pressing the retry button for 15 seconds on the
 * bootstrap-failed screen. Requires the bot to be a chat admin with
 * "Pin Messages" permission in the main group.
 */
@Singleton
class BootstrapSeeder @Inject constructor(
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val projectConfigStore: ProjectConfigStore
) {

    sealed interface Result {
        data class Success(val messageId: Long, val doctorCount: Int) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun seed(): Result = withContext(Dispatchers.IO) {
        try {
            val admin = projectConfigStore.current().initialAdmin
            val telegramId = admin.telegramId
            require(telegramId > 0L) {
                "معرف تليجرام للمدير الأول غير مضبوط"
            }
            val name = admin.fullName.trim()
            require(name.isNotEmpty()) {
                "اسم المدير الأول فارغ"
            }

            val doctor = Doctor(
                id = DoctorNaming.stableId(name),
                fullName = name,
                firstName = extractFirstName(name),
                lastName = extractLastName(name),
                gender = Gender.fromCode(admin.genderCode),
                clinicalRole = ClinicalRole.fromCode(admin.clinicalRoleCode),
                telegramId = telegramId,
                telegramUsername = null,
                customTitle = null,
                rank = 1,
                isPermanentAdmin = true,
                extraOptions = emptySet(),
                updatedAt = Instant.now(),
                deletedAt = null
            )

            val text = DoctorsRegistryCodec.encode(listOf(doctor))
            val file = File.createTempFile("bootstrap_registry_", ".txt")
            file.writeText(text, Charsets.UTF_8)

            val msg = try {
                telegram.sendDocument(
                    chatId = topics.chatId,
                    file = file,
                    caption = "سجل الأطباء الأولي",
                    parseMode = null,
                    disableNotification = true,
                    messageThreadId = null
                )
            } finally {
                file.delete()
            }

            telegram.pinMessage(topics.chatId, msg.messageId, disableNotification = true)

            Result.Success(msg.messageId, 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e.message ?: "فشل إنشاء سجل الأطباء الأولي")
        }
    }

    private fun extractFirstName(fullName: String): String {
        val cleaned = fullName.removePrefix("د.").trim()
        return cleaned.substringBefore(' ')
    }

    private fun extractLastName(fullName: String): String {
        val cleaned = fullName.removePrefix("د.").trim()
        val idx = cleaned.indexOf(' ')
        return if (idx < 0) "" else cleaned.substring(idx + 1)
    }
}
