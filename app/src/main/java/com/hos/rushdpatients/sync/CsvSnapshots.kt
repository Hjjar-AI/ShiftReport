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
 * 10-hour editing window while the previous recovery version remains fixed.
 */
internal fun SyncState.withPublishedCsv(
    uploaded: CsvSnapshot,
    rotateRecovery: Boolean
): SyncState {
    if (!rotateRecovery && currentCsvSnapshot() != null) {
        return copy(
            csvFileId = uploaded.fileId,
            csvMessageId = uploaded.messageId,
            csvDate = uploaded.date,
            csvUpdatedAt = uploaded.updatedAt
        )
    }
    val snapshots = listOfNotNull(currentCsvSnapshot(), previousCsvSnapshot(), uploaded)
        .distinctBy { it.messageId }
        .sortedByDescending { it.messageId }
    val latest = snapshots.first()
    val previous = snapshots.getOrNull(1)
    return copy(
        csvFileId = latest.fileId,
        csvMessageId = latest.messageId,
        csvDate = latest.date,
        csvUpdatedAt = latest.updatedAt,
        previousCsvFileId = previous?.fileId,
        previousCsvMessageId = previous?.messageId,
        previousCsvDate = previous?.date,
        previousCsvUpdatedAt = previous?.updatedAt ?: 0L
    )
}

