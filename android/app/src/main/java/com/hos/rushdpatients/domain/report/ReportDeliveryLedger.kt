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
data class ReportDeliveryRecord(
    val id: String, val shiftId: String, val route: String, val key: String,
    val label: String, val format: String, val actorId: String,
    val at: Long, val status: String = "UNKNOWN", val messageIds: List<Long> = emptyList()
)

@Serializable
private data class DeliveryLog(val records: List<ReportDeliveryRecord> = emptyList(), val repeatPermits: Set<String> = emptySet())

class ReportDeliveryBlockedException(message: String) : IllegalStateException(message)

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
                      label: String, expectedActorId: String?): ReportDeliveryRecord = withContext(Dispatchers.IO) {
        mutex.withLock {
            val actor = activeActor()
            require(actor.id == expectedActorId) { "تغير مستخدم الإرسال؛ أعد المراجعة" }
            val route = digest("$shiftId|$chatId")
            val key = digest("$route|$format|$contentKey")
            val log = load()
            requireAllowed(log, route, key)
            val record = ReportDeliveryRecord(UUID.randomUUID().toString(), shiftId, route, key,
                label, format, actor.id, System.currentTimeMillis())
            save(log.copy(records = log.records + record, repeatPermits = log.repeatPermits - key))
            activeAttempts += record.id
            record
        }
    }

    /** Register and release together even if cancellation arrives during durable registration. */
    suspend fun <T> deliver(shiftId: String, chatId: Long, contentKey: String, format: String,
                           label: String, expectedActorId: String?, block: suspend (ReportDeliveryRecord) -> T): T {
        val record = withContext(NonCancellable) {
            begin(shiftId, chatId, contentKey, format, label, expectedActorId)
        }
        try { return block(record) }
        finally {
            withContext(NonCancellable + Dispatchers.IO) {
                mutex.withLock { activeAttempts -= record.id }
            }
        }
    }

    private fun requireAllowed(log: DeliveryLog, route: String, key: String) {
        if (log.records.any { it.route == route && it.status == "UNKNOWN" }) throw ReportDeliveryBlockedException(
            "يوجد تسليم غير مؤكد لهذه المناوبة والوجهة؛ افتح سجل التسليم وافحص تليجرام ثم احسمه قبل الإعادة")
        if (log.records.any { it.key == key && it.status == "SENT" } && key !in log.repeatPermits) throw ReportDeliveryBlockedException(
            "هذا التقرير سُلّم سابقاً؛ افتح سجل التسليم واسمح صراحةً بنسخة إضافية إن أردتها")
    }

    suspend fun check(shiftId: String, chatId: Long, contentKey: String, format: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            activeActor()
            val route = digest("$shiftId|$chatId")
            requireAllowed(load(), route, digest("$route|$format|$contentKey"))
        }
    }

    suspend fun confirm(id: String, messageIds: List<Long>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(messageIds.isNotEmpty() && messageIds.all { it > 0 }) { "إيصال تليجرام غير صالح" }
            val log = load()
            require(log.records.any { it.id == id && it.status == "UNKNOWN" }) { "تغير سجل التسليم؛ راجعه" }
            save(log.copy(records = log.records.map { if (it.id == id) it.copy(status = "SENT", messageIds = messageIds) else it }))
        }
    }

    suspend fun resolve(id: String, expectedStatus: String, resolution: DeliveryResolution) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val actor = activeActor()
            require(id !in activeAttempts) { "طلب التسليم ما زال قيد التنفيذ؛ انتظر نتيجته قبل الحسم" }
            val log = load()
            val row = log.records.singleOrNull { it.id == id } ?: error("سجل التسليم غير موجود")
            require(row.status == expectedStatus) { "تغيرت حالة العملية؛ أعد فتح سجل التسليم" }
            require(actor.id == row.actorId || actor.isAdmin) { "حسم العملية متاح لمن أرسلها أو لمدير نشط" }
            if (resolution == DeliveryResolution.ALLOW_COPY) {
                require(row.status == "SENT") { "احسم التسليم غير المؤكد أولاً" }
                save(log.copy(repeatPermits = log.repeatPermits + row.key))
            } else {
                require(row.status == "UNKNOWN") { "هذه العملية محسومة بالفعل" }
                val status = if (resolution == DeliveryResolution.RECEIVED) "SENT" else "NOT_SENT"
                save(log.copy(records = log.records.map { if (it.id == id) it.copy(status = status) else it }))
            }
        }
    }
}
