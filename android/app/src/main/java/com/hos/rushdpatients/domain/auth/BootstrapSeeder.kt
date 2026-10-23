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

/** Initial project creation only. Login/retry never publishes or pins a registry. */
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
            val config = projectConfigStore.current()
            check(!config.initialized && !config.demoMode) { "إنشاء السجل متاح أثناء إعداد مشروع جديد فقط" }
            require(telegram.getChat(topics.chatId).pinnedMessage == null) {
                "توجد رسالة مثبّتة؛ استخدم الانضمام إلى المشروع ولا تستبدل سجل الفريق"
            }
            val admin = config.initialAdmin
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
            val msg = try {
                file.writeText(text, Charsets.UTF_8)
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

            check(projectConfigStore.current() == config) { "تغير إعداد المشروع؛ أعد مراجعته" }
            require(telegram.getChat(topics.chatId).pinnedMessage == null) {
                "ظهرت رسالة مثبّتة أثناء الإعداد؛ لم يُستبدل سجل الفريق"
            }
            check(telegram.pinMessage(topics.chatId, msg.messageId, disableNotification = true)) {
                "لم يؤكد تليجرام تثبيت سجل الأطباء؛ راجع الرسالة المثبّتة قبل إعادة المحاولة"
            }

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
