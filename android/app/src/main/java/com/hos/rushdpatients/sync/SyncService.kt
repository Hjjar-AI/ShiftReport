package com.hos.rushdpatients.sync

import android.content.Context
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.report.ReportBuilder
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val syncStateRepository: SyncStateRepository,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val auditRepository: AuditRepository,
    private val dispatchers: DispatcherProvider,
    private val remoteState: RemoteSyncStateStore,
    private val doctorSync: DoctorSyncCoordinator,
    private val reportBuilder: ReportBuilder,
    private val downloadUndo: LocalDownloadUndo,
    private val publicationStatus: PatientPublicationStatus
) {

    private val patientSyncMutex = Mutex()

    private val retrieval = PatientRetrievalCoordinator(
        patientSyncMutex, context, telegram, shiftRepository, patientRepository, doctorRepository, syncStateRepository, settingsRepository, database, auditRepository, dispatchers, remoteState, downloadUndo, doctorSync, publicationStatus)

    private val publication = PatientPublicationCoordinator(
        patientSyncMutex, context, telegram, topics, shiftRepository, patientRepository, doctorRepository, syncStateRepository, settingsRepository, auditRepository, dispatchers, remoteState, doctorSync, reportBuilder, database)

    fun observeDownloadUndo() = downloadUndo.available
    suspend fun undoLastDownload(): Result<String> = patientSyncMutex.withLock {
        try { Result.success(downloadUndo.undo()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun uploadDoctors(registryData: String): Result<Unit> = doctorSync.uploadDoctors(registryData)
    suspend fun uploadCurrentDoctors(): Result<Unit> = doctorSync.uploadCurrentDoctors()
    suspend fun synchronizeDoctors(): Result<Unit> = doctorSync.synchronizeDoctors()
    suspend fun refreshDoctorsForPatientFetch(): Result<Unit> = doctorSync.refreshDoctorsForPatientFetch()
    suspend fun refreshDoctorsFromRemote(): Result<Unit> = doctorSync.refreshDoctorsFromRemote()
    suspend fun fetchLatestDoctors(): Result<String> = doctorSync.fetchLatestDoctors()
    suspend fun fetchAndApplyLatestDoctors(): Result<Int> = doctorSync.fetchAndApplyLatestDoctors()
    suspend fun fetchLatestCsv(replaceLocal: Boolean = false, allowOlder: Boolean = false): Result<CsvFetchResult> =
        retrieval.fetchLatestCsv(replaceLocal, allowOlder)

    suspend fun describeLocalChanges(shiftId: String): HandoverChangeSummary = retrieval.describeLocalChanges(shiftId)

    suspend fun prepareCsvPublication(shiftId: String, reviewed: com.hos.rushdpatients.domain.report.BuiltReport? = null): CsvPublicationReview = publication.prepareCsvPublication(shiftId, reviewed)

    suspend fun uploadCsv(shiftId: String): Result<CsvUploadResult> = publication.uploadCsv(shiftId)

    suspend fun uploadReviewedCsv(review: CsvPublicationReview): Result<CsvUploadResult> = publication.uploadReviewedCsv(review)

    suspend fun prepareRollback(): CsvRollbackReview = publication.prepareRollback()
    suspend fun publishRollback(review: CsvRollbackReview): Result<Unit> = publication.publishRollback(review)

    suspend fun getOrCreateDeviceId(): String = publication.getOrCreateDeviceId()

    suspend fun getPublicationJournal(): List<PublicationJournalEntry> =
        remoteState.readState()?.publicationJournal.orEmpty().sortedByDescending { it.messageId }

}
