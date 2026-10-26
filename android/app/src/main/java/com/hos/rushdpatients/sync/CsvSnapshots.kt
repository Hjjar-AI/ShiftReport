package com.hos.rushdpatients.sync

internal data class CsvSnapshot(
    val fileId: String,
    val messageId: Long,
    val date: String?,
    val updatedAt: Long
)

internal fun SyncState.currentCsvSnapshot(): CsvSnapshot? {
    val fileId = csvFileId ?: return null
    val messageId = csvMessageId ?: return null
    return CsvSnapshot(fileId, messageId, csvDate, csvUpdatedAt)
}

internal fun SyncState.previousCsvSnapshot(): CsvSnapshot? {
    val fileId = previousCsvFileId ?: return null
    val messageId = previousCsvMessageId ?: return null
    return CsvSnapshot(fileId, messageId, previousCsvDate, previousCsvUpdatedAt)
}

/**
 * Telegram documents are immutable. The current pointer can advance repeatedly during the
 * publication while retaining the immediately preceding publication for rollback.
 */
internal fun SyncState.withPublishedCsv(uploaded: CsvSnapshot): SyncState = withCsvHistory(
    listOfNotNull(currentCsvSnapshot(), previousCsvSnapshot(), uploaded))

internal fun SyncState.withCsvHistory(snapshots: List<CsvSnapshot>): SyncState {
    val ordered = snapshots.distinctBy { it.messageId }.sortedByDescending { it.messageId }
    val latest = ordered.firstOrNull()
    val previous = ordered.getOrNull(1)
    return copy(
        csvFileId = latest?.fileId,
        csvMessageId = latest?.messageId,
        csvDate = latest?.date,
        csvUpdatedAt = latest?.updatedAt ?: 0L,
        previousCsvFileId = previous?.fileId,
        previousCsvMessageId = previous?.messageId,
        previousCsvDate = previous?.date,
        previousCsvUpdatedAt = previous?.updatedAt ?: 0L
    )
}
