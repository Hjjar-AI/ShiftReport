package com.hos.rushdpatients.config

import android.net.Uri
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A reconnect can replace credentials, never the clinical scope or its data key. */
internal data class ConnectionChange(
    val expected: ProjectConfig,
    val token: String,
    val adminActorId: String? = null
)

@Singleton
class ProjectConnectionManager @Inject constructor(
    private val projects: ProjectConfigStore,
    private val provisioning: ProjectProvisioningManager,
    private val telegram: TelegramClient,
    private val adminAuthorizer: AdminAuthorizer,
    private val dispatchers: DispatcherProvider
) {
    private val mutex = Mutex()

    internal suspend fun prepareFile(uri: Uri, password: CharArray): ConnectionChange {
        val current = requireProject()
        val imported = try {
            provisioning.importConfig(uri, password)
        } finally {
            password.fill('\u0000')
        }
        require(imported.chatId == current.chatId &&
            imported.botToken.substringBefore(':') == current.botToken.substringBefore(':')) {
            "الملف لمشروع آخر. تم منع التبديل لحماية المرضى والمناوبات المحلية؛ لم تتغير الإعدادات."
        }
        require(imported.telegramDataKey == current.telegramDataKey) {
            "مفتاح تشفير الملف مختلف؛ إعادة الاتصال لا تغيّر مفتاح بيانات المشروع"
        }
        require(imported.reportsTopicId == current.reportsTopicId &&
            imported.announcementsTopicId == current.announcementsTopicId &&
            imported.csvTopicId == current.csvTopicId &&
            imported.doctorsTopicId == current.doctorsTopicId) {
            "أقسام الملف مختلفة عن المشروع المحلي؛ تم منع تغيير وجهات البيانات"
        }
        return prepare(current, imported.botToken)
    }

    internal suspend fun prepareToken(token: String): ConnectionChange {
        val actor = adminAuthorizer.requireAdmin()
        return prepare(requireProject(), token.trim(), actor.id)
    }

    internal suspend fun apply(change: ConnectionChange) = mutex.withLock {
        check(projects.current() == change.expected) { "تغير الاتصال؛ أعد التحقق من الإعدادات" }
        change.adminActorId?.let { expectedActor ->
            check(adminAuthorizer.requireAdmin().id == expectedActor) { "تغير المستخدم؛ أعد التحقق" }
        }
        validate(change.expected, change.token)
        withContext(dispatchers.io) {
            // Recheck after network suspension, including live authority for manual replacement.
            change.adminActorId?.let { expectedActor ->
                check(adminAuthorizer.requireAdmin().id == expectedActor) { "تغير المستخدم؛ أعد التحقق" }
            }
            projects.replaceBotToken(change.expected, change.token)
        }
    }

    private suspend fun prepare(
        expected: ProjectConfig, token: String, actorId: String? = null
    ): ConnectionChange {
        validate(expected, token)
        check(projects.current() == expected) { "تغير الاتصال؛ أعد التحقق من الإعدادات" }
        return ConnectionChange(expected, token, actorId)
    }

    private suspend fun validate(expected: ProjectConfig, token: String) {
        require(TOKEN_PATTERN.matches(token)) { "صيغة رمز البوت غير صالحة" }
        val botId = expected.botToken.substringBefore(':').toLongOrNull()
            ?: error("تعذر تحديد بوت المشروع الحالي")
        require(token.substringBefore(':').toLongOrNull() == botId) {
            "الرمز لبوت آخر؛ تم منع تبديل المشروع لحماية البيانات المحلية"
        }
        telegram.validateReplacementToken(token, expected.chatId, botId)
    }

    private fun requireProject(): ProjectConfig = projects.current().also {
        require(it.initialized && !it.demoMode) { "إعادة الاتصال متاحة لمشروع مهيأ فقط" }
    }

    private companion object {
        val TOKEN_PATTERN = Regex("""^\d{5,}:[A-Za-z0-9_-]{20,}$""")
    }
}
