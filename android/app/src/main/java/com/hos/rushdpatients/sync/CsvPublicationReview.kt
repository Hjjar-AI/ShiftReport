package com.hos.rushdpatients.sync

/** Device-memory review token; never persisted or placed in SavedStateHandle. */
class CsvPublicationReview internal constructor(
    internal val shiftId: String,
    internal val csv: String,
    internal val source: SyncState,
    internal val actorId: String,
    internal val telegramId: Long?,
    val text: String
)

internal fun samePublicationSource(a: SyncState?, b: SyncState): Boolean =
    a != null && a.csvFileId == b.csvFileId && a.csvMessageId == b.csvMessageId &&
        a.doctorsFileId == b.doctorsFileId && a.doctorsMessageId == b.doctorsMessageId &&
        a.doctorsData == b.doctorsData
