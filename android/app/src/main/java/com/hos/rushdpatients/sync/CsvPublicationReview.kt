package com.hos.rushdpatients.sync

/** Device-memory review token; never persisted or placed in SavedStateHandle. */
class CsvPublicationReview internal constructor(
    internal val shiftId: String,
    internal val csv: String,
    internal val source: SyncState,
    internal val actorId: String,
    internal val telegramId: Long?,
    internal val shift: com.hos.rushdpatients.data.model.Shift,
    internal val patients: List<com.hos.rushdpatients.data.model.Patient>,
    internal val doctorsCsv: String?,
    val text: String
)

internal fun sameDoctorPublicationSource(a: SyncState?, b: SyncState): Boolean =
    a != null && a.doctorsFileId == b.doctorsFileId && a.doctorsMessageId == b.doctorsMessageId &&
        a.doctorsUpdatedAt == b.doctorsUpdatedAt && a.doctorsData == b.doctorsData

internal fun samePublicationSource(a: SyncState?, b: SyncState): Boolean =
    sameDoctorPublicationSource(a, b) && a?.csvFileId == b.csvFileId && a?.csvMessageId == b.csvMessageId
