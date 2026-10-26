package com.hos.rushdpatients.sync

import android.content.Context
import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.Topic
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.db.entity.PatientEntity
import com.hos.rushdpatients.data.db.entity.ShiftEntity
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory, single-use confirmation of the records being removed. */
class EmptyStartReview internal constructor(
    internal val source: SyncState,
    internal val actorId: String,
    internal val shifts: List<ShiftEntity>,
    internal val patients: List<PatientEntity>,
    internal val draft: String?,
    internal val shift: Shift,
    val text: String
) {
    internal var consumed = false
}

/** Explicit permanent-superadmin operation; never removes Telegram messages or accounts. */
@Singleton
class EmptyStartCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val shifts: ShiftRepository,
    private val sync: SyncStateRepository,
    private val audit: AuditRepository,
    private val doctors: DoctorSyncCoordinator,
    private val remote: RemoteSyncStateStore,
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val dispatchers: DispatcherProvider
) {
    private val mutex = Mutex()

    suspend fun prepare(): EmptyStartReview = mutex.withLock { withContext(dispatchers.io) {
        val source = requireNotNull(remote.readState()) { "فهرس المشروع غير متاح" }
        val actor = doctors.requirePublishedAdmin(source, permanentOnly = true)
        val shift = Shift(date = ShiftDate.current(), doctorIds = listOf(actor.id), revision = 1,
            snapshotId = UUID.randomUUID().toString(), publishedByDeviceId = "empty-start")
        database.withTransaction {
            val localShifts = database.shiftDao().getRecent(Int.MAX_VALUE).sortedBy { it.id }
            val localPatients = database.patientDao().getAll().sortedBy { it.id }
            EmptyStartReview(source, actor.id, localShifts, localPatients,
                settings.get(AppConstants.SETTING_PATIENT_DRAFT), shift,
                "سيُمسح: جميع المرضى والمناوبات المحلية، بما فيها السلة ومسودة الإدخال. وستُنشر بداية مرضى فارغة للفريق.\n" +
                "محلياً: ${localPatients.size} سجل مريض و${localShifts.size} مناوبة.\n" +
                "يشمل المسح تعديلات المرضى غير المنشورة؛ لا يلزم نشرها أو جلب بيانات المرضى أولاً.\n" +
                "سيبقى: سجل الأطباء والحسابات وإعدادات المشروع والاتصال والتشفير وسجل التسليم. لا تُحذف رسائل أو ملفات تليجرام.\n" +
                "على الأجهزة الأخرى جلب البداية الجديدة قبل العمل؛ نشر نسخة قديمة قد يعيد المرضى. لا يُرسل تقرير.\n" +
                "احفظ نسخة احتياطية أولاً إن أردت الاحتفاظ بالبيانات خارج سجل تليجرام.")
        }
    } }

    private suspend fun requireLocal(review: EmptyStartReview) {
        check(database.shiftDao().getRecent(Int.MAX_VALUE).sortedBy { it.id } == review.shifts &&
            database.patientDao().getAll().sortedBy { it.id } == review.patients &&
            settings.get(AppConstants.SETTING_PATIENT_DRAFT) == review.draft) {
            "تغيرت البيانات المحلية بعد مراجعة البداية الفارغة؛ لم تُمسح تعديلاتك. أعد المراجعة"
        }
    }

    suspend fun confirm(review: EmptyStartReview) = mutex.withLock { withContext(dispatchers.io) {
        check(!review.consumed) { "أعد مراجعة البداية الفارغة قبل محاولة جديدة" }
        val source = requireNotNull(remote.readState()) { "فهرس المشروع غير متاح" }
        check(sameDoctorPublicationSource(source, review.source)) { "تغير سجل الأطباء؛ أعد المراجعة" }
        val actor = doctors.requirePublishedAdmin(source, permanentOnly = true)
        check(actor.id == review.actorId) { "تغير المستخدم؛ أعد المراجعة" }
        database.withTransaction { requireLocal(review) }
        review.consumed = true // A failed/uncertain transport always needs renewed confirmation.
        val file = File.createTempFile("empty_start_", ".csv", context.cacheDir)
        val csv = CsvBundleCodec.encode(listOf(ShiftCsvSnapshot(review.shift, emptyList())))
        val message = try {
            file.writeText(csv, Charsets.UTF_8)
            telegram.sendDocument(chatId = topics.chatId, file = file,
                caption = "بداية فارغة — ${review.shift.date}", parseMode = null,
                messageThreadId = topics.threadId(Topic.CSV))
        } finally { file.delete() }
        val fileId = requireNotNull(message.document?.fileId) { "لم يُرجع تليجرام معرّف الملف" }
        val current = requireNotNull(remote.readState()) { "فهرس المشروع غير متاح" }
        check(sameDoctorPublicationSource(current, review.source)) { "تغير سجل الأطباء؛ لم يُعتمد الملف الفارغ" }
        check(doctors.requirePublishedAdmin(current, permanentOnly = true).id == actor.id) { "تغير المستخدم" }
        database.withTransaction { requireLocal(review) }
        val at = message.date.takeIf { it > 0L }?.times(1000L) ?: System.currentTimeMillis()
        val committed = remote.updateExistingState(replaceCsvHistory = true) { state ->
            check(sameDoctorPublicationSource(state, review.source)) { "تغير سجل الأطباء؛ أعد المراجعة" }
            state.copy(csvFileId = fileId, csvMessageId = message.messageId,
                csvDate = review.shift.date.toString(), csvUpdatedAt = at,
                previousCsvFileId = null, previousCsvMessageId = null, previousCsvDate = null, previousCsvUpdatedAt = 0L,
                publicationJournal = listOf(PublicationJournalEntry(requireNotNull(review.shift.snapshotId),
                    fileId = fileId, messageId = message.messageId, at = at, deviceId = "empty-start", forced = true)))
        }
        check(committed.csvMessageId == message.messageId && sameDoctorPublicationSource(committed, review.source)) {
            "لم تتأكد البداية الفارغة كنسخة حالية؛ لم تُمسح البيانات المحلية. افحص تليجرام ثم أعد المراجعة"
        }
        withContext(NonCancellable) {
            try {
                database.withTransaction {
                    // A racing editor keeps its local work even if Telegram already accepted the reset.
                    requireLocal(review)
                    database.shiftDao().deleteAll() // Patient foreign key cascades include the recycle bin.
                    val clinicalKeys = settings.getAll().keys.filter {
                        it == AppConstants.SETTING_PATIENT_DRAFT || it == "local_patient_download_undo" ||
                            it.startsWith(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX) ||
                            it.startsWith(AppConstants.SETTING_PATIENT_PUBLISHED_AT_PREFIX) ||
                            it.startsWith(AppConstants.SETTING_PATIENT_DOWNLOAD_AT_PREFIX) ||
                            it.startsWith(AppConstants.SETTING_PATIENT_DOWNLOAD_MESSAGE_PREFIX)
                    }
                    clinicalKeys.forEach { settings.delete(it) }
                    shifts.upsert(review.shift)
                    shifts.updatePublicationMetadata(review.shift, message.messageId)
                    settings.put(AppConstants.SETTING_PATIENT_PUBLISHED_AT_PREFIX + review.shift.id, at.toString())

                    settings.put(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + review.shift.id,
                        CsvCodec.encode(review.shift, emptyList()))
                    sync.update(SyncChannel.CSV, at, message.messageId, fileId)
                    audit.record(actor.id, actor.fullName, "empty_start", "patients=${review.patients.size}; shifts=${review.shifts.size}")
                }
            } catch (e: Exception) {
                throw IllegalStateException("تم نشر البداية الفارغة على تليجرام؛ تعذر مسح البيانات المحلية أو تغيرت بعد المراجعة. لم تُستبدل تعديلاتك. افحص النسخة الحالية قبل الإعادة", e)
            }
        }
    } }
}
