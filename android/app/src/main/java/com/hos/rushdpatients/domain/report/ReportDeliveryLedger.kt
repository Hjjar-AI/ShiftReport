package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.domain.auth.SessionManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class DeliveryStatus { UNKNOWN, SENT, NOT_SENT, UNKNOWN_COPY_ALLOWED }

@Serializable
data class ReportDeliveryRecord(
    val id: String, val shiftId: String, val route: String, val key: String,
    val label: String, val format: String, val actorId: String,
    val at: Long, val status: DeliveryStatus = DeliveryStatus.UNKNOWN, val messageIds: List<Long> = emptyList(),
    val metadata: Map<String, Long> = emptyMap()
)

@Serializable
private data class DeliveryLog(val records: List<ReportDeliveryRecord> = emptyList(), val repeatPermits: Set<String> = emptySet())

open class ReportDeliveryBlockedException(message: String) : IllegalStateException(message)

class ExactReportCopyRequiredException : ReportDeliveryBlockedException(
    "Extra-copy permission applies only to the approved report")

enum class DeliveryResolution { RECEIVED, NOT_RECEIVED, ALLOW_COPY }

/** Local metadata only. Persist UNKNOWN before requests; never infer absence from a timeout. */
@Singleton
class ReportDeliveryLedger @Inject constructor(
    private val settings: SettingsRepository,
    private val sessions: SessionManager,
    private val doctors: DoctorRepository
) {
    private val mutex = Mutex()
    private val activeAttempts = mutableSetOf<String>()
    private val activeCopyOrigins = mutableMapOf<String, Set<String>>()
    private val json = Json { ignoreUnknownKeys = true }
    private val settingKey = "report_delivery_ledger"
    private fun decode(raw: String?): DeliveryLog = raw?.let { json.decodeFromString<DeliveryLog>(it) } ?: DeliveryLog()
    private suspend fun load() = decode(settings.get(settingKey))
    private suspend fun save(log: DeliveryLog) = settings.put(settingKey, json.encodeToString(log))
    fun observe() = settings.observe(settingKey).map { decode(it).records }.flowOn(Dispatchers.IO)
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private suspend fun activeActor() = sessions.current()?.doctorId?.let { doctors.getActiveById(it) }
        ?: error("يجب تسجيل الدخول بطبيب نشط قبل تعديل سجل التسليم")

    private suspend fun begin(shiftId: String, chatId: Long, contentKey: String, format: String,
                      label: String, expectedActorId: String?, metadata: Map<String, Long>, legacyContentKey: String?): ReportDeliveryRecord = withContext(Dispatchers.IO) {
        mutex.withLock {
            val actor = activeActor()
            require(actor.id == expectedActorId) { "تغير مستخدم الإرسال؛ أعد المراجعة" }
            val route = digest("$shiftId|$chatId")
            val key = digest("$route|$format|$contentKey")
            val log = load()
            val keys = setOfNotNull(key, legacyContentKey?.let { digest("$route|$format|$it") })
            requireAllowed(log, route, keys)
            val record = ReportDeliveryRecord(UUID.randomUUID().toString(), shiftId, route, key,
                label, format, actor.id, System.currentTimeMillis(), metadata = metadata)
            val origins = log.records.filter {
                it.route == route && it.status == DeliveryStatus.UNKNOWN_COPY_ALLOWED &&
                    it.key in keys && it.key in log.repeatPermits
            }.mapTo(mutableSetOf()) { it.id }
            // Consume the one-use permit while durably restoring the original uncertainty.
            // Failed/interrupted copies must leave that original outcome actionable.
            save(log.copy(records = log.records.map {
                if (it.id in origins) it.copy(status = DeliveryStatus.UNKNOWN) else it
            } + record, repeatPermits = log.repeatPermits - keys))
            activeAttempts += record.id
            activeCopyOrigins[record.id] = origins
            record
        }
    }

    /** Register and release together even if cancellation arrives during durable registration. */
    suspend fun <T> deliver(shiftId: String, chatId: Long, contentKey: String, format: String,
                           label: String, expectedActorId: String?, metadata: Map<String, Long> = emptyMap(),
                           legacyContentKey: String? = null,
                           block: suspend (ReportDeliveryRecord) -> T): T {
        val record = withContext(NonCancellable) {
            begin(shiftId, chatId, contentKey, format, label, expectedActorId, metadata, legacyContentKey)
        }
        try { return block(record) }
        catch (e: com.hos.rushdpatients.network.telegram.TelegramException) {
            if (e.isDefiniteRejection) withContext(NonCancellable + Dispatchers.IO) {
                mutex.withLock {
                    val log = load()
                    save(log.copy(records = log.records.map {
                        if (it.id == record.id && it.status == DeliveryStatus.UNKNOWN) it.copy(status = DeliveryStatus.NOT_SENT) else it
                    }))
                }
            }
            throw e
        }
        finally {
            withContext(NonCancellable + Dispatchers.IO) {
                mutex.withLock {
                    activeAttempts -= record.id
                    activeCopyOrigins.remove(record.id)
                }
            }
        }
    }

    private fun requireAllowed(log: DeliveryLog, route: String, keys: Set<String>) {
        if (log.records.any { it.route == route && it.status == DeliveryStatus.UNKNOWN }) throw ReportDeliveryBlockedException(
            "يوجد إرسال غير مؤكد لهذه الوجهة؛ افتح سجل التسليم وافحص تليجرام ثم احسمه قبل الإعادة")
        if (log.records.any { it.route == route && it.status == DeliveryStatus.UNKNOWN_COPY_ALLOWED &&
                it.key in log.repeatPermits && it.key !in keys }) throw ExactReportCopyRequiredException()
        if (log.records.any { it.key in keys && it.status == DeliveryStatus.SENT } && keys.none { it in log.repeatPermits }) throw ReportDeliveryBlockedException(
            "هذا المحتوى سُلّم سابقاً؛ افتح سجل التسليم واسمح صراحةً بنسخة إضافية إن أردتها")
    }

    suspend fun check(shiftId: String, chatId: Long, contentKey: String, format: String, legacyContentKey: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            activeActor()
            val route = digest("$shiftId|$chatId")
            requireAllowed(load(), route, setOfNotNull(digest("$route|$format|$contentKey"),
                legacyContentKey?.let { digest("$route|$format|$it") }))
        }
    }

    suspend fun confirm(id: String, messageIds: List<Long>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(messageIds.isNotEmpty() && messageIds.all { it > 0 }) { "إيصال تليجرام غير صالح" }
            val log = load()
            require(log.records.any { it.id == id && it.status == DeliveryStatus.UNKNOWN }) { "تغير سجل التسليم؛ راجعه" }
            val origins = activeCopyOrigins[id].orEmpty()
            save(log.copy(records = log.records.map {
                when {
                    it.id == id -> it.copy(status = DeliveryStatus.SENT, messageIds = messageIds)
                    it.id in origins && it.status == DeliveryStatus.UNKNOWN -> it.copy(status = DeliveryStatus.UNKNOWN_COPY_ALLOWED)
                    else -> it
                }
            }))
        }
    }

    private suspend fun resolvable(log: DeliveryLog, id: String, expectedStatus: DeliveryStatus): ReportDeliveryRecord {
        val actor = activeActor()
        require(id !in activeAttempts && activeCopyOrigins.values.none { id in it }) {
            "طلب التسليم ما زال قيد التنفيذ؛ انتظر نتيجته قبل الحسم"
        }
        val row = log.records.singleOrNull { it.id == id } ?: error("سجل التسليم غير موجود")
        require(row.status == expectedStatus) { "تغيرت حالة العملية؛ أعد فتح سجل التسليم" }
        require(actor.id == row.actorId || actor.isAdmin) { "حسم العملية متاح لمن أرسلها أو لمدير نشط" }
        return row
    }

    suspend fun requireResolvable(id: String, expectedStatus: DeliveryStatus): ReportDeliveryRecord = withContext(Dispatchers.IO) {
        mutex.withLock { resolvable(load(), id, expectedStatus) }
    }

    suspend fun resolve(id: String, expectedStatus: DeliveryStatus, resolution: DeliveryResolution) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val log = load()
            val row = resolvable(log, id, expectedStatus)
            if (resolution == DeliveryResolution.ALLOW_COPY) {
                require(row.status == DeliveryStatus.SENT || row.status == DeliveryStatus.UNKNOWN) { "هذه العملية لا تحتاج إذن نسخة إضافية" }
                // An explicit resolution records uncertainty without claiming receipt or deleting evidence.
                // A failed extra send creates its own UNKNOWN record and blocks again.
                save(log.copy(records = log.records.map {
                    if (it.id == id && it.status == DeliveryStatus.UNKNOWN) it.copy(status = DeliveryStatus.UNKNOWN_COPY_ALLOWED) else it
                }, repeatPermits = log.repeatPermits + row.key))
            } else {
                require(row.status == DeliveryStatus.UNKNOWN || row.status == DeliveryStatus.UNKNOWN_COPY_ALLOWED) {
                    "هذه العملية محسومة بالفعل"
                }
                val status = if (resolution == DeliveryResolution.RECEIVED) DeliveryStatus.SENT else DeliveryStatus.NOT_SENT
                save(log.copy(records = log.records.map { if (it.id == id) it.copy(status = status) else it },
                    repeatPermits = log.repeatPermits - row.key))
            }
        }
    }
}
